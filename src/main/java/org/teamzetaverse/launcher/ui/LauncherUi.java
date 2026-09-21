package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.LauncherConfig;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.auth.Account;
import org.teamzetaverse.launcher.auth.AccountStore;
import org.teamzetaverse.launcher.auth.MicrosoftAuth;
import org.teamzetaverse.launcher.discord.DiscordPresence;
import org.teamzetaverse.launcher.feed.Feed;
import org.teamzetaverse.launcher.feed.FeedService;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.instance.InstanceArchive;
import org.teamzetaverse.launcher.instance.InstanceStore;
import org.teamzetaverse.launcher.launch.GameLauncher;
import org.teamzetaverse.launcher.launch.GameProcess;
import org.teamzetaverse.launcher.minecraft.GameInstaller;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.release.ReleaseService;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.task.Tasks;
import org.teamzetaverse.launcher.update.UpdateChecker;

public final class LauncherUi {
    enum Page {
        HOME, INSTANCES, NEWS, COSMETICS, SETTINGS
    }

    enum InstallState {
        READY, NEEDS_INSTALL, PLAYING, BUSY
    }

    static final String[] RENDERERS = {"auto", "vulkan", "opengl"};
    static final String[] RENDERER_LABELS = {"Automatic", "Vulkan", "OpenGL"};

    final LauncherPaths paths;
    final LauncherConfig config;
    final AccountStore accounts;
    final InstanceStore instances;
    final ReleaseService releases = new ReleaseService();
    final GameInstaller installer;
    final GameLauncher gameLauncher;
    final Tasks tasks = new Tasks();
    final Map<String, GameProcess> running = new ConcurrentHashMap<>();
    final ImageCache images;
    final Showcase showcase;
    final World world = new World();
    private float contentScroll;
    private boolean resetScroll;
    final Announcements announcements;
    final DiscordPresence discord;
    final List<String> errors = new ArrayList<>();
    final Dialogs dialogs;
    final org.teamzetaverse.launcher.cosmetics.CosmeticsClient cosmetics = new org.teamzetaverse.launcher.cosmetics.CosmeticsClient();

    private final FeedService feedService;
    private final UpdateChecker updateChecker;
    private final ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ABNW Launcher background");
        thread.setDaemon(true);
        return thread;
    });
    private final HomePage home;
    private final InstancesPage instancesPage;
    private final NewsPage newsPage;
    private final CosmeticsPage cosmeticsPage;
    private final SettingsPage settingsPage;
    private final Map<String, long[]> installCache = new HashMap<>();
    private final long openedAt = System.currentTimeMillis() / 1000L;

    Feed feed = Feed.empty();
    org.teamzetaverse.launcher.feed.Changelog launcherChangelog = org.teamzetaverse.launcher.feed.Changelog.empty();
    org.teamzetaverse.launcher.feed.Changelog abnwChangelog = org.teamzetaverse.launcher.feed.Changelog.empty();
    UpdateChecker.Status updateStatus = UpdateChecker.Status.pending();
    volatile boolean checkingUpdates;
    String selectedInstanceId;
    Page page = Page.HOME;
    private List<Showcase.Slide> feedSlides = List.of();
    private List<Showcase.Slide> localSlides = List.of();

    public LauncherUi(final LauncherPaths paths, final LauncherConfig config) {
        this.paths = paths;
        this.config = config;
        this.accounts = new AccountStore(paths);
        this.instances = new InstanceStore(paths);
        this.installer = new GameInstaller(paths, config);
        this.gameLauncher = new GameLauncher(paths, config);
        this.selectedInstanceId = config.selectedInstance;
        this.images = new ImageCache(paths.cache().resolve("images"));
        Widgets.useImages(this.images);
        Pixel.useImages(this.images);
        this.showcase = new Showcase(this.images);
        this.announcements = new Announcements(config);
        this.discord = new DiscordPresence(BuildInfo.DISCORD_CLIENT_ID, config.discordPresence);
        this.feedService = new FeedService(paths);
        this.updateChecker = new UpdateChecker(this.releases);
        this.dialogs = new Dialogs(this);
        this.home = new HomePage(this);
        this.instancesPage = new InstancesPage(this);
        this.newsPage = new NewsPage(this);
        this.cosmeticsPage = new CosmeticsPage(this);
        this.settingsPage = new SettingsPage(this);
        this.applyFeed(FeedService.bundled());

        this.background.scheduleWithFixedDelay(this::refreshFeed, 0, 30, TimeUnit.MINUTES);
        this.background.scheduleWithFixedDelay(this::runUpdateCheck, 2, 30 * 60, TimeUnit.SECONDS);
        this.background.scheduleWithFixedDelay(this::scanScreenshots, 1, 5 * 60, TimeUnit.SECONDS);
    }

    public float uiScale() {
        return Math.max(0.75f, Math.min(2f, this.config.uiScale));
    }

    public boolean isAnimating() {
        return this.tasks.isBusy() || Motion.isMoving() || this.showcase.isAnimating() || this.images.isLoading();
    }

    public void beforeFrame() {
        this.tasks.drainUiQueue();
    }

    public void shutdown() {
        this.config.save();
        this.accounts.flush();
        this.background.shutdownNow();
        this.discord.close();
        this.tasks.shutdown();
        this.images.dispose();
    }

    public void draw() {
        Motion.beginFrame();
        this.images.beginFrame();
        this.updatePresence();

        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, 0);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean open = ImGui.begin("ABNW Launcher", flags);
        ImGui.popStyleVar(2);
        if (open) {
            float vx = ImGui.getWindowPosX();
            float vy = ImGui.getWindowPosY();
            float vw = ImGui.getWindowWidth();
            float vh = ImGui.getWindowHeight();
            float bar = topBarHeight();
            float status = statusBarHeight();
            ImGui.setCursorScreenPos(vx, vy + bar);
            this.drawContent(vy, vh - bar - status);
            this.drawTopBar(vx, vy, vw, bar);
            this.drawStatusBar(vx, vy + vh - status, vw, status);
            ImDrawList front = ImGui.getWindowDrawList();
            float edge = Pixel.unit();
            front.addRectFilled(vx, vy + bar, vx + edge, vy + vh - status, u32(Theme.BORDER));
            front.addRectFilled(vx + vw - edge, vy + bar, vx + vw, vy + vh - status, u32(Theme.BORDER));
            this.dialogs.draw();
        }
        ImGui.end();

        float tasksHeight = this.drawTasks();
        this.announcements.drawToasts(tasksHeight + statusBarHeight());
    }

    static float topBarHeight() {
        return Pixel.unit() * 34f;
    }

    static float statusBarHeight() {
        return Pixel.unit() * 20f;
    }

    public double idleWait() {
        return 0.05;
    }

    private void drawTopBar(final float x, final float y, final float w, final float h) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float u = Pixel.unit();
        dl.addRectFilled(x, y, x + w, y + h, u32(Theme.SIDEBAR));
        Pixel.tile(dl, Pixel.STARS, x, y, x + w, y + h - u * 2, u, u32(0xFFFFFF, 0.25f));
        dl.addRectFilled(x, y + h - u * 2, x + w, y + h - u, u32(0x10041A));
        dl.addRectFilled(x, y + h - u, x + w, y + h, u32(Theme.BORDER));

        float markScale = u * 2f;
        float markY = y + (h - u * 2 - PixelText.height(markScale)) * 0.5f;
        ImGui.setCursorScreenPos(x + u * 8, y);
        float markWidth = PixelText.width("ABNW", markScale);
        if (ImGui.invisibleButton("wordmark", markWidth, h - u * 2)) {
            this.navigate(Page.HOME);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        PixelText.draw(dl, x + u * 8, markY, "ABNW", markScale, u32(0xC100FF));

        float tx = x + u * 8 + markWidth + u * 18;
        float tabH = h - u * 12;
        float tabY = y + u * 4;
        tx = this.drawTab(Page.HOME, Icons.Icon.HOME, "Home", tx, tabY, tabH);
        tx = this.drawTab(Page.INSTANCES, Icons.Icon.INSTANCES, "Instances", tx, tabY, tabH);
        tx = this.drawTab(Page.NEWS, Icons.Icon.NEWS, "News", tx, tabY, tabH);
        tx = this.drawTab(Page.COSMETICS, Icons.Icon.CROWN, "Cosmetics", tx, tabY, tabH);
        this.drawTab(Page.SETTINGS, Icons.Icon.SETTINGS, "Settings", tx, tabY, tabH);

        float accountW = Math.min(px(236), w * 0.24f);
        this.drawAccount(x + w - accountW - u * 6, tabY, accountW, tabH);
    }

    private float drawTab(final Page target, final Icons.Icon icon, final String label, final float x, final float y, final float h) {
        float u = Pixel.unit();
        String text = label.toUpperCase();
        float iconSize = u * 16f;
        float w = u * 10 + iconSize + u * 4 + PixelText.width(text, u) + u * 10;
        ImGui.setCursorScreenPos(x, y);
        String id = "tab-" + target.name();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        boolean active = this.page == target;
        float hv = Motion.hover(id + "#hover", hovered);
        float av = Motion.to(id + "#active", active ? 1f : 0f, 14f);
        ImDrawList dl = ImGui.getWindowDrawList();
        float lift = Math.round(u * (1f - av) * hv);
        if (hv * (1f - av) > 0.01f) {
            Pixel.nine(dl, Pixel.PANEL_HOVER, x, y, x + w, y + h, 4, u32(0xFFFFFF, hv * (1f - av)));
        }
        if (av > 0.01f) {
            Pixel.three(dl, Pixel.BUTTON_PRIMARY, x, y, x + w, y + h, 11, u32(0xFFFFFF, av));
        }
        int tint = u32(Theme.mix(Theme.mix(0x9670B4, Theme.TEXT, hv), 0xFFFFFF, av));
        float face = h * (av > 0.5f ? 0.82f : 1f);
        Icons.draw(dl, icon, x + u * 10, y + (face - iconSize) * 0.5f - lift, iconSize, tint);
        PixelText.draw(dl, x + u * 10 + iconSize + u * 4, y + (face - PixelText.height(u)) * 0.5f - lift, text, u, tint);
        if (clicked) {
            this.navigate(target);
        }
        return x + w + u * 3;
    }

    private void drawStatusBar(final float x, final float y, final float w, final float h) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float u = Pixel.unit();
        dl.addRectFilled(x, y, x + w, y + h, u32(Theme.SIDEBAR));
        dl.addRectFilled(x, y, x + w, y + u, u32(Theme.BORDER));
        float mid = y + u + (h - u - PixelText.height(u)) * 0.5f;
        float iconSize = u * 8f;
        float iconY = y + u + (h - u - iconSize) * 0.5f;

        List<Progress> running = this.tasks.running();
        Optional<Instance> selected = this.selectedInstance();
        GameProcess playing = selected.map(i -> this.running.get(i.id)).orElse(null);
        String message;
        Icons.Icon icon;
        int colour;
        if (!running.isEmpty()) {
            message = running.get(0).title();
            icon = Icons.Icon.DOWNLOAD;
            colour = Theme.SUN;
        } else if (playing != null) {
            message = "Playing " + selected.get().name;
            icon = Icons.Icon.PLAY;
            colour = Theme.OK;
        } else if (this.currentAccount().isEmpty()) {
            message = "Sign in to play";
            icon = Icons.Icon.USER;
            colour = Theme.MUTED;
        } else {
            message = "Ready to play";
            icon = Icons.Icon.CHECK;
            colour = Theme.OK;
        }
        Icons.draw(dl, icon, x + u * 6, iconY, iconSize, u32(colour));
        float textX = x + u * 6 + iconSize + u * 5;
        PixelText.draw(dl, textX, mid, PixelText.fit(message.toUpperCase(), u, w * 0.36f), u, u32(Theme.MUTED));

        if (!running.isEmpty()) {
            float fraction = (float)running.get(0).fraction();
            float barW = Math.min(px(260), w * 0.24f);
            float bx = x + (w - barW) * 0.5f;
            float by = y + u + (h - u - u * 4) * 0.5f;
            if (!Pixel.three(dl, Pixel.PROGRESS_TRACK, bx, by, bx + barW, by + u * 4, 2, u32(0xFFFFFF))) {
                Pixel.rect(dl, bx, by, bx + barW, by + u * 4, u32(0xFFFFFF, 0.08f));
            }
            float fill = fraction < 0 ? barW * (0.5f + 0.5f * (float)Math.sin(ImGui.getTime() * 3.0)) : barW * Math.min(1f, fraction);
            if (fill > u * 4) {
                Pixel.three(dl, Pixel.PROGRESS_FILL, bx, by, bx + fill, by + u * 4, 2, u32(0xFFFFFF));
            }
            Motion.keepAlive();
        }

        String version = BuildInfo.isDevBuild() ? "DEVELOPMENT BUILD" : "LAUNCHER " + BuildInfo.VERSION;
        float vw = PixelText.width(version, u);
        float right = x + w - u * 6;
        PixelText.draw(dl, right - vw, mid, version, u, u32(Theme.FAINT));
        UpdateChecker.LauncherUpdate update = this.updateStatus.launcherUpdate();
        if (update != null) {
            String label = "UPDATE READY";
            float alertSize = u * 12f;
            float lw = PixelText.width(label, u) + alertSize + u * 16;
            float lx = right - vw - u * 10 - lw;
            float ly = y + u * 3;
            ImGui.setCursorScreenPos(lx, ly);
            boolean clicked = ImGui.invisibleButton("status-update", lw, h - u * 5);
            boolean hovered = ImGui.isItemHovered();
            if (hovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                Widgets.tooltip("Get launcher v" + update.version());
            }
            float hv = Motion.hover("status-update#hover", hovered);
            Pixel.three(dl, Pixel.BUTTON_PRIMARY, lx, ly, lx + lw, ly + h - u * 5, 11, u32(Theme.mix(0xDDD2E6, 0xFFFFFF, hv)));
            Icons.draw(dl, Icons.Icon.UPDATE, lx + u * 5, ly + h - u * 7 - alertSize, alertSize, u32(0xFFFFFF));
            PixelText.draw(dl, lx + u * 6 + alertSize + u * 4, ly + (h - u * 5) * 0.41f - PixelText.height(u) * 0.5f, label, u, u32(0xFFFFFF));
            if (clicked) {
                Desktop.browse(update.url());
            }
        }
    }

    private void drawAccount(final float x, final float y, final float w, final float h) {
        Optional<Account> account = this.currentAccount();
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton("account", w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover("account#hover", hovered || ImGui.isPopupOpen("account-menu"));
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.card(dl, x, y, x + w, y + h, hv, u32(Theme.mix(Theme.SURFACE, Theme.SURFACE_HI, hv)));
        float avatar = h - Pixel.unit() * 8;
        float ax = x + Pixel.unit() * 5;
        float ay = y + (h - avatar) * 0.5f;
        String name = account.map(a -> a.name).orElse("Sign in");
        if (account.isPresent()) {
            Pixel.rect(dl, ax, ay, ax + avatar, ay + avatar, u32(Theme.SURFACE_HOVER));
            String headKey = account.get().devOffline ? null : PlayerHead.key(account.get().uuid);
            ImageCache.Texture head = headKey == null ? null : this.images.get(headKey, 0, true);
            if (head != null) {
                float scale = Math.max(1f, (float)Math.floor(avatar / head.width()));
                float size = head.width() * scale;
                float hx = Math.round(ax + (avatar - size) * 0.5f);
                float hy = Math.round(ay + (avatar - size) * 0.5f);
                dl.addImage(head.id(), hx, hy, hx + size, hy + size, 0f, 0f, 1f, 1f, u32(0xFFFFFF));
            } else {
                World.penguinIcon(dl, ax, ay, avatar);
            }
        } else {
            Pixel.rect(dl, ax, ay, ax + avatar, ay + avatar, u32(Theme.SURFACE_HOVER));
            Icons.draw(dl, Icons.Icon.USER, ax + px(8), ay + px(8), avatar - px(16), u32(Theme.MUTED));
        }
        float tx = ax + avatar + px(11);
        float textWidth = w - (tx - x) - px(30);
        Widgets.drawText(dl, Fonts.label, tx, y + px(11), u32(Theme.TEXT), Widgets.ellipsize(Fonts.label, name, textWidth));
        Widgets.drawText(dl, Fonts.small, tx, y + px(11) + Fonts.label.size() + px(1), u32(Theme.FAINT),
            account.isPresent() ? (account.get().devOffline ? "Offline account" : "Microsoft account") : "Microsoft account needed");
        Icons.draw(dl, Icons.Icon.CHEVRON_DOWN, x + w - px(26), y + (h - px(14)) * 0.5f, px(14), u32(Theme.MUTED));
        if (clicked) {
            if (account.isEmpty() && this.accounts.all().isEmpty()) {
                this.startSignIn();
            } else {
                ImGui.openPopup("account-menu");
            }
        }
        ImGui.setNextWindowPos(x + w, y + h + px(8), 0, 1f, 0f);
        ImGui.setNextWindowSize(Math.max(w, px(260)), 0);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(8), px(8));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(4), px(4));
        if (ImGui.beginPopup("account-menu")) {
            for (Account entry : this.accounts.all()) {
                boolean selected = account.isPresent() && account.get().uuid.equals(entry.uuid);
                if (this.menuItem("acct-" + entry.uuid, selected ? Icons.Icon.CHECK : Icons.Icon.USER, entry.name, selected)) {
                    this.config.selectedAccount = entry.uuid;
                    this.config.save();
                    ImGui.closeCurrentPopup();
                }
            }
            if (this.menuItem("acct-add", Icons.Icon.PLUS, "Add another account", false)) {
                ImGui.closeCurrentPopup();
                this.startSignIn();
            }
            if (account.isPresent() && this.menuItem("acct-remove", Icons.Icon.LOGOUT, "Sign out " + account.get().name, false)) {
                this.accounts.remove(account.get());
                this.config.selectedAccount = this.accounts.all().isEmpty() ? "" : this.accounts.all().get(0).uuid;
                this.config.save();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
        ImGui.popStyleVar(2);
    }

    boolean menuItem(final String id, final Icons.Icon icon, final String label, final boolean highlighted) {
        float w = Math.max(ImGui.getContentRegionAvailX(), px(200));
        float h = px(36);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        ImDrawList dl = ImGui.getWindowDrawList();
        if (hovered) {
            Pixel.rect(dl, x, y, x + w, y + h, u32(0xFFFFFF, 0.06f));
        }
        int color = u32(highlighted ? Theme.EMBER : hovered ? Theme.TEXT : Theme.MUTED);
        Icons.draw(dl, icon, x + px(10), y + (h - px(16)) * 0.5f, px(16), color);
        Widgets.drawText(dl, Fonts.body, x + px(36), y + (h - Fonts.body.size()) * 0.5f - px(0.5f), u32(highlighted || hovered ? Theme.TEXT : Theme.MUTED),
            Widgets.ellipsize(Fonts.body, label, w - px(46)));
        return clicked;
    }

    private void drawContent(final float windowTop, final float height) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(36), px(30));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(12), px(10));
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, px(8));
        boolean open = ImGui.beginChild("content", 0, height, ImGuiChildFlags.AlwaysUseWindowPadding, 0);
        ImGui.popStyleVar(3);
        if (open) {
            if (this.resetScroll) {
                ImGui.setScrollY(0);
                this.resetScroll = false;
            }
            this.contentScroll = ImGui.getScrollY();
            float cx = ImGui.getWindowPosX();
            float cy = ImGui.getWindowPosY();
            this.world.draw(ImGui.getWindowDrawList(), cx, windowTop, cx + ImGui.getWindowWidth(), cy + ImGui.getWindowHeight(), this.page, this.contentScroll);
            float alpha = Motion.to("page#alpha", 1f, 11f);
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, Math.max(0.01f, alpha));
            ImGui.setCursorPosY(ImGui.getCursorPosY() + (1f - alpha) * px(12));
            switch (this.page) {
                case HOME -> this.home.draw();
                case INSTANCES -> this.instancesPage.draw();
                case NEWS -> this.newsPage.draw();
                case COSMETICS -> this.cosmeticsPage.draw();
                case SETTINGS -> this.settingsPage.draw();
            }
            ImGui.dummy(0, this.tasks.running().isEmpty() ? px(4) : px(90));
            ImGui.popStyleVar();
        }
        ImGui.endChild();
    }
    private float drawTasks() {
        List<Progress> running = this.tasks.running();
        if (running.isEmpty()) {
            return 0;
        }
        ImGuiViewport viewport = ImGui.getMainViewport();
        float width = Math.min(px(520), viewport.getWorkSizeX() - px(72));
        float centerX = viewport.getWorkPosX() + viewport.getWorkSizeX() * 0.5f;
        float bottom = viewport.getWorkPosY() + viewport.getWorkSizeY() - statusBarHeight() - px(14);
        float[] bg = Theme.rgba(Theme.SURFACE_HI, 1f);
        float[] border = Theme.rgba(Theme.BORDER, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(18), px(14));
        boolean pixel = Pixel.ready();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, pixel ? 0f : px(1));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(10), px(6));
        ImGui.pushStyleColor(ImGuiCol.WindowBg, bg[0], bg[1], bg[2], pixel ? 0f : 0.97f);
        ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], 1f);
        ImGui.setNextWindowPos(centerX, bottom, 0, 0.5f, 1f);
        ImGui.setNextWindowSize(width, 0);
        float height = 0;
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.AlwaysAutoResize;
        if (ImGui.begin("##tasks", flags)) {
            if (pixel) {
                Pixel.windowPanel();
            }
            int index = 0;
            for (Progress progress : running) {
                ImGui.pushID(index++);
                float fraction = progress.fraction();
                float textWidth = ImGui.getContentRegionAvailX() - px(40);
                Widgets.text(Fonts.label, Theme.TEXT, Widgets.ellipsize(Fonts.label, progress.title(), textWidth));
                ImGui.sameLine();
                Widgets.alignRight(px(28));
                if (Widgets.iconButton("cancel", Icons.Icon.CLOSE, px(28), "Cancel", true)) {
                    progress.cancel();
                }
                String status = progress.status().isEmpty() ? "Working…" : progress.status();
                if (fraction >= 0) {
                    status = Math.round(fraction * 100) + "%  ·  " + status;
                }
                ImGui.setCursorPosY(ImGui.getCursorPosY() - px(6));
                Widgets.text(Fonts.small, Theme.MUTED, Widgets.ellipsize(Fonts.small, status, ImGui.getContentRegionAvailX()));
                Widgets.progress(fraction, -1, px(6));
                if (index < running.size()) {
                    ImGui.dummy(0, px(6));
                }
                ImGui.popID();
            }
            height = ImGui.getWindowHeight() + px(12);
        }
        ImGui.end();
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(4);
        return height;
    }

    void navigate(final Page target) {
        if (this.page != target) {
            this.page = target;
            this.resetScroll = true;
            Motion.set("page#alpha", 0f);
        }
    }

    Optional<Instance> selectedInstance() {
        if (this.selectedInstanceId != null) {
            Optional<Instance> found = this.instances.find(this.selectedInstanceId);
            if (found.isPresent()) {
                return found;
            }
        }
        return this.instances.all().stream().max(Comparator.comparingLong(i -> Math.max(i.lastPlayed, i.created)));
    }

    void select(final Instance instance) {
        this.selectedInstanceId = instance.id;
        this.config.selectedInstance = instance.id;
        this.config.save();
    }

    Optional<Account> currentAccount() {
        Optional<Account> selected = this.accounts.find(this.config.selectedAccount);
        if (selected.isPresent() || this.accounts.all().isEmpty()) {
            return selected;
        }
        return Optional.of(this.accounts.all().get(0));
    }

    Release latestRelease() {
        return this.updateStatus.latestGame();
    }

    boolean isOutdated(final Instance instance) {
        Release latest = this.latestRelease();
        return latest != null && instance.release != null && !latest.id.equals(instance.release.id)
            && UpdateChecker.compare(latest.id, instance.release.id) > 0;
    }

    boolean isBusy(final Instance instance) {
        return this.tasks.running().stream().anyMatch(p -> p.title().endsWith(instance.name));
    }

    InstallState installState(final Instance instance) {
        if (this.running.containsKey(instance.id)) {
            return InstallState.PLAYING;
        }
        if (this.isBusy(instance)) {
            return InstallState.BUSY;
        }
        if (instance.release == null || !instance.release.isResolved()) {
            return InstallState.NEEDS_INSTALL;
        }
        String key = instance.id + ":" + instance.release.target.sha256;
        long now = System.currentTimeMillis();
        long[] cached = this.installCache.get(key);
        if (cached == null || now - cached[1] > 4000) {
            Path jar = this.paths.patchedJars().resolve(instance.release.patchedJarName());
            cached = new long[]{Files.isRegularFile(jar) ? 1 : 0, now};
            this.installCache.put(key, cached);
        }
        return cached[0] == 1 ? InstallState.READY : InstallState.NEEDS_INSTALL;
    }

    void fail(final Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        this.errors.add(message);
    }

    MicrosoftAuth auth() {
        return new MicrosoftAuth(this.config.effectiveClientId());
    }

    void play(final Instance instance) {
        Optional<Account> maybeAccount = this.currentAccount();
        if (maybeAccount.isEmpty()) {
            this.startSignIn();
            return;
        }
        Account account = maybeAccount.get();
        if (account.devOffline && !this.accounts.hasAuthenticatedAccount()) {
            this.fail(new MicrosoftAuth.AuthException(
                "An offline account needs a currently signed-in Microsoft account. Sign in, or launch your Microsoft account once to refresh its session."));
            return;
        }
        this.select(instance);
        this.tasks.submit("Launching " + instance.name, progress -> {
            progress.status("Signing in…");
            this.auth().refresh(account, progress);
            this.accounts.save();
            var installed = this.installer.install(instance.release, this.instances.readLibraries(instance), progress);
            progress.checkCancelled();
            progress.stage("Starting the game…", 0);
            return this.gameLauncher.launch(instance, installed, account, exited -> this.tasks.onUi(() -> this.gameExited(instance, exited)));
        }, process -> {
            this.running.put(instance.id, process);
            instance.lastPlayed = System.currentTimeMillis();
            this.saveQuietly(instance);
            this.installCache.clear();
        }, this::fail);
    }

    private void gameExited(final Instance instance, final GameProcess process) {
        this.running.remove(instance.id, process);
        instance.totalPlayMillis += System.currentTimeMillis() - process.startedMillis();
        this.saveQuietly(instance);
        this.instancesPage.rememberLog(instance, process.lines());
        this.background.execute(this::scanScreenshots);
    }

    void stop(final Instance instance) {
        GameProcess process = this.running.get(instance.id);
        if (process != null) {
            process.kill();
        }
    }

    void prepare(final Instance instance) {
        this.tasks.submit("Installing " + instance.name, progress -> {
            this.installer.install(instance.release, this.instances.readLibraries(instance), progress);
            return Boolean.TRUE;
        }, ok -> this.installCache.clear(), this::fail);
    }

    void saveQuietly(final Instance instance) {
        try {
            this.instances.save(instance);
        } catch (IOException e) {
            this.fail(e);
        }
    }

    void createInstance(final String name, final Release release, final int memory, final String renderer) {
        this.tasks.submit("Creating " + name, progress -> {
            progress.status("Fetching " + release.displayName() + "…");
            ReleaseService.Resolved resolved = this.releases.resolve(release);
            return this.instances.create(name, resolved, memory, renderer);
        }, created -> {
            this.select(created);
            this.navigate(Page.INSTANCES);
            this.prepare(created);
        }, this::fail);
    }

    void switchRelease(final Instance instance, final Release release) {
        this.tasks.submit("Switching " + instance.name, progress -> {
            progress.status("Fetching " + release.displayName() + "…");
            ReleaseService.Resolved resolved = this.releases.resolve(release);
            this.instances.setRelease(instance, resolved);
            return Boolean.TRUE;
        }, ok -> {
            this.installCache.clear();
            this.prepare(instance);
        }, this::fail);
    }

    boolean addOfflineAccount(final String name) {
        if (!this.accounts.hasAuthenticatedAccount()) {
            return false;
        }
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            return false;
        }
        Account account = new Account();
        account.devOffline = true;
        account.name = name;
        account.uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
        account.xuid = "0";
        this.accounts.put(account);
        this.config.selectedAccount = account.uuid;
        this.config.save();
        return true;
    }

    void startSignIn() {
        MicrosoftAuth auth = this.auth();
        this.tasks.submit("Connecting to Microsoft", progress -> auth.requestDeviceCode(), code -> {
            Progress signIn = this.tasks.submit("Signing in", progress -> auth.completeDeviceCode(code, progress), account -> {
                this.accounts.put(account);
                this.config.selectedAccount = account.uuid;
                this.config.save();
                this.dialogs.signInFinished();
            }, error -> {
                this.dialogs.signInFinished();
                this.fail(error);
            });
            this.dialogs.openSignIn(code, signIn);
        }, this::fail);
    }

    void importArchive() {
        Path archive = Desktop.chooseArchiveToOpen();
        if (archive == null) {
            return;
        }
        this.tasks.submit("Importing " + archive.getFileName(), progress -> {
            Instance described = InstanceArchive.peek(archive);
            progress.status("Fetching " + described.release.displayName() + "…");
            ReleaseService.Resolved resolved = this.releases.resolve(described.release);
            return InstanceArchive.importArchive(archive, this.instances, resolved, progress);
        }, imported -> {
            this.select(imported);
            this.navigate(Page.INSTANCES);
            this.prepare(imported);
        }, this::fail);
    }

    void exportArchive(final Instance instance) {
        Path target = Desktop.chooseArchiveToSave(instance.name.replaceAll("[^A-Za-z0-9 ._-]", ""));
        if (target == null) {
            return;
        }
        this.tasks.submit("Exporting " + instance.name, progress -> {
            InstanceArchive.export(instance, target, progress);
            return target;
        }, done -> {
            Announcements.Entry entry = new Announcements.Entry();
            entry.id = "export-" + System.nanoTime();
            entry.title = "Export complete";
            entry.message = instance.name + " is ready to share as " + done.getFileName() + ".";
            entry.level = "success";
            entry.once = false;
            entry.actionLabel = "Show file";
            entry.action = () -> Desktop.open(done.getParent());
            this.announcements.put(entry);
        }, this::fail);
    }

    void checkForUpdatesNow() {
        this.background.execute(this::runUpdateCheck);
    }

    private void runUpdateCheck() {
        if (this.checkingUpdates) {
            return;
        }
        this.checkingUpdates = true;
        try {
            UpdateChecker.Status status = this.updateChecker.check();
            this.tasks.onUi(() -> this.applyUpdateStatus(status));
        } catch (RuntimeException e) {
            System.err.println("Update check failed: " + e);
        } finally {
            this.checkingUpdates = false;
        }
    }

    private void applyUpdateStatus(final UpdateChecker.Status status) {
        this.updateStatus = status;
        UpdateChecker.LauncherUpdate update = status.launcherUpdate();
        if (update != null) {
            Announcements.Entry entry = new Announcements.Entry();
            entry.id = "launcher-update-" + update.version();
            entry.title = "A new launcher update is available";
            entry.message = "Launcher v" + update.version() + " is ready with the latest improvements.";
            entry.level = "update";
            entry.sticky = true;
            entry.actionLabel = "Download";
            entry.action = () -> Desktop.browse(update.url());
            this.announcements.put(entry);
        }
        Release latest = status.latestGame();
        if (latest != null) {
            if (this.config.lastSeenGameRelease.isEmpty()) {
                this.config.lastSeenGameRelease = latest.id;
                this.config.save();
            } else if (!this.config.lastSeenGameRelease.equals(latest.id)) {
                Announcements.Entry entry = new Announcements.Entry();
                entry.id = "game-release-" + latest.id;
                entry.title = latest.displayName() + " is here";
                entry.message = "A brand new build of A Brand New World is ready. Switch an instance over and dive in.";
                entry.level = "update";
                entry.style = "toast";
                entry.sticky = true;
                entry.actionLabel = "Take me there";
                entry.action = () -> this.navigate(Page.INSTANCES);
                entry.onDismiss = () -> {
                    this.config.lastSeenGameRelease = latest.id;
                    this.config.save();
                };
                this.announcements.put(entry);
            }
        }
    }

    private void refreshFeed() {
        try {
            FeedService.Loaded loaded = this.feedService.load();
            this.tasks.onUi(() -> this.applyFeed(loaded));
        } catch (RuntimeException e) {
            System.err.println("Feed refresh failed: " + e);
        }
    }

    private void applyFeed(final FeedService.Loaded loaded) {
        Feed next = loaded.news();
        this.feed = next;
        this.launcherChangelog = loaded.launcherChangelog();
        this.abnwChangelog = loaded.abnwChangelog();
        this.announcements.setFeed(next.announcements, Desktop::browse);
        List<Showcase.Slide> slides = new ArrayList<>();
        for (Feed.Screenshot shot : next.screenshots) {
            slides.add(new Showcase.Slide(shot.url, shot.caption, shot.credit));
        }
        this.feedSlides = slides;
        this.pushSlides();
    }

    void scanScreenshots() {
        try {
            this.scanScreenshotsUnsafe();
        } catch (RuntimeException e) {
            System.err.println("Screenshot scan failed: " + e);
        }
    }

    private void scanScreenshotsUnsafe() {
        List<Path> screenshotFolders = new ArrayList<>();
        for (Instance instance : List.copyOf(this.instances.all())) {
            screenshotFolders.add(instance.gameFolder().resolve("screenshots"));
        }
        List<Path> files = new ArrayList<>();
        collectImages(this.paths.showcase(), files, 24);
        List<Showcase.Slide> slides = new ArrayList<>();
        for (Path file : files) {
            slides.add(new Showcase.Slide("file:" + file.toAbsolutePath(), "Featured", ""));
        }
        List<Path> shots = new ArrayList<>();
        for (Path folder : screenshotFolders) {
            collectImages(folder, shots, 64);
        }
        shots.sort(Comparator.comparingLong(LauncherUi::modified).reversed());
        for (Path shot : shots.subList(0, Math.min(10, shots.size()))) {
            slides.add(new Showcase.Slide("file:" + shot.toAbsolutePath(), "Your screenshots", ""));
        }
        List<Showcase.Slide> result = List.copyOf(slides);
        this.tasks.onUi(() -> {
            this.localSlides = result;
            this.pushSlides();
        });
    }

    private void pushSlides() {
        List<Showcase.Slide> all = new ArrayList<>(this.feedSlides);
        all.addAll(this.localSlides);
        this.showcase.setSlides(all);
    }

    private static void collectImages(final Path folder, final List<Path> into, final int limit) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                })
                .sorted(Comparator.comparingLong(LauncherUi::modified).reversed())
                .limit(limit)
                .forEach(into::add);
        } catch (IOException e) {
            System.err.println("Could not list " + folder + ": " + e.getMessage());
        }
    }

    private static long modified(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private void updatePresence() {
        GameProcess playing = null;
        Instance playingInstance = null;
        for (Map.Entry<String, GameProcess> entry : this.running.entrySet()) {
            Optional<Instance> instance = this.instances.find(entry.getKey());
            if (instance.isPresent()) {
                playing = entry.getValue();
                playingInstance = instance.get();
            }
        }
        if (playing != null) {
            this.discord.setActivity(new DiscordPresence.Activity("Playing ABNW", playingInstance.release.displayName(),
                playing.startedMillis() / 1000L, "Get the ABNW Launcher", BuildInfo.launcherReleasesPage()));
        } else {
            this.discord.setActivity(new DiscordPresence.Activity("In the launcher", "Getting ready to explore", this.openedAt,
                "Get the ABNW Launcher", BuildInfo.launcherReleasesPage()));
        }
    }
}
