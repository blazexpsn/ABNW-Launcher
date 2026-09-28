package org.teamzetaverse.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Json;

public final class MicrosoftAuth {
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";
    // Microsoft account (login.live.com) client IDs are 16 hex digits and sign in through a browser redirect instead of a device code.
    private static final String LIVE_AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf";
    private static final String LIVE_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String LIVE_REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final String clientId;

    public MicrosoftAuth(final String clientId) {
        this.clientId = clientId;
    }

    public String clientId() {
        return this.clientId;
    }

    public boolean usesBrowserSignIn() {
        return isLiveClientId(this.clientId);
    }

    static boolean isLiveClientId(final String clientId) {
        return clientId != null && clientId.trim().matches("[0-9A-Fa-f]{16}");
    }

    public static class AuthException extends IOException {
        public AuthException(final String message) {
            super(message);
        }
    }

    /** Microsoft, Xbox Live or Minecraft refused this client ID itself, so signing in with another client ID may still work. */
    public static final class ClientRejectedException extends AuthException {
        public ClientRejectedException(final String message) {
            super(message);
        }
    }

    public String browserSignInUrl() throws AuthException {
        this.requireClientId();
        return LIVE_AUTHORIZE_URL
            + "?client_id=" + encode(this.clientId)
            + "&response_type=code"
            + "&redirect_uri=" + encode(LIVE_REDIRECT_URI)
            + "&scope=" + encode(LIVE_SCOPE)
            + "&prompt=select_account";
    }

    public Account completeBrowserSignIn(final String pasted, final Progress progress) throws IOException, Progress.CancelledException {
        this.requireClientId();
        String code = extractCode(pasted);
        progress.status("Signing in to Microsoft...");
        JsonObject token;
        try {
            token = Http.postForm(LIVE_TOKEN_URL, Map.of(
                "client_id", this.clientId,
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", LIVE_REDIRECT_URI));
        } catch (Http.StatusException e) {
            String error = oauthError(e);
            if (isClientError(error)) {
                throw new ClientRejectedException("Microsoft refused this launcher's sign-in (" + error + ").");
            }
            throw new AuthException("Microsoft did not accept that sign-in" + (error.isEmpty() ? "" : " (" + error + ")")
                + ". Each sign-in link only works once and expires after a few minutes, so sign in again.");
        }
        Account account = new Account();
        account.msaClientId = this.clientId;
        account.msaRefreshToken = Json.string(token, "refresh_token");
        this.signInToMinecraft(account, Json.string(token, "access_token"), progress);
        return account;
    }

    static String extractCode(final String pasted) throws AuthException {
        String text = pasted == null ? "" : pasted.trim();
        int start = text.indexOf('?');
        String query = start < 0 ? null : text.substring(start + 1);
        if (query == null && text.contains("#")) {
            query = text.substring(text.indexOf('#') + 1);
        }
        if (query == null) {
            if (text.matches("[A-Za-z0-9._!*$-]{10,}")) {
                return text;
            }
            throw new AuthException("Paste the full address of the blank page Microsoft sent you to. It contains \"code=\".");
        }
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash) + "&" + query.substring(hash + 1);
        }
        String code = null;
        String error = null;
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = pair.substring(0, equals);
            String value = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            if (key.equals("code")) {
                code = value;
            } else if (key.equals("error")) {
                error = value;
            }
        }
        if (error != null) {
            throw new AuthException(error.equals("access_denied") ? "Sign-in was cancelled or declined." : "Microsoft sign-in failed: " + error);
        }
        if (code == null || code.isBlank()) {
            throw new AuthException("That address has no sign-in code in it. Copy the address of the blank page after you finish signing in.");
        }
        return code;
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
                account.msaClientId = this.clientId;
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
                    case "invalid_client", "unauthorized_client" -> throw new ClientRejectedException("Microsoft refused this launcher's sign-in (" + error + ").");
                    default -> throw new AuthException("Microsoft sign-in failed: " + (error.isEmpty() ? e.getMessage() : error));
                }
            }
        }
    }

    public void refresh(final Account account, final Progress progress) throws IOException, Progress.CancelledException {
        if (account.devOffline || account.hasValidMinecraftToken()) {
            return;
        }
        this.requireClientId();
        progress.status("Signing in as " + account.name + "...");
        boolean live = this.usesBrowserSignIn();
        JsonObject token;
        try {
            token = Http.postForm(live ? LIVE_TOKEN_URL : TOKEN_URL, live
                ? Map.of(
                    "grant_type", "refresh_token",
                    "client_id", this.clientId,
                    "refresh_token", account.msaRefreshToken,
                    "redirect_uri", LIVE_REDIRECT_URI,
                    "scope", LIVE_SCOPE)
                : Map.of(
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
        // login.live.com tokens are passed as "t=", Azure (login.microsoftonline.com) tokens as "d=".
        xblProperties.addProperty("RpsTicket", (this.usesBrowserSignIn() ? "t=" : "d=") + msaAccessToken);
        xblRequest.add("Properties", xblProperties);
        xblRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblRequest.addProperty("TokenType", "JWT");
        JsonObject xbl;
        try {
            xbl = Http.postJson(XBL_URL, xblRequest);
        } catch (Http.StatusException e) {
            if (e.status == 400 || e.status == 401 || e.status == 403) {
                throw new ClientRejectedException("Xbox Live refused this launcher's sign-in (HTTP " + e.status + ").");
            }
            throw e;
        }
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
                throw new ClientRejectedException("Minecraft refused this launcher's sign-in. Its Microsoft client ID has not been approved by Mojang.");
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

    private static boolean isClientError(final String error) {
        return error.equals("invalid_client") || error.equals("unauthorized_client") || error.equals("unsupported_grant_type");
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
