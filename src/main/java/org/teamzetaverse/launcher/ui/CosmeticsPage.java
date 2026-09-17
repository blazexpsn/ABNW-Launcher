package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.teamzetaverse.cosmetics.api.Cosmetic;
import org.teamzetaverse.cosmetics.api.CosmeticRegistry;
import org.teamzetaverse.cosmetics.api.InvalidRegistryException;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.auth.Account;
import org.teamzetaverse.launcher.auth.MicrosoftAuth;
import org.teamzetaverse.launcher.cosmetics.CosmeticsClient;
import org.teamzetaverse.launcher.task.Progress;

final class CosmeticsPage {
    private static final long POLL_MILLIS = 3000L;

    private interface Call<T> {
        T run(String token) throws IOException;
    }

    private final LauncherUi ui;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ABNW cosmetics");
        thread.setDaemon(true);
        return thread;
    });
    private String loadedFor;
    private CosmeticsClient.Status status;
    private String error;
    private boolean loading;
    private Progress linking;
    private String linkUrl;
    private Progress unlinking;

    CosmeticsPage(final LauncherUi ui) {
        this.ui = ui;
    }

    void draw() {
        float full = ImGui.getContentRegionAvailX();
        float width = Math.min(full, px(880));
        ImGui.setCursorPosX(ImGui.getCursorPosX() + (full - width) * 0.5f);
        ImGui.beginGroup();
        Widgets.pageHeader("Cosmetics", "Look the part. Cosmetics are purely visual and never change gameplay.");
        this.forgetFinishedTasks();

        Optional<Account> account = this.ui.currentAccount();
        if (!BuildInfo.cosmeticsConfigured()) {
            this.hero("cos-soon", width, "COSMETICS", "Cosmetics coming soon",
                "Supporter cosmetics and the cosmetics store aren't open yet. Keep an eye on the News page.", Icons.Icon.SPARK, null);
        } else if (account.isEmpty()) {
            this.hero("cos-signin", width, "ABNW SUPPORTERS", "Sign in to link Patreon",
                "Sign in with your Microsoft account first, then link your Patreon to unlock supporter cosmetics.", Icons.Icon.CROWN, () -> {
                    if (Widgets.primary("cos-signin-button", "Sign in with Microsoft", Icons.Icon.USER, 0, px(48), true)) {
                        this.ui.startSignIn();
                    }
                });
        } else {
            this.ensureLoaded(account.get());
            if (this.status == null) {
                this.drawLoadingOrError(width, account.get());
            } else if (!this.status.patreon().linked()) {
                this.drawLink(width, account.get());
            } else {
                this.drawLinked(width, account.get());
            }
        }
        ImGui.endGroup();
    }

    private void drawLoadingOrError(final float width, final Account account) {
        if (this.error != null && !this.loading) {
            this.hero("cos-error", width, "COSMETICS", "Couldn't reach cosmetics", this.error, Icons.Icon.ALERT, () -> {
                if (Widgets.secondary("cos-retry", "Try again", Icons.Icon.REFRESH)) {
                    this.reload(account);
                }
            });
            return;
        }
        this.hero("cos-loading", width, "COSMETICS", "Checking your cosmetics…", "Confirming " + account.name + " with Minecraft. This only takes a moment.",
            Icons.Icon.CROWN, null);
        Motion.keepAlive();
    }

    private void drawLink(final float width, final Account account) {
        String body = "Members of the official ABNW Patreon unlock supporter cosmetics. They're purely visual, never affect gameplay, "
            + "and stay unlocked for as long as you support.";
        this.hero("cos-link", width, "ABNW SUPPORTERS", "Link your Patreon", body, Icons.Icon.CROWN, () -> {
            if (this.linking != null) {
                Widgets.primary("cos-waiting", "Waiting for Patreon…", Icons.Icon.CLOCK, 0, px(48), false);
                ImGui.sameLine(0, px(10));
                if (this.linkUrl != null && Widgets.button("cos-reopen", "Open Patreon again", Icons.Icon.EXTERNAL, Widgets.Variant.SECONDARY, 0, px(48), true)) {
                    Desktop.browse(this.linkUrl);
                }
                ImGui.sameLine(0, px(10));
                if (Widgets.button("cos-cancel", "Cancel", Icons.Icon.CLOSE, Widgets.Variant.GHOST, 0, px(48), true)) {
                    this.linking.cancel();
                }
                Motion.keepAlive();
            } else {
                if (Widgets.primary("cos-link-button", "Link Patreon", Icons.Icon.HEART, 0, px(48), true)) {
                    this.startLink(account);
                }
                if (!BuildInfo.PATREON_PAGE_URL.isEmpty()) {
                    ImGui.sameLine(0, px(10));
                    if (Widgets.button("cos-visit", "Visit ABNW's Patreon", Icons.Icon.EXTERNAL, Widgets.Variant.SECONDARY, 0, px(48), true)) {
                        Desktop.browse(BuildInfo.PATREON_PAGE_URL);
                    }
                }
            }
        });
        this.drawError();
    }

    private void drawLinked(final float width, final Account account) {
        CosmeticsClient.Patreon patreon = this.status.patreon();
        String body = patreon.active()
            ? "Thank you for supporting ABNW! Supporter cosmetics are being made right now and will unlock for you automatically. "
                + "Cosmetics you buy in game will be yours to keep forever."
            : "Supporter cosmetics are being made right now. Your Patreon is linked, but it isn't an active membership of the official "
                + "ABNW Patreon, so they'll unlock as soon as it is.";
        this.hero("cos-coming", width, "COSMETICS", "Cosmetics coming soon", body, Icons.Icon.SPARK, null);
        ImGui.dummy(0, px(8));

        if (Widgets.beginCard("cos-patreon", width, 0, Theme.SURFACE, px(26), px(22))) {
            Widgets.cardTitle(Icons.Icon.HEART, "Patreon");
            Widgets.pill("Linked", Theme.OK, Theme.OK, 0.13f, Icons.Icon.CHECK);
            ImGui.sameLine(0, px(8));
            if (patreon.active()) {
                Widgets.pill("Active patron", Theme.SUN, Theme.SUN, 0.13f, Icons.Icon.CROWN);
            } else if ("token_revoked".equals(patreon.status())) {
                Widgets.pill("Access removed on Patreon", Theme.WARN, Theme.WARN, 0.13f, Icons.Icon.ALERT);
            } else {
                Widgets.pill("Not an active patron", Theme.MUTED, 0xFFFFFF, 0.06f, null);
            }
            ImGui.dummy(0, px(4));
            String who = patreon.fullName().isEmpty() ? "" : "Patreon account " + patreon.fullName() + " is linked to " + account.name + ".";
            if ("token_revoked".equals(patreon.status())) {
                who = "ABNW can no longer see this Patreon account. Unlink it and link it again.";
            }
            if (!who.isEmpty()) {
                Widgets.textWrapped(Fonts.body, Theme.MUTED, who);
            }
            String unlocked = unlockedNames(this.status);
            if (!unlocked.isEmpty()) {
                Widgets.textWrapped(Fonts.body, Theme.TEXT, "Unlocked: " + unlocked);
            }
            ImGui.dummy(0, px(8));
            boolean busy = this.loading || this.unlinking != null;
            if (Widgets.secondary("cos-refresh", this.loading ? "Checking…" : "Check again", Icons.Icon.REFRESH, !busy)) {
                this.reload(account);
            }
            ImGui.sameLine(0, px(10));
            if (Widgets.button("cos-unlink", "Unlink Patreon", Icons.Icon.LOGOUT, Widgets.Variant.DANGER, 0, 0, !busy)) {
                this.startUnlink(account);
            }
        }
        Widgets.endCard();
        this.drawError();
    }

    private void drawError() {
        if (this.error != null && this.status != null) {
            ImGui.dummy(0, px(6));
            Widgets.textWrapped(Fonts.body, Theme.ERROR, this.error);
        }
    }

    private void hero(final String id, final float width, final String overline, final String title, final String body, final Icons.Icon icon,
                      final Runnable buttons) {
        float pad = px(32);
        boolean showIcon = width >= px(600);
        float art = showIcon ? px(170) : 0;
        float textWidth = width - pad * 2 - art;
        float titleHeight = Widgets.textHeight(Fonts.title, title, textWidth);
        float bodyHeight = Widgets.textHeight(Fonts.body, body, textWidth);
        float buttonsHeight = buttons != null ? px(22) + px(48) : 0;
        float h = Math.max(px(200), pad + Fonts.overline.size() + px(10) + titleHeight + px(8) + bodyHeight + buttonsHeight + pad);

        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + width, y + h, u32(Theme.SURFACE), px(20));
        if (showIcon) {
            float gx = x + width - pad - art * 0.5f;
            float gy = y + h * 0.5f;
            float radius = Math.min(h * 0.42f, art * 0.5f);
            for (int i = 4; i >= 1; i--) {
                dl.addCircleFilled(gx, gy, radius * (0.45f + 0.15f * i), u32(Theme.EMBER, 0.035f));
            }
            dl.addCircle(gx, gy, radius * 0.62f, u32(Theme.EMBER, 0.35f), 0, px(1.5f));
            float size = radius * 0.72f;
            Icons.draw(dl, icon, gx - size * 0.5f, gy - size * 0.5f, size, u32(Theme.EMBER));
        }
        dl.addRect(x, y, x + width, y + h, u32(Theme.EMBER_LO, 0.45f), px(20), 0, px(1));

        float cx = x + pad;
        float cy = y + pad;
        Widgets.drawOverline(dl, cx, cy, u32(Theme.SUN), overline);
        cy += Fonts.overline.size() + px(10);
        Widgets.drawTextWrapped(dl, Fonts.title, cx, cy, u32(Theme.TEXT), title, textWidth);
        cy += titleHeight + px(8);
        Widgets.drawTextWrapped(dl, Fonts.body, cx, cy, u32(Theme.MUTED), body, textWidth);
        cy += bodyHeight;
        if (buttons != null) {
            ImGui.setCursorScreenPos(cx, cy + px(22));
            buttons.run();
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.dummy(width, h);
    }

    private void forgetFinishedTasks() {
        if (this.linking != null && !this.ui.tasks.running().contains(this.linking)) {
            this.linking = null;
            this.linkUrl = null;
        }
        if (this.unlinking != null && !this.ui.tasks.running().contains(this.unlinking)) {
            this.unlinking = null;
        }
    }

    private void ensureLoaded(final Account account) {
        if (!account.uuid.equals(this.loadedFor)) {
            this.status = null;
            this.error = null;
            this.reload(account);
        }
    }

    private void reload(final Account account) {
        if (this.loading) {
            return;
        }
        this.loading = true;
        this.loadedFor = account.uuid;
        this.error = null;
        Progress progress = new Progress("Cosmetics");
        this.loader.execute(() -> {
            try {
                CosmeticsClient.Status loaded = this.withSession(account, progress, token -> this.ui.cosmetics.status(token));
                this.ui.tasks.onUi(() -> {
                    this.loading = false;
                    if (account.uuid.equals(this.loadedFor)) {
                        this.status = loaded;
                    }
                });
            } catch (Exception e) {
                String message = message(e);
                this.ui.tasks.onUi(() -> {
                    this.loading = false;
                    if (account.uuid.equals(this.loadedFor)) {
                        this.error = message;
                    }
                });
            }
        });
    }

    private void startLink(final Account account) {
        this.error = null;
        this.linking = this.ui.tasks.submit("Linking Patreon", progress -> {
            progress.status("Opening Patreon…");
            CosmeticsClient.PatreonLink link = this.withSession(account, progress, token -> this.ui.cosmetics.startPatreonLink(token));
            this.ui.tasks.onUi(() -> {
                this.linkUrl = link.url();
                Desktop.browse(link.url());
            });
            progress.status("Approve ABNW in your browser to finish linking.");
            while (System.currentTimeMillis() < link.expiresAtMillis()) {
                for (long waited = 0; waited < POLL_MILLIS; waited += 250) {
                    progress.checkCancelled();
                    Thread.sleep(250);
                }
                CosmeticsClient.Status current = this.withSession(account, progress, token -> this.ui.cosmetics.status(token));
                if (current.patreon().linked()) {
                    return current;
                }
            }
            throw new IOException("Patreon linking timed out. Choose Link Patreon to try again.");
        }, linked -> {
            if (account.uuid.equals(this.loadedFor)) {
                this.status = linked;
            }
        }, failure -> this.error = message(failure));
    }

    private void startUnlink(final Account account) {
        this.error = null;
        this.unlinking = this.ui.tasks.submit("Unlinking Patreon", progress -> this.withSession(account, progress, token -> {
            this.ui.cosmetics.unlinkPatreon(token);
            return this.ui.cosmetics.status(token);
        }), unlinked -> {
            if (account.uuid.equals(this.loadedFor)) {
                this.status = unlinked;
            }
        }, failure -> this.error = message(failure));
    }

    private <T> T withSession(final Account account, final Progress progress, final Call<T> call) throws Exception {
        if (account.devOffline) {
            throw new MicrosoftAuth.AuthException("Offline accounts can't use cosmetics. Sign in with Microsoft.");
        }
        String token = account.cosmeticsToken;
        if (token != null && !token.isEmpty()) {
            try {
                return call.run(token);
            } catch (CosmeticsClient.UnauthorizedException expired) {
                account.cosmeticsToken = "";
            }
        }
        progress.status("Confirming " + account.name + " with Minecraft…");
        this.ui.auth().refresh(account, progress);
        String issued = this.ui.cosmetics.signIn(account);
        account.cosmeticsToken = issued;
        this.ui.accounts.save();
        return call.run(issued);
    }

    private static String unlockedNames(final CosmeticsClient.Status status) {
        CosmeticRegistry registry;
        try {
            registry = CosmeticRegistry.bundled();
        } catch (InvalidRegistryException e) {
            return "";
        }
        return status.unlocked().stream().map(registry::get).flatMap(Optional::stream).map(Cosmetic::name).collect(Collectors.joining(", "));
    }

    private static String message(final Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Something went wrong. Try again in a moment." : message;
    }
}
