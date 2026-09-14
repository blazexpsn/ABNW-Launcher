package org.teamzetaverse.launcher.ui;

import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.LauncherConfig;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.auth.Account;
import org.teamzetaverse.launcher.auth.AccountStore;
import org.teamzetaverse.launcher.auth.MicrosoftAuth;
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

public final class LauncherUi {
    private static final String[] RENDERERS = {"auto", "vulkan", "opengl"};
    private static final String[] RENDERER_LABELS = {"Automatic (Vulkan, falling back to OpenGL)", "Vulkan", "OpenGL"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    private final LauncherPaths paths;
    private final LauncherConfig config;
    private final AccountStore accounts;
    private final InstanceStore instances;
    private final ReleaseService releases = new ReleaseService();
    private final GameInstaller installer;
    private final GameLauncher gameLauncher;
    private final Tasks tasks = new Tasks();
    private final Map<String, GameProcess> running = new ConcurrentHashMap<>();

    private String selectedInstanceId;
    private final List<String> errors = new ArrayList<>();
    private String pendingPopup;

    private ReleaseService.Listing listing;
    private String listingError;
    private boolean listingLoading;
    private int selectedRelease = -1;

    private final ImString newName = new ImString(64);
    private final ImInt newRenderer = new ImInt(0);
    private final int[] newMemory = {4096};

    private String editorsFor;
    private final ImInt editRenderer = new ImInt(0);
    private final int[] editMemory = {4096};
    private final ImString editJvmArgs = new ImString(512);
    private final ImString renameText = new ImString(64);

    private MicrosoftAuth.DeviceCode deviceCode;
    private Progress signInProgress;

    private final ImString settingsClientId = new ImString(128);
    private final ImString settingsJava = new ImString(512);
    private final int[] settingsMemory = {4096};
    private final ImInt settingsRenderer = new ImInt(0);
    private final float[] settingsScale = {1f};

    private int logVersionShown = -1;
    private List<String> logLines = List.of();
    private final ImBoolean logFollow = new ImBoolean(true);

    public LauncherUi(final LauncherPaths paths, final LauncherConfig config) {
        this.paths = paths;
        this.config = config;
        this.accounts = new AccountStore(paths);
        this.instances = new InstanceStore(paths);
        this.installer = new GameInstaller(paths, config);
        this.gameLauncher = new GameLauncher(paths, config);
        this.selectedInstanceId = config.selectedInstance;
    }

    public float uiScale() {
        return Math.max(0.75f, Math.min(2f, this.config.uiScale));
    }

    public boolean isAnimating() {
        return this.tasks.isBusy() || !this.running.isEmpty() || this.listingLoading;
    }

    public void beforeFrame() {
        this.tasks.drainUiQueue();
    }

    public void shutdown() {
        this.config.save();
        this.tasks.shutdown();
    }

    public void draw() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (ImGui.begin("ABNW Launcher", flags)) {
            this.drawHeader();
            ImGui.separator();

            float taskBarHeight = this.tasks.running().isEmpty() ? 0 : (ImGui.getFrameHeight() + 10) * this.tasks.running().size() + 16;
            float bodyHeight = ImGui.getContentRegionAvailY() - taskBarHeight;
            float listWidth = Math.max(240, ImGui.getContentRegionAvailX() * 0.26f);

            if (ImGui.beginChild("instances", listWidth, bodyHeight, true)) {
                this.drawInstanceList();
            }
            ImGui.endChild();
            ImGui.sameLine();
            if (ImGui.beginChild("details", 0, bodyHeight, true)) {
                this.drawDetails();
            }
            ImGui.endChild();

            this.drawTaskBar();
        }
        if (this.pendingPopup != null) {
            ImGui.openPopup(this.pendingPopup);
            this.pendingPopup = null;
        } else if (!this.errors.isEmpty() && !ImGui.isPopupOpen("Something went wrong")) {
            ImGui.openPopup("Something went wrong");
        }
        this.drawNewInstanceDialog();
        this.drawChangeReleaseDialog();
        this.drawSignInDialog();
        this.drawRenameDialog();
        this.drawDeleteDialog();
        this.drawSettingsDialog();
        this.drawErrorDialog();
        ImGui.end();
    }

    private void drawHeader() {
        ImGui.pushStyleColor(ImGuiCol.Text, Theme.SUN[0], Theme.SUN[1], Theme.SUN[2], 1f);
        ImGui.text("A BRAND NEW WORLD");
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.textDisabled("Launcher " + BuildInfo.VERSION);

        String accountLabel = this.currentAccount().map(a -> a.name).orElse("Not signed in");
        float comboWidth = 220;
        float buttonsWidth = ImGui.calcTextSize("Add account").x + ImGui.calcTextSize("Settings").x + 60;
        ImGui.sameLine(ImGui.getWindowWidth() - comboWidth - buttonsWidth - 30);
        ImGui.setNextItemWidth(comboWidth);
        if (ImGui.beginCombo("##account", accountLabel)) {
            for (Account account : this.accounts.all()) {
                boolean selected = account.uuid.equals(this.config.selectedAccount);
                if (ImGui.selectable(account.name + "##" + account.uuid, selected)) {
                    this.config.selectedAccount = account.uuid;
                    this.config.save();
                }
            }
            if (!this.accounts.all().isEmpty()) {
                ImGui.separator();
                Optional<Account> current = this.currentAccount();
                if (current.isPresent() && ImGui.selectable("Remove " + current.get().name)) {
                    this.accounts.remove(current.get());
                    this.config.selectedAccount = this.accounts.all().isEmpty() ? "" : this.accounts.all().get(0).uuid;
                    this.config.save();
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (ImGui.button("Add account")) {
            this.startSignIn();
        }
        ImGui.sameLine();
        if (ImGui.button("Settings")) {
            this.settingsClientId.set(this.config.msaClientId);
            this.settingsJava.set(this.config.javaPath);
            this.settingsMemory[0] = this.config.defaultMemoryMb;
            this.settingsRenderer.set(indexOf(RENDERERS, this.config.defaultRenderer));
            this.settingsScale[0] = this.uiScale();
            this.pendingPopup = "Settings";
        }
    }

    private void drawInstanceList() {
        ImGui.textDisabled("INSTANCES");
        if (Theme.accentButton("New instance", -1, 0)) {
            this.openNewInstance();
        }
        if (ImGui.button("Import .abnw", -1, 0)) {
            this.importArchive();
        }
        ImGui.separator();
        if (this.instances.all().isEmpty()) {
            ImGui.textWrapped("No instances yet. Create one to install ABNW.");
            return;
        }
        for (Instance instance : this.instances.all()) {
            boolean selected = instance.id.equals(this.selectedInstanceId);
            String badge = this.running.containsKey(instance.id) ? "  (playing)" : "";
            if (ImGui.selectable(instance.name + badge + "##" + instance.id, selected, 0, 0, ImGui.getFrameHeight() * 1.4f)) {
                this.select(instance);
            }
            ImGui.sameLine();
            ImGui.textDisabled(instance.release.displayName());
        }
    }

    private void select(final Instance instance) {
        this.selectedInstanceId = instance.id;
        this.config.selectedInstance = instance.id;
        this.config.save();
    }

    private Optional<Instance> selectedInstance() {
        return this.selectedInstanceId == null ? Optional.empty() : this.instances.find(this.selectedInstanceId);
    }

    private void drawDetails() {
        Optional<Instance> maybe = this.selectedInstance();
        if (maybe.isEmpty()) {
            ImGui.dummy(0, 40);
            ImGui.textWrapped("A Brand New World installs Minecraft from Mojang and turns it into ABNW with a small patch. "
                + "Select an instance, or create one to get started.");
            if (this.accounts.all().isEmpty()) {
                ImGui.spacing();
                Theme.textColored(Theme.MUTED, "You'll need to sign in with the Microsoft account that owns Minecraft: Java Edition "
                    + "(or has PC Game Pass). Use \"Add account\" at the top.");
            }
            return;
        }
        Instance instance = maybe.get();
        this.bindEditors(instance);
        GameProcess process = this.running.get(instance.id);
        boolean busy = process != null || this.isInstanceBusy(instance);

        ImGui.text(instance.name);
        ImGui.textDisabled(instance.release.displayName() + "  -  for Minecraft " + instance.release.minecraft);
        ImGui.spacing();

        if (process != null) {
            if (ImGui.button("Stop game", 180, 44)) {
                process.kill();
            }
            ImGui.sameLine();
            Theme.textColored(Theme.OK, "Playing for " + formatDuration(System.currentTimeMillis() - process.startedMillis()));
        } else {
            ImGui.beginDisabled(busy);
            if (Theme.accentButton("PLAY", 180, 44)) {
                this.play(instance);
            }
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        ImGui.beginDisabled(busy);
        if (ImGui.button("Change release", 0, 44)) {
            this.openChangeRelease(instance);
        }
        ImGui.endDisabled();

        if (ImGui.beginTabBar("instanceTabs")) {
            if (ImGui.beginTabItem("Overview")) {
                this.drawOverview(instance, busy);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Game log")) {
                this.drawLog(instance);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    private void bindEditors(final Instance instance) {
        if (instance.id.equals(this.editorsFor)) {
            return;
        }
        this.editorsFor = instance.id;
        this.editRenderer.set(indexOf(RENDERERS, instance.renderer));
        this.editMemory[0] = instance.memoryMb > 0 ? instance.memoryMb : this.config.defaultMemoryMb;
        this.editJvmArgs.set(instance.extraJvmArgs == null ? "" : instance.extraJvmArgs);
        this.logVersionShown = -1;
    }

    private void drawOverview(final Instance instance, final boolean busy) {
        ImGui.spacing();
        if (ImGui.beginTable("facts", 2, ImGuiTableFlags.SizingStretchProp)) {
            row("Release", instance.release.displayName());
            row("Minecraft", instance.release.minecraft);
            row("Created", instance.created > 0 ? DATE.format(Instant.ofEpochMilli(instance.created)) : "-");
            row("Last played", instance.lastPlayed > 0 ? DATE.format(Instant.ofEpochMilli(instance.lastPlayed)) : "Never");
            row("Time played", formatDuration(instance.totalPlayMillis));
            ImGui.endTable();
        }

        ImGui.separatorText("Game settings");
        ImGui.setNextItemWidth(360);
        if (ImGui.combo("Renderer", this.editRenderer, RENDERER_LABELS)) {
            instance.renderer = RENDERERS[this.editRenderer.get()];
            this.saveQuietly(instance);
        }
        ImGui.setNextItemWidth(360);
        if (ImGui.sliderInt("Memory (MB)", this.editMemory, 2048, maxMemoryMb(), "%d MB")) {
            instance.memoryMb = roundTo(this.editMemory[0], 256);
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            this.saveQuietly(instance);
        }
        ImGui.setNextItemWidth(360);
        ImGui.inputTextWithHint("Extra Java arguments", "e.g. -XX:+UseZGC", this.editJvmArgs);
        if (ImGui.isItemDeactivatedAfterEdit()) {
            instance.extraJvmArgs = this.editJvmArgs.get().trim();
            this.saveQuietly(instance);
        }

        ImGui.separatorText("Folders");
        if (ImGui.button("Open game folder")) {
            Desktop.open(instance.gameFolder());
        }
        ImGui.sameLine();
        if (ImGui.button("Open mods folder")) {
            Desktop.open(instance.modsFolder());
        }
        ImGui.sameLine();
        if (ImGui.button("Open worlds")) {
            Desktop.open(instance.gameFolder().resolve("saves"));
        }

        ImGui.separatorText("Manage");
        ImGui.beginDisabled(busy);
        if (ImGui.button("Export .abnw")) {
            this.exportArchive(instance);
        }
        ImGui.sameLine();
        if (ImGui.button("Repair")) {
            this.prepare(instance, null);
        }
        ImGui.sameLine();
        if (ImGui.button("Rename")) {
            this.renameText.set(instance.name);
            this.pendingPopup = "Rename instance";
        }
        ImGui.sameLine();
        if (ImGui.button("Delete")) {
            this.pendingPopup = "Delete instance";
        }
        ImGui.endDisabled();
    }

    private static void row(final String label, final String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.text(value);
    }

    private void drawLog(final Instance instance) {
        GameProcess process = this.running.get(instance.id);
        if (process == null) {
            if (this.logLines.isEmpty() || !instance.id.equals(this.editorsFor)) {
                ImGui.textDisabled("The game's output appears here while it runs.");
                if (ImGui.button("Open launcher logs folder")) {
                    Desktop.open(this.paths.logs());
                }
                return;
            }
        } else if (process.version() != this.logVersionShown) {
            this.logVersionShown = process.version();
            this.logLines = process.lines();
        }
        ImGui.checkbox("Follow output", this.logFollow);
        ImGui.sameLine();
        if (ImGui.button("Copy")) {
            ImGui.setClipboardText(String.join("\n", this.logLines));
        }
        if (ImGui.beginChild("log", 0, 0, true, ImGuiWindowFlags.HorizontalScrollbar)) {
            for (String line : this.logLines) {
                if (line.contains("ERROR") || line.contains("Exception")) {
                    ImGui.pushStyleColor(ImGuiCol.Text, Theme.ERROR[0], Theme.ERROR[1], Theme.ERROR[2], 1f);
                    ImGui.textUnformatted(line);
                    ImGui.popStyleColor();
                } else if (line.contains("WARN")) {
                    ImGui.pushStyleColor(ImGuiCol.Text, Theme.SUN[0], Theme.SUN[1], Theme.SUN[2], 1f);
                    ImGui.textUnformatted(line);
                    ImGui.popStyleColor();
                } else {
                    ImGui.textUnformatted(line);
                }
            }
            if (this.logFollow.get() && ImGui.getScrollY() >= ImGui.getScrollMaxY() - 40) {
                ImGui.setScrollHereY(1f);
            }
        }
        ImGui.endChild();
    }

    private void drawTaskBar() {
        for (Progress progress : this.tasks.running()) {
            float fraction = progress.fraction();
            String overlay = progress.title() + (progress.status().isEmpty() ? "" : " - " + progress.status());
            float width = ImGui.getContentRegionAvailX() - 90;
            if (fraction < 0) {
                float t = (float)((System.nanoTime() / 1_000_000_000.0) % 1.0);
                ImGui.progressBar(t, width, 0, overlay);
            } else {
                ImGui.progressBar(fraction, width, 0, overlay);
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel##" + System.identityHashCode(progress), 80, 0)) {
                progress.cancel();
            }
        }
    }

    private boolean isInstanceBusy(final Instance instance) {
        return this.tasks.running().stream().anyMatch(p -> p.title().contains(instance.name));
    }

    private Optional<Account> currentAccount() {
        Optional<Account> selected = this.accounts.find(this.config.selectedAccount);
        if (selected.isPresent() || this.accounts.all().isEmpty()) {
            return selected;
        }
        return Optional.of(this.accounts.all().get(0));
    }

    private MicrosoftAuth auth() {
        return new MicrosoftAuth(this.config.effectiveClientId());
    }

    private void fail(final Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        this.errors.add(message);
    }

    private void play(final Instance instance) {
        Optional<Account> maybeAccount = this.currentAccount();
        if (maybeAccount.isEmpty()) {
            this.errors.add("Sign in with the Microsoft account that owns Minecraft: Java Edition before playing. Use \"Add account\" at the top.");
            return;
        }
        Account account = maybeAccount.get();
        this.tasks.submit("Launching " + instance.name, progress -> {
            this.auth().refresh(account, progress);
            this.accounts.save();
            var installed = this.installer.install(instance.release, this.instances.readLibraries(instance), progress);
            progress.checkCancelled();
            progress.stage("Starting the game...", 0);
            return this.gameLauncher.launch(instance, installed, account, exited -> this.tasks.onUi(() -> this.gameExited(instance, exited)));
        }, process -> {
            this.running.put(instance.id, process);
            instance.lastPlayed = System.currentTimeMillis();
            this.saveQuietly(instance);
            this.logVersionShown = -1;
        }, this::fail);
    }

    private void gameExited(final Instance instance, final GameProcess process) {
        this.running.remove(instance.id, process);
        instance.totalPlayMillis += System.currentTimeMillis() - process.startedMillis();
        this.saveQuietly(instance);
        this.logLines = process.lines();
    }

    private void prepare(final Instance instance, final Runnable after) {
        this.tasks.submit("Preparing " + instance.name, progress -> {
            this.installer.install(instance.release, this.instances.readLibraries(instance), progress);
            return Boolean.TRUE;
        }, ok -> {
            if (after != null) {
                after.run();
            }
        }, this::fail);
    }

    private void saveQuietly(final Instance instance) {
        try {
            this.instances.save(instance);
        } catch (java.io.IOException e) {
            this.fail(e);
        }
    }

    private void loadListing() {
        if (this.listingLoading) {
            return;
        }
        this.listingLoading = true;
        this.listingError = null;
        this.tasks.submit("Loading ABNW releases", progress -> this.releases.fetchListing(), result -> {
            this.listingLoading = false;
            this.listing = result;
            this.selectedRelease = -1;
            for (int i = 0; i < result.releases().size(); i++) {
                if (result.releases().get(i).id.equals(result.latest())) {
                    this.selectedRelease = i;
                }
            }
            if (this.selectedRelease < 0 && !result.releases().isEmpty()) {
                this.selectedRelease = 0;
            }
        }, error -> {
            this.listingLoading = false;
            this.listingError = "Could not load the ABNW releases: " + error.getMessage();
        });
    }

    private Release drawReleasePicker(final String currentId) {
        if (this.listingLoading) {
            ImGui.text("Loading releases...");
            return null;
        }
        if (this.listingError != null) {
            Theme.textColored(Theme.ERROR, this.listingError);
            if (ImGui.button("Try again")) {
                this.loadListing();
            }
            return null;
        }
        if (this.listing == null) {
            return null;
        }
        if (this.listing.releases().isEmpty()) {
            ImGui.textWrapped("No ABNW releases are published yet.");
            return null;
        }
        int tableFlags = ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersInnerH | ImGuiTableFlags.ScrollY | ImGuiTableFlags.SizingStretchProp;
        if (ImGui.beginTable("releases", 3, tableFlags, 0, 220)) {
            ImGui.tableSetupColumn("Release");
            ImGui.tableSetupColumn("Minecraft");
            ImGui.tableSetupColumn("Released");
            ImGui.tableHeadersRow();
            for (int i = 0; i < this.listing.releases().size(); i++) {
                Release release = this.listing.releases().get(i);
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                String label = release.displayName() + (release.id.equals(this.listing.latest()) ? "  (latest)" : "")
                    + (release.id.equals(currentId) ? "  (current)" : "");
                if (ImGui.selectable(label + "##release" + i, i == this.selectedRelease, imgui.flag.ImGuiSelectableFlags.SpanAllColumns)) {
                    this.selectedRelease = i;
                }
                ImGui.tableNextColumn();
                ImGui.text(release.minecraft);
                ImGui.tableNextColumn();
                ImGui.text(release.releaseTime == null || release.releaseTime.length() < 10 ? "" : release.releaseTime.substring(0, 10));
            }
            ImGui.endTable();
        }
        return this.selectedRelease >= 0 && this.selectedRelease < this.listing.releases().size()
            ? this.listing.releases().get(this.selectedRelease)
            : null;
    }

    private void openNewInstance() {
        this.newName.set("");
        this.newRenderer.set(indexOf(RENDERERS, this.config.defaultRenderer));
        this.newMemory[0] = this.config.defaultMemoryMb;
        this.loadListing();
        this.pendingPopup = "New instance";
    }

    private void drawNewInstanceDialog() {
        centerNextModal(640);
        if (!ImGui.beginPopupModal("New instance", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        ImGui.textWrapped("Pick an ABNW release. The launcher downloads Minecraft from Mojang and applies the release's patch.");
        ImGui.spacing();
        Release release = this.drawReleasePicker(null);
        ImGui.spacing();
        ImGui.setNextItemWidth(360);
        ImGui.inputTextWithHint("Name", release == null ? "My world" : release.displayName(), this.newName);
        ImGui.setNextItemWidth(360);
        ImGui.combo("Renderer", this.newRenderer, RENDERER_LABELS);
        ImGui.setNextItemWidth(360);
        ImGui.sliderInt("Memory", this.newMemory, 2048, maxMemoryMb(), "%d MB");
        ImGui.spacing();

        ImGui.beginDisabled(release == null);
        if (Theme.accentButton("Create and install", 200, 0) && release != null) {
            String name = this.newName.get().trim().isEmpty() ? release.displayName() : this.newName.get().trim();
            int memory = roundTo(this.newMemory[0], 256);
            String renderer = RENDERERS[this.newRenderer.get()];
            this.tasks.submit("Creating " + name, progress -> {
                progress.status("Fetching " + release.displayName() + "...");
                ReleaseService.Resolved resolved = this.releases.resolve(release);
                return this.instances.create(name, resolved, memory, renderer);
            }, created -> {
                this.select(created);
                this.prepare(created, null);
            }, this::fail);
            ImGui.closeCurrentPopup();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void openChangeRelease(final Instance instance) {
        this.loadListing();
        this.pendingPopup = "Change release";
    }

    private void drawChangeReleaseDialog() {
        centerNextModal(640);
        if (!ImGui.beginPopupModal("Change release", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        Optional<Instance> maybe = this.selectedInstance();
        if (maybe.isEmpty()) {
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        Instance instance = maybe.get();
        ImGui.textWrapped("Choose the ABNW release " + instance.name + " plays. Worlds, settings and mods stay; "
            + "the new release is downloaded and patched the next time you play.");
        ImGui.spacing();
        Release release = this.drawReleasePicker(instance.release.id);
        ImGui.spacing();
        boolean same = release != null && release.id.equals(instance.release.id) && release.minecraft.equals(instance.release.minecraft);
        ImGui.beginDisabled(release == null || same);
        if (Theme.accentButton("Switch release", 200, 0) && release != null) {
            this.tasks.submit("Switching " + instance.name, progress -> {
                progress.status("Fetching " + release.displayName() + "...");
                ReleaseService.Resolved resolved = this.releases.resolve(release);
                this.instances.setRelease(instance, resolved);
                return Boolean.TRUE;
            }, ok -> this.prepare(instance, null), this::fail);
            ImGui.closeCurrentPopup();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void startSignIn() {
        MicrosoftAuth auth = this.auth();
        this.deviceCode = null;
        this.tasks.submit("Starting Microsoft sign-in", progress -> auth.requestDeviceCode(), code -> {
            this.deviceCode = code;
            this.pendingPopup = "Sign in with Microsoft";
            this.signInProgress = this.tasks.submit("Signing in", progress -> auth.completeDeviceCode(code, progress), account -> {
                this.accounts.put(account);
                this.config.selectedAccount = account.uuid;
                this.config.save();
                this.deviceCode = null;
                this.signInProgress = null;
            }, error -> {
                this.deviceCode = null;
                this.signInProgress = null;
                this.fail(error);
            });
        }, this::fail);
    }

    private void drawSignInDialog() {
        centerNextModal(520);
        if (!ImGui.beginPopupModal("Sign in with Microsoft", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        MicrosoftAuth.DeviceCode code = this.deviceCode;
        if (code == null) {
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        ImGui.textWrapped("To sign in, open this page in your browser and enter the code below. "
            + "Use the Microsoft account that owns Minecraft: Java Edition (or has PC Game Pass).");
        ImGui.spacing();
        ImGui.text(code.verificationUri());
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, Theme.SUN[0], Theme.SUN[1], Theme.SUN[2], 1f);
        ImGui.text("Code:  " + code.userCode());
        ImGui.popStyleColor();
        ImGui.spacing();
        if (Theme.accentButton("Copy code and open page", 240, 0)) {
            ImGui.setClipboardText(code.userCode());
            Desktop.browse(code.verificationUri());
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            if (this.signInProgress != null) {
                this.signInProgress.cancel();
            }
            this.deviceCode = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.spacing();
        ImGui.textDisabled(this.signInProgress == null || this.signInProgress.status().isEmpty()
            ? "Waiting for you to finish signing in..."
            : this.signInProgress.status());
        ImGui.endPopup();
    }

    private void importArchive() {
        Path archive = Desktop.chooseArchiveToOpen();
        if (archive == null) {
            return;
        }
        this.tasks.submit("Importing " + archive.getFileName(), progress -> {
            Instance described = InstanceArchive.peek(archive);
            progress.status("Fetching " + described.release.displayName() + "...");
            ReleaseService.Resolved resolved = this.releases.resolve(described.release);
            return InstanceArchive.importArchive(archive, this.instances, resolved, progress);
        }, imported -> {
            this.select(imported);
            this.prepare(imported, null);
        }, this::fail);
    }

    private void exportArchive(final Instance instance) {
        Path target = Desktop.chooseArchiveToSave(instance.name.replaceAll("[^A-Za-z0-9 ._-]", ""));
        if (target == null) {
            return;
        }
        this.tasks.submit("Exporting " + instance.name, progress -> {
            InstanceArchive.export(instance, target, progress);
            return target;
        }, done -> {
        }, this::fail);
    }

    private void drawRenameDialog() {
        centerNextModal(420);
        if (!ImGui.beginPopupModal("Rename instance", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        if (ImGui.isWindowAppearing()) {
            ImGui.setKeyboardFocusHere();
        }
        ImGui.setNextItemWidth(360);
        boolean submitted = ImGui.inputText("##name", this.renameText, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        if (Theme.accentButton("Rename", 120, 0) || submitted) {
            this.selectedInstance().ifPresent(instance -> {
                try {
                    this.instances.rename(instance, this.renameText.get());
                } catch (java.io.IOException e) {
                    this.fail(e);
                }
            });
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void drawDeleteDialog() {
        centerNextModal(460);
        if (!ImGui.beginPopupModal("Delete instance", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        Optional<Instance> maybe = this.selectedInstance();
        if (maybe.isEmpty()) {
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        Instance instance = maybe.get();
        ImGui.textWrapped("Delete " + instance.name + "? Its worlds, settings and mods are deleted with it. "
            + "Export it first if you want to keep anything.");
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.15f, 0.15f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.75f, 0.2f, 0.2f, 1f);
        if (ImGui.button("Delete", 120, 0)) {
            try {
                this.instances.delete(instance);
                this.selectedInstanceId = null;
            } catch (java.io.IOException e) {
                this.fail(e);
            }
            ImGui.closeCurrentPopup();
        }
        ImGui.popStyleColor(2);
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void drawSettingsDialog() {
        centerNextModal(620);
        if (!ImGui.beginPopupModal("Settings", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        ImGui.separatorText("New instances");
        ImGui.setNextItemWidth(360);
        ImGui.combo("Default renderer", this.settingsRenderer, RENDERER_LABELS);
        ImGui.setNextItemWidth(360);
        ImGui.sliderInt("Default memory", this.settingsMemory, 2048, maxMemoryMb(), "%d MB");

        ImGui.separatorText("Java");
        ImGui.setNextItemWidth(360);
        ImGui.inputTextWithHint("Java executable", "Empty: download the Java Minecraft asks for", this.settingsJava);
        ImGui.sameLine();
        if (ImGui.button("Browse")) {
            Path java = Desktop.chooseJava();
            if (java != null) {
                this.settingsJava.set(java.toString());
            }
        }

        ImGui.separatorText("Microsoft sign-in");
        ImGui.setNextItemWidth(360);
        ImGui.inputTextWithHint("Client ID", BuildInfo.MSA_CLIENT_ID.isEmpty() ? "Not set in this build" : "Built-in: " + BuildInfo.MSA_CLIENT_ID,
            this.settingsClientId);
        ImGui.textDisabled("Leave empty to use the client ID this launcher was built with.");

        ImGui.separatorText("Interface");
        ImGui.setNextItemWidth(360);
        ImGui.sliderFloat("Scale (after restart)", this.settingsScale, 0.75f, 2f, "%.2fx");

        ImGui.separatorText("About");
        ImGui.textDisabled("Releases from github.com/" + BuildInfo.REPOSITORY);
        ImGui.textDisabled("Data folder: " + this.paths.root());
        if (ImGui.button("Open data folder")) {
            Desktop.open(this.paths.root());
        }
        ImGui.textDisabled("NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.");

        ImGui.spacing();
        if (Theme.accentButton("Save", 120, 0)) {
            this.config.defaultRenderer = RENDERERS[this.settingsRenderer.get()];
            this.config.defaultMemoryMb = roundTo(this.settingsMemory[0], 256);
            this.config.javaPath = this.settingsJava.get().trim();
            this.config.msaClientId = this.settingsClientId.get().trim();
            this.config.uiScale = this.settingsScale[0];
            this.config.save();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void drawErrorDialog() {
        centerNextModal(520);
        if (!ImGui.beginPopupModal("Something went wrong", null, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings)) {
            return;
        }
        if (this.errors.isEmpty()) {
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        Theme.textColored(Theme.ERROR, this.errors.get(0));
        ImGui.spacing();
        if (ImGui.button("OK", 120, 0)) {
            this.errors.remove(0);
            if (this.errors.isEmpty()) {
                ImGui.closeCurrentPopup();
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Copy", 120, 0)) {
            ImGui.setClipboardText(this.errors.get(0));
        }
        ImGui.endPopup();
    }

    private static void centerNextModal(final float width) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + viewport.getWorkSizeX() / 2, viewport.getWorkPosY() + viewport.getWorkSizeY() / 2,
            ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(width, 0, ImGuiCond.Appearing);
    }

    private static int indexOf(final String[] values, final String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static int roundTo(final int value, final int step) {
        return Math.max(step, Math.round(value / (float)step) * step);
    }

    private static int maxMemoryMb() {
        long total = 16L * 1024;
        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            total = os.getTotalMemorySize() / (1024 * 1024);
        }
        return (int)Math.max(4096, Math.min(64L * 1024, total - 2048));
    }

    private static String formatDuration(final long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
    }
}
