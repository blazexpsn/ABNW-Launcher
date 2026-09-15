package org.teamzetaverse.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Json;

public final class MicrosoftAuth {
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final String clientId;

    public MicrosoftAuth(final String clientId) {
        this.clientId = clientId;
    }

    public static final class AuthException extends IOException {
        public AuthException(final String message) {
            super(message);
        }
    }

    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int intervalSeconds, long expiresAtMillis) {
    }

    public DeviceCode requestDeviceCode() throws IOException {
        this.requireClientId();
        JsonObject response = Http.postForm(DEVICE_CODE_URL, Map.of("client_id", this.clientId, "scope", SCOPE));
        return new DeviceCode(
            Json.string(response, "device_code"),
            Json.string(response, "user_code"),
            Json.string(response, "verification_uri"),
            (int)Json.number(response, "interval", 5),
            System.currentTimeMillis() + Json.number(response, "expires_in", 900) * 1000L);
    }

    public Account completeDeviceCode(final DeviceCode code, final Progress progress) throws IOException, Progress.CancelledException {
        int interval = Math.max(1, code.intervalSeconds());
        while (true) {
            progress.checkCancelled();
            if (System.currentTimeMillis() > code.expiresAtMillis()) {
                throw new AuthException("The sign-in code expired. Start signing in again.");
            }
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Progress.CancelledException();
            }
            try {
                JsonObject token = Http.postForm(TOKEN_URL, Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", this.clientId,
                    "device_code", code.deviceCode()));
                Account account = new Account();
                account.msaRefreshToken = Json.string(token, "refresh_token");
                this.signInToMinecraft(account, Json.string(token, "access_token"), progress);
                return account;
            } catch (Http.StatusException e) {
                String error = oauthError(e);
                switch (error) {
                    case "authorization_pending" -> {
                    }
                    case "slow_down" -> interval += 5;
                    case "authorization_declined" -> throw new AuthException("Sign-in was declined.");
                    case "expired_token" -> throw new AuthException("The sign-in code expired. Start signing in again.");
                    default -> throw new AuthException("Microsoft sign-in failed: " + (error.isEmpty() ? e.getMessage() : error));
                }
            }
        }
    }

    public void refresh(final Account account, final Progress progress) throws IOException, Progress.CancelledException {
        if (account.hasValidMinecraftToken()) {
            return;
        }
        this.requireClientId();
        progress.status("Signing in as " + account.name + "...");
        JsonObject token;
        try {
            token = Http.postForm(TOKEN_URL, Map.of(
                "grant_type", "refresh_token",
                "client_id", this.clientId,
                "refresh_token", account.msaRefreshToken,
                "scope", SCOPE));
        } catch (Http.StatusException e) {
            throw new AuthException("Your Microsoft sign-in for " + account.name + " has expired. Remove the account and sign in again.");
        }
        String refreshToken = Json.string(token, "refresh_token");
        if (refreshToken != null) {
            account.msaRefreshToken = refreshToken;
        }
        this.signInToMinecraft(account, Json.string(token, "access_token"), progress);
    }

    private void signInToMinecraft(final Account account, final String msaAccessToken, final Progress progress) throws IOException, Progress.CancelledException {
        progress.checkCancelled();
        progress.status("Signing in to Xbox Live...");
        JsonObject xblRequest = new JsonObject();
        JsonObject xblProperties = new JsonObject();
        xblProperties.addProperty("AuthMethod", "RPS");
        xblProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProperties.addProperty("RpsTicket", "d=" + msaAccessToken);
        xblRequest.add("Properties", xblProperties);
        xblRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblRequest.addProperty("TokenType", "JWT");
        JsonObject xbl = Http.postJson(XBL_URL, xblRequest);
        String xblToken = Json.string(xbl, "Token");
        String userHash = firstClaim(xbl, "uhs");

        progress.checkCancelled();
        JsonObject xstsRequest = new JsonObject();
        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProperties.add("UserTokens", userTokens);
        xstsRequest.add("Properties", xstsProperties);
        xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.addProperty("TokenType", "JWT");
        JsonObject xsts;
        try {
            xsts = Http.postJson(XSTS_URL, xstsRequest);
        } catch (Http.StatusException e) {
            throw new AuthException(xstsError(e));
        }
        String xstsToken = Json.string(xsts, "Token");
        account.xuid = firstClaim(xsts, "xid");

        progress.checkCancelled();
        progress.status("Signing in to Minecraft...");
        JsonObject loginRequest = new JsonObject();
        loginRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject login;
        try {
            login = Http.postJson(MINECRAFT_LOGIN_URL, loginRequest);
        } catch (Http.StatusException e) {
            if (e.status == 403) {
                throw new AuthException("Minecraft refused this launcher's sign-in. Its Microsoft client ID has not been approved by Mojang.");
            }
            throw e;
        }
        account.minecraftToken = Json.string(login, "access_token");
        account.minecraftTokenExpiry = System.currentTimeMillis() + Json.number(login, "expires_in", 86_400) * 1000L;

        progress.checkCancelled();
        boolean entitled = hasJavaEntitlement(Http.getJson(ENTITLEMENTS_URL, account.minecraftToken));
        if (!entitled) {
            throw new AuthException("This Microsoft account does not own Minecraft: Java Edition and has no PC Game Pass.");
        }

        JsonObject profile;
        try {
            profile = Http.getJson(PROFILE_URL, account.minecraftToken);
        } catch (Http.StatusException e) {
            if (e.status != 404) {
                throw e;
            }
            throw new AuthException("This account can play Java Edition but has no Minecraft profile yet. "
                + "Open the official Minecraft Launcher once to create one, then sign in again.");
        }
        account.uuid = Json.string(profile, "id");
        account.name = Json.string(profile, "name");
    }

    private static boolean hasJavaEntitlement(final JsonObject entitlements) {
        JsonArray items = entitlements.getAsJsonArray("items");
        if (items == null) {
            return false;
        }
        for (var item : items) {
            String name = Json.string(item.getAsJsonObject(), "name");
            if (name == null) {
                continue;
            }
            switch (name) {
                case "game_minecraft", "product_minecraft", "product_game_pass_pc", "product_game_pass_ultimate" -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private void requireClientId() throws AuthException {
        if (this.clientId == null || this.clientId.isBlank()) {
            throw new AuthException("This launcher has no Microsoft client ID. Set one in Settings (or abnw_msa_client_id when building).");
        }
    }

    private static String firstClaim(final JsonObject token, final String claim) throws AuthException {
        JsonObject claims = Json.object(token, "DisplayClaims");
        JsonArray xui = claims == null ? null : claims.getAsJsonArray("xui");
        if (xui == null || xui.isEmpty()) {
            throw new AuthException("Xbox Live returned no user claims.");
        }
        String value = Json.string(xui.get(0).getAsJsonObject(), claim);
        return value == null ? "" : value;
    }

    private static String oauthError(final Http.StatusException e) {
        try {
            String error = Json.string(Json.parseObject(e.body), "error");
            return error == null ? "" : error;
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String xstsError(final Http.StatusException e) {
        long code = 0;
        try {
            code = Json.number(Json.parseObject(e.body), "XErr", 0);
        } catch (IOException ignored) {
        }
        return switch ((int)(code - 2148916000L)) {
            case 233 -> "This Microsoft account has no Xbox profile. Sign in once at minecraft.net or xbox.com to create one.";
            case 235 -> "Xbox Live is not available in this account's country.";
            case 236, 237 -> "This account needs adult verification on xbox.com before it can play.";
            case 238 -> "This is a child account. It must be added to a Microsoft family before it can play.";
            default -> "Xbox Live sign-in failed (" + (code == 0 ? "HTTP " + e.status : "XErr " + code) + ").";
        };
    }
}
