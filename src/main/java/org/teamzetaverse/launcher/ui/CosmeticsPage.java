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
    private long nextSync;
    private Progress linking;
    private String linkUrl;
    private Progress unlinking;
    private Progress purchasing;

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

        var identities = this.ui.cosmetics.identities();
        Widgets.textWrapped(Fonts.body, Theme.MUTED, "Your ABNW cosmetic identity works with any Minecraft profile, including offline profiles.");
        for (var identity : identities.all()) {
            if (Widgets.secondary("identity-" + identity.id(), identity.label() + " · " + identity.id().substring(5, 13),
                Icons.Icon.USER, !identity.id().equals(identities.selected()))) {
                try { identities.select(identity.id()); } catch (IOException e) { this.error = message(e); }
            }
        }
        String id = identities.selected();
        if (id.isEmpty()) {
            Optional<Account> account = this.ui.currentAccount().filter(a0 -> !a0.devOffline);
            this.hero("cos-setup", width, "ABNW IDENTITY", "Set up your cosmetics",
                "Create a cosmetic identity once. Your saved cosmetics will then work online and offline.", Icons.Icon.PENGUIN_KEY, () -> {
                    if (Widgets.primary("cos-setup-button", account.isPresent() ? "Set up cosmetics" : "Sign in to the launcher", Icons.Icon.USER, 0, px(48), true)) {
                        if (account.isPresent()) this.ui.initializeCosmetics(account.get()); else this.ui.startSignIn();
                    }
                });
        } else {
            this.ensureLoaded(id);
            if (this.status == null) this.drawLoadingOrError(width, id);
            else if (!this.status.patreon().linked()) this.drawLink(width, id);
            else this.drawLinked(width, id);
            ImGui.dummy(0, px(8));
            this.drawWardrobe(width, id);
        }
        ImGui.endGroup();
    }

    private void drawLoadingOrError(final float width, final String account) {
        if (this.error != null && !this.loading) {
            this.hero("cos-error", width, "COSMETICS", "Couldn't reach cosmetics", this.error, Icons.Icon.PENGUIN_ALERT, () -> {
                if (Widgets.secondary("cos-retry", "Try again", Icons.Icon.REFRESH)) {
                    this.reload(account);
                }
            });
            return;
        }
        this.hero("cos-loading", width, "COSMETICS", "Checking your cosmetics…", "Loading your saved ABNW entitlements and checking for updates.",
            Icons.Icon.PENGUIN_CLOCK, null);
        Motion.keepAlive();
    }

    private void drawLink(final float width, final String account) {
        String body = "Members of the official ABNW Patreon unlock supporter cosmetics. They're purely visual, never affect gameplay, "
            + "and stay unlocked for as long as you support.";
        this.hero("cos-link", width, "ABNW SUPPORTERS", "Link your Patreon", body, Icons.Icon.PENGUIN_SUPPORTER, () -> {
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

    private void drawLinked(final float width, final String account) {
        CosmeticsClient.Patreon patreon = this.status.patreon();
        String body = patreon.active()
            ? "Thank you for supporting ABNW! Supporter cosmetics are being made right now and will unlock for you automatically. "
                + "Cosmetics you buy in game will be yours to keep forever."
            : "Supporter cosmetics are being made right now. Your Patreon is linked, but it isn't an active membership of the official "
                + "ABNW Patreon, so they'll unlock as soon as it is.";
        this.hero("cos-coming", width, "COSMETICS", "Cosmetics coming soon", body, Icons.Icon.SOON, null);
        ImGui.dummy(0, px(8));

        if (Widgets.beginCard("cos-patreon", width, 0, Theme.SURFACE, px(26), px(22))) {
            Widgets.cardTitle(Icons.Icon.PENGUIN_SUPPORTER, "Patreon");
            Widgets.pill("Linked", Theme.OK, Theme.OK, 0.13f, Icons.Icon.CHECK);
            ImGui.sameLine(0, px(8));
            if (patreon.active()) {
                Widgets.pill("Active patron", Theme.SUN, Theme.SUN, 0.13f, Icons.Icon.HEART);
            } else if ("token_revoked".equals(patreon.status())) {
                Widgets.pill("Access removed on Patreon", Theme.WARN, Theme.WARN, 0.13f, Icons.Icon.ALERT);
            } else {
                Widgets.pill("Not an active patron", Theme.MUTED, 0xFFFFFF, 0.06f, null);
            }
            ImGui.dummy(0, px(4));
            String who = patreon.fullName().isEmpty() ? "" : "Patreon account " + patreon.fullName() + " is linked to " + "your ABNW identity" + ".";
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

    private void drawWardrobe(final float width, final String account) {
        PenguinWardrobe wardrobe = this.ui.wardrobe;
        if (Widgets.beginCard("cos-wardrobe", width, 0, Theme.SURFACE, px(26), px(22))) {
            Widgets.cardTitle(Icons.Icon.COSMETICS, "Penguin wardrobe");
            Widgets.textWrapped(Fonts.body, Theme.MUTED, "Dress up the penguin on your instance icons. The top hat, party hat, beanie, sunglasses, "
                + "monocle, scarves and bow tie are free for everyone; pick them on an instance's Icon tab.");
            ImGui.dummy(0, px(8));
            java.util.List<PenguinWardrobe.Accessory> items = wardrobe.store();
            if (items.isEmpty()) {
                Widgets.mascotMessage(Icons.Icon.SOON, wardrobe.storeLoading() ? "Opening the wardrobe…"
                    : "More accessories are on their way to the store.", Theme.MUTED);
            } else {
                float gap = px(12);
                int columns = width >= px(640) ? 3 : 2;
                float inner = ImGui.getContentRegionAvailX();
                float cardWidth = (inner - gap * (columns - 1)) / columns;
                for (int i = 0; i < items.size(); i++) {
                    if (i % columns != 0) {
                        ImGui.sameLine(0, gap);
                    }
                    this.storeItem(items.get(i), cardWidth, account);
                }
            }
        }
        Widgets.endCard();
    }

    private void storeItem(final PenguinWardrobe.Accessory item, final float width, final String account) {
        float h = px(132);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.rect(dl, x, y, x + width, y + h, u32(Theme.BG));
        Pixel.frame(dl, x, y, x + width, y + h, u32(Theme.BORDER_SOFT), px(1));
        float art = px(92);
        float ax = x + px(12);
        float ay = y + (h - art) * 0.5f;
        Pixel.rect(dl, ax, ay, ax + art, ay + art, u32(Theme.SURFACE_HI));
        PenguinWardrobe.drawBust(dl, ax, ay, art, java.util.List.of(item.texture()));
        float tx = ax + art + px(14);
        float tw = x + width - tx - px(12);
        ImGui.setCursorScreenPos(tx, y + px(14));
        ImGui.beginGroup();
        Widgets.text(Fonts.label, Theme.TEXT, Widgets.ellipsize(Fonts.label, item.name(), tw));
        Widgets.drawTextWrapped(dl, Fonts.small, tx, ImGui.getCursorScreenPosY(), u32(Theme.FAINT), item.description(), tw);
        ImGui.setCursorScreenPos(tx, y + h - px(50));
        boolean owned = this.ui.wardrobe.owns(item);
        if (owned) {
            Widgets.pill("Owned", Theme.OK, Theme.OK, 0.13f, Icons.Icon.CHECK);
        } else {
            boolean busy = this.purchasing != null;
            if (Widgets.primary("buy-" + item.id(), busy ? "Waiting…" : "Buy " + item.price(), Icons.Icon.HEART, 0, px(38), !busy)) {
                this.startPurchase(account, item);
            }
        }
        ImGui.endGroup();
        ImGui.setCursorScreenPos(x, y);
        ImGui.dummy(width, h);
    }

    private void startPurchase(final String account, final PenguinWardrobe.Accessory item) {
        this.error = null;
        this.purchasing = this.ui.tasks.submit("Buying " + item.name(), progress -> {
            progress.status("Opening checkout…");
            String url = this.withSession(account, progress, token -> this.ui.cosmetics.startCheckout(token, item.id()));
            this.ui.tasks.onUi(() -> Desktop.browse(url));
            progress.status("Finish paying in your browser. " + item.name() + " unlocks as soon as it goes through.");
            long deadline = System.currentTimeMillis() + 20L * 60 * 1000;
            while (System.currentTimeMillis() < deadline) {
                for (long waited = 0; waited < POLL_MILLIS; waited += 250) {
                    progress.checkCancelled();
                    Thread.sleep(250);
                }
                CosmeticsClient.Status current = this.withSession(account, progress, token -> this.ui.cosmetics.status(token));
                if (current.unlocked().contains(item.id())) {
                    return current;
                }
            }
            throw new IOException("Checkout timed out. If you paid, " + item.name() + " will appear the next time the launcher checks.");
        }, bought -> {
            if (account.equals(this.loadedFor)) {
                this.status = bought;
            }
        }, failure -> this.error = message(failure));
    }

    java.util.Set<String> unlocked() {
        return this.ui.cosmetics.unlocked();
    }

    void load() {
        String id = this.ui.cosmetics.identities().selected();
        if (!id.isEmpty()) this.ensureLoaded(id);
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
        Pixel.card(dl, x, y, x + width, y + h, 0f, u32(Theme.SURFACE));
        if (showIcon) {
            float gx = x + width - pad - art * 0.5f;
            float gy = y + h * 0.5f;
            float radius = Math.min(h * 0.42f, art * 0.5f);
            for (int i = 4; i >= 1; i--) {
                Pixel.dot(dl, gx, gy, radius * (0.45f + 0.15f * i), u32(Theme.EMBER, 0.035f));
            }
            Pixel.frame(dl, gx - radius * 0.62f, gy - radius * 0.62f, gx + radius * 0.62f, gy + radius * 0.62f, u32(Theme.EMBER, 0.35f), px(1.5f));
            float size = radius * 0.72f;
            Icons.draw(dl, icon, gx - size * 0.5f, gy - size * 0.5f, size, u32(Theme.EMBER));
        }
        Pixel.frame(dl, x, y, x + width, y + h, u32(Theme.EMBER_LO, 0.45f), px(1));

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
        if (this.purchasing != null && !this.ui.tasks.running().contains(this.purchasing)) {
            this.purchasing = null;
        }
    }

    private void ensureLoaded(final String account) {
        if (!account.equals(this.loadedFor)) {
            this.loadedFor = account;
            this.status = null;
            this.error = null;
            this.loading = false;
            this.nextSync = 0;
            try { this.status = this.ui.cosmetics.cachedStatus(account); }
            catch (IOException e) { this.error = message(e); }
        }
        if (!this.loading && System.currentTimeMillis() >= this.nextSync) this.reload(account);
    }

    private void reload(final String account) {
        if (this.loading) {
            return;
        }
        this.loading = true;
        this.nextSync = System.currentTimeMillis() + 60000;
        this.loadedFor = account;
        this.error = null;
        Progress progress = new Progress("Cosmetics");
        this.loader.execute(() -> {
            try {
                CosmeticsClient.Status loaded = this.withSession(account, progress, token -> this.ui.cosmetics.status(token));
                this.ui.tasks.onUi(() -> {
                    if (account.equals(this.loadedFor)) {
                        this.loading = false;
                        this.status = loaded;
                    }
                });
            } catch (Exception e) {
                String message = message(e);
                this.ui.tasks.onUi(() -> {
                    if (account.equals(this.loadedFor)) {
                        this.loading = false;
                        this.error = message;
                    }
                });
            }
        });
    }

    private void startLink(final String account) {
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
            if (account.equals(this.loadedFor)) {
                this.status = linked;
            }
        }, failure -> this.error = message(failure));
    }

    private void startUnlink(final String account) {
        this.error = null;
        this.unlinking = this.ui.tasks.submit("Unlinking Patreon", progress -> this.withSession(account, progress, token -> {
            this.ui.cosmetics.unlinkPatreon(token);
            return this.ui.cosmetics.status(token);
        }), unlinked -> {
            if (account.equals(this.loadedFor)) {
                this.status = unlinked;
            }
        }, failure -> this.error = message(failure));
    }

    private <T> T withSession(final String account, final Progress progress, final Call<T> call) throws Exception {
        progress.checkCancelled();
        return call.run(account);
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
