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
            float sidebarWidth = px(252);
            this.drawSidebar(sidebarWidth);
            ImGui.sameLine(0, 0);
            this.drawContent();
            this.dialogs.draw();
        }
        ImGui.end();

        float tasksHeight = this.drawTasks();
        this.announcements.drawToasts(tasksHeight);
    }

    private void drawSidebar(final float width) {
        float[] bg = Theme.rgba(Theme.SIDEBAR, 1f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg[0], bg[1], bg[2], 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(18), px(20));
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(8), px(6));
        boolean open = ImGui.beginChild("sidebar", width, 0, ImGuiChildFlags.AlwaysUseWindowPadding, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImGui.popStyleVar(3);
        ImGui.popStyleColor();
        if (open) {
            ImDrawList dl = ImGui.getWindowDrawList();
            float wx = ImGui.getWindowPosX();
            float wy = ImGui.getWindowPosY();
            float wh = ImGui.getWindowHeight();
            dl.addLine(wx + width - 1, wy, wx + width - 1, wy + wh, u32(Theme.BORDER_SOFT), 1f);

            float logoWidth = ImGui.getContentRegionAvailX();
            ImageCache.Texture logo = this.images.get(Showcase.FALLBACK, 0, true);
            float logoHeight = logo != null ? logoWidth / logo.aspect() : logoWidth * 0.73f;
            float lx = ImGui.getCursorScreenPosX();
            float ly = ImGui.getCursorScreenPosY();
            boolean logoClicked = ImGui.invisibleButton("logo", logoWidth, logoHeight);
            boolean logoHovered = ImGui.isItemHovered();
            float glow = Motion.hover("logo#hover", logoHovered);
            if (logoHovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
            if (glow > 0.01f) {
                for (int i = 3; i >= 1; i--) {
                    float grow = px(3) * i * glow;
                    dl.addRectFilled(lx - grow, ly - grow, lx + logoWidth + grow, ly + logoHeight + grow, u32(Theme.EMBER, 0.06f * glow / i), px(14) + grow);
                }
            }
            if (logo != null) {
                dl.addImageRounded(logo.id(), lx, ly, lx + logoWidth, ly + logoHeight, 0, 0, 1, 1, u32(0xFFFFFF), px(14), imgui.flag.ImDrawFlags.RoundCornersAll);
            } else {
                dl.addRectFilled(lx, ly, lx + logoWidth, ly + logoHeight, u32(Theme.EMBER_DEEP), px(14));
            }
            dl.addRect(lx, ly, lx + logoWidth, ly + logoHeight, u32(0xFFFFFF, 0.08f), px(14), 0, px(1));
            if (logoClicked) {
                this.navigate(Page.HOME);
            }
            ImGui.dummy(0, px(18));

            this.navItem(Page.HOME, Icons.Icon.HOME, "Home", null);
            String count = this.instances.all().isEmpty() ? null : String.valueOf(this.instances.all().size());
            this.navItem(Page.INSTANCES, Icons.Icon.INSTANCES, "Instances", count);
            this.navItem(Page.NEWS, Icons.Icon.NEWS, "News", null);
            this.navItem(Page.COSMETICS, Icons.Icon.CROWN, "Cosmetics", null);
            this.navItem(Page.SETTINGS, Icons.Icon.SETTINGS, "Settings", null);

            UpdateChecker.LauncherUpdate update = this.updateStatus.launcherUpdate();
            float accountHeight = px(58);
            float updateHeight = update != null ? px(62) + px(10) : 0;
            float footer = accountHeight + updateHeight + px(22);
            float targetY = ImGui.getWindowHeight() - px(20) - footer;
            if (ImGui.getCursorPosY() < targetY) {
                ImGui.setCursorPosY(targetY);
            }
            if (update != null) {
                this.drawUpdateCard(update);
                ImGui.dummy(0, px(10));
            }
            this.drawAccount();
            ImGui.dummy(0, px(6));
            String version = BuildInfo.isDevBuild() ? "Development build" : "Launcher v" + BuildInfo.VERSION;
            float vw = Widgets.textWidth(Fonts.tiny, version);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + (ImGui.getContentRegionAvailX() - vw) * 0.5f);
            Widgets.text(Fonts.tiny, Theme.FAINT, version);
        }
        ImGui.endChild();
    }

    private void navItem(final Page target, final Icons.Icon icon, final String label, final String badge) {
        String id = "nav-" + target.name();
        float w = ImGui.getContentRegionAvailX();
        float h = px(44);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        boolean active = this.page == target;
        float hv = Motion.hover(id + "#hover", hovered);
        float av = Motion.to(id + "#active", active ? 1f : 0f, 14f);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + w, y + h, u32(0xFFFFFF, 0.045f * hv * (1f - av)), px(12));
        if (av > 0.01f) {
            dl.addRectFilledMultiColor(x + px(6), y, x + w * 0.9f, y + h, u32(Theme.EMBER, 0.16f * av), u32(Theme.EMBER, 0.02f * av),
                u32(Theme.EMBER, 0.02f * av), u32(Theme.EMBER, 0.16f * av));
            dl.addRect(x, y, x + w, y + h, u32(Theme.EMBER, 0.22f * av), px(12), 0, px(1));
            float barH = h * 0.5f * av;
            dl.addRectFilled(x, y + (h - barH) * 0.5f, x + px(3), y + (h + barH) * 0.5f, u32(Theme.EMBER, av), px(2));
        }
        int iconColor = u32(Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.EMBER, av));
        int textColor = u32(Theme.mix(Theme.MUTED, Theme.TEXT, Math.max(hv, av)));
        float iconSize = px(19);
        Icons.draw(dl, icon, x + px(14), y + (h - iconSize) * 0.5f, iconSize, iconColor);
        Widgets.drawText(dl, Fonts.label, x + px(46), y + (h - Fonts.label.size()) * 0.5f - px(0.5f), textColor, label);
        if (badge != null) {
            float bw = Math.max(px(22), Widgets.textWidth(Fonts.tiny, badge) + px(12));
            float bh = px(20);
            float bx = x + w - px(12) - bw;
            float by = y + (h - bh) * 0.5f;
            dl.addRectFilled(bx, by, bx + bw, by + bh, u32(active ? Theme.EMBER : Theme.SURFACE_HOVER, active ? 0.9f : 1f), bh * 0.5f);
            float tw = Widgets.textWidth(Fonts.tiny, badge);
            Widgets.drawText(dl, Fonts.tiny, bx + (bw - tw) * 0.5f, by + (bh - Fonts.tiny.size()) * 0.5f - px(0.5f),
                u32(active ? Theme.ON_EMBER : Theme.MUTED), badge);
        }
        if (clicked) {
            this.navigate(target);
        }
    }

    private void drawUpdateCard(final UpdateChecker.LauncherUpdate update) {
        float w = ImGui.getContentRegionAvailX();
        float h = px(62);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("launcher-update", w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover("launcher-update#hover", hovered);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilledMultiColor(x, y, x + w, y + h, u32(Theme.EMBER, 0.22f + 0.08f * hv), u32(Theme.SUN, 0.10f + 0.06f * hv),
            u32(Theme.SUN, 0.10f + 0.06f * hv), u32(Theme.EMBER, 0.22f + 0.08f * hv));
        dl.addRect(x, y, x + w, y + h, u32(Theme.EMBER, 0.55f + 0.3f * hv), px(12), 0, px(1));
        float iconSize = px(20);
        Icons.draw(dl, Icons.Icon.DOWNLOAD, x + px(14), y + (h - iconSize) * 0.5f, iconSize, u32(Theme.SUN));
        Widgets.drawText(dl, Fonts.label, x + px(44), y + px(12), u32(Theme.TEXT), "Update ready");
        Widgets.drawText(dl, Fonts.small, x + px(44), y + px(12) + Fonts.label.size() + px(2), u32(Theme.MUTED), "Launcher v" + update.version());
        if (clicked) {
            Desktop.browse(update.url());
        }
    }

    private void drawAccount() {
        Optional<Account> account = this.currentAccount();
        float w = ImGui.getContentRegionAvailX();
        float h = px(58);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("account", w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover("account#hover", hovered || ImGui.isPopupOpen("account-menu"));
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + w, y + h, u32(Theme.mix(Theme.SURFACE, Theme.SURFACE_HI, hv)), px(14));
        dl.addRect(x, y, x + w, y + h, u32(Theme.BORDER_SOFT), px(14), 0, px(1));
        float avatar = px(36);
        float ax = x + px(11);
        float ay = y + (h - avatar) * 0.5f;
        String name = account.map(a -> a.name).orElse("Sign in");
        if (account.isPresent()) {
            dl.addRectFilledMultiColor(ax, ay, ax + avatar, ay + avatar, u32(Theme.EMBER), u32(Theme.SUN), u32(Theme.EMBER_LO), u32(Theme.MAROON));
            dl.addRect(ax - px(0.5f), ay - px(0.5f), ax + avatar + px(0.5f), ay + avatar + px(0.5f), u32(Theme.SURFACE, 1f), px(10), 0, px(3));
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            float iw = Widgets.textWidth(Fonts.heading, initial);
            Widgets.drawText(dl, Fonts.heading, ax + (avatar - iw) * 0.5f, ay + (avatar - Fonts.heading.size()) * 0.5f - px(1), u32(Theme.ON_EMBER), initial);
        } else {
            dl.addRectFilled(ax, ay, ax + avatar, ay + avatar, u32(Theme.SURFACE_HOVER), px(10));
            Icons.draw(dl, Icons.Icon.USER, ax + px(8), ay + px(8), avatar - px(16), u32(Theme.MUTED));
        }
        float tx = ax + avatar + px(11);
        float textWidth = w - (tx - x) - px(30);
        Widgets.drawText(dl, Fonts.label, tx, y + px(11), u32(Theme.TEXT), Widgets.ellipsize(Fonts.label, name, textWidth));
        Widgets.drawText(dl, Fonts.small, tx, y + px(11) + Fonts.label.size() + px(1), u32(Theme.FAINT),
            account.isPresent() ? "Microsoft account" : "Microsoft account needed");
        Icons.draw(dl, Icons.Icon.CHEVRON_DOWN, x + w - px(26), y + (h - px(14)) * 0.5f, px(14), u32(Theme.MUTED));
        if (clicked) {
            if (account.isEmpty() && this.accounts.all().isEmpty()) {
                this.startSignIn();
            } else {
                ImGui.openPopup("account-menu");
            }
        }
        ImGui.setNextWindowPos(x, y - px(8), 0, 0f, 1f);
        ImGui.setNextWindowSize(w, 0);
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
            dl.addRectFilled(x, y, x + w, y + h, u32(0xFFFFFF, 0.06f), px(9));
        }
        int color = u32(highlighted ? Theme.EMBER : hovered ? Theme.TEXT : Theme.MUTED);
        Icons.draw(dl, icon, x + px(10), y + (h - px(16)) * 0.5f, px(16), color);
        Widgets.drawText(dl, Fonts.body, x + px(36), y + (h - Fonts.body.size()) * 0.5f - px(0.5f), u32(highlighted || hovered ? Theme.TEXT : Theme.MUTED),
            Widgets.ellipsize(Fonts.body, label, w - px(46)));
        return clicked;
    }

    private void drawContent() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(36), px(30));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(12), px(10));
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, px(8));
        boolean open = ImGui.beginChild("content", 0, 0, ImGuiChildFlags.AlwaysUseWindowPadding, 0);
        ImGui.popStyleVar(3);
        if (open) {
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
        float width = Math.min(px(520), viewport.getWorkSizeX() - px(252) - px(72));
        float centerX = viewport.getWorkPosX() + px(252) + (viewport.getWorkSizeX() - px(252)) * 0.5f;
        float bottom = viewport.getWorkPosY() + viewport.getWorkSizeY() - px(24);
        float[] bg = Theme.rgba(Theme.SURFACE_HI, 1f);
        float[] border = Theme.rgba(Theme.BORDER, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(18), px(14));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, px(18));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, px(1));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(10), px(6));
        ImGui.pushStyleColor(ImGuiCol.WindowBg, bg[0], bg[1], bg[2], 0.97f);
        ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], 1f);
        ImGui.setNextWindowPos(centerX, bottom, 0, 0.5f, 1f);
        ImGui.setNextWindowSize(width, 0);
        float height = 0;
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.AlwaysAutoResize;
        if (ImGui.begin("##tasks", flags)) {
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
            entry.level = "info";
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
                entry.level = "release";
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
