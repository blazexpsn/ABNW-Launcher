package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.launch.GameLauncher;
import org.teamzetaverse.launcher.launch.GameProcess;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.server.ServerAdministration;
import org.teamzetaverse.launcher.util.Format;

final class ServersPage {
    private final LauncherUi ui;
    private final ServerAccessEditor access;
    private final Map<String, List<String>> lastLogs = new HashMap<>();
    private String selectedId;
    private String bound;
    private int tab;
    private final int[] memory = {2048};
    private final ImString jvm = new ImString(4096);
    private final ImString command = new ImString(4096);
    private final ImString propertyName = new ImString(256);
    private final ImString propertyValue = new ImString(4096);
    private final Map<String, ImString> fields = new java.util.LinkedHashMap<>();
    private Properties properties;
    private String settingsError;
    private boolean followLog = true;
    private int logVersion = -1;
    private List<String> logLines = List.of();
    private boolean wasRunning;
    private boolean stopping;

    ServersPage(final LauncherUi ui) { this.ui = ui; this.access = new ServerAccessEditor(ui); }
    void select(final Instance instance) { this.selectedId = instance.id; this.logVersion = -1; }
    void rememberLog(final Instance instance, final List<String> lines) {
        this.lastLogs.put(instance.id, lines); this.logVersion = -1;
        if (instance.id.equals(this.selectedId)) { this.bound = null; this.access.invalidate(); }
    }
    private Optional<Instance> selected(final List<Instance> servers) {
        return servers.stream().filter(server -> server.id.equals(this.selectedId)).findFirst().or(() -> servers.stream().findFirst());
    }
    void draw() {
        List<Instance> servers = this.ui.instances.servers();
        Widgets.text(Fonts.title, Theme.TEXT, "Servers"); ImGui.sameLine(); Widgets.alignRight(px(285));
        if (Widgets.secondary("srv-import", "Import", Icons.Icon.IMPORT)) this.ui.importArchive();
        ImGui.sameLine();
        if (Widgets.primary("srv-new", "New server", Icons.Icon.PLUS, 0, px(40), true)) this.ui.dialogs.openNewServer();
        Widgets.text(Fonts.body, Theme.MUTED, servers.size() == 1 ? "1 server" : servers.size() + " servers"); ImGui.dummy(0, px(10));
        if (servers.isEmpty()) { Widgets.mascotMessage(Icons.Icon.PENGUIN_EMPTY, "Create a server, choose its build, and manage it here.", Theme.MUTED); return; }
        float height = Math.max(px(380), ImGui.getContentRegionAvailY() - px(4));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0); ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(8), px(8));
        Optional<Instance> current = this.selected(servers);
        if (ImGui.beginChild("srv-list", px(280), height, ImGuiChildFlags.None, 0)) {
            for (Instance server : servers) {
                if (Widgets.beginCard("srv-card-" + server.id, 0, 0, current.isPresent() && current.get().id.equals(server.id) ? Theme.SURFACE_HI : Theme.SURFACE, px(16), px(14))) {
                    InstancesPage.drawTile(ImGui.getWindowDrawList(), server, ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(), px(40));
                    ImGui.dummy(px(40), px(40)); ImGui.sameLine(); ImGui.beginGroup();
                    Widgets.text(Fonts.button, Theme.TEXT, Widgets.ellipsize(Fonts.button, server.name, px(180)));
                    Widgets.text(Fonts.label, Theme.MUTED, server.release.displayName()); ImGui.endGroup();
                    Widgets.text(Fonts.label, this.ui.running.containsKey(server.id) ? Theme.OK : Theme.MUTED,
                        this.ui.running.containsKey(server.id) ? "Running" : server.publicServer && ServerAdministration.supportsPublic(server.release) ? "Public · stopped" : "Stopped");
                }
                Widgets.endCard(); if (ImGui.isItemClicked()) this.select(server);
            }
        }
        ImGui.endChild(); ImGui.popStyleVar(2); ImGui.sameLine(0, px(20));
        if (current.isPresent()) {
            if (Widgets.beginCard("srv-details", 0, height, Theme.SURFACE, px(24), px(22))) this.drawDetails(current.get());
            Widgets.endCard();
        }
    }
    private void bind(final Instance server) {
        String key = server.id + ":" + server.release.id; boolean running = this.ui.running.containsKey(server.id);
        if (key.equals(this.bound) && this.wasRunning == running) return;
        this.bound = key; this.wasRunning = running; this.stopping = false; this.access.invalidate(); this.logVersion = -1; this.command.set("");
        this.memory[0] = server.memoryMb; this.jvm.set(server.extraJvmArgs == null ? "" : server.extraJvmArgs); this.settingsError = null;
        try {
            this.properties = ServerAdministration.properties(server); this.fields.clear();
            for (String[] field : new String[][]{{"motd", "A Minecraft Server"}, {"server-port", "25565"}, {"max-players", "20"}, {"level-name", "world"},
                {"gamemode", "survival"}, {"difficulty", "easy"}, {"online-mode", "true"}, {"white-list", "false"}, {"view-distance", "10"}, {"simulation-distance", "10"}})
                this.fields.put(field[0], new ImString(this.properties.getProperty(field[0], field[1]), 4096));
        } catch (Exception e) { this.properties = null; this.settingsError = e.getMessage(); }
    }
    private void drawDetails(final Instance server) {
        this.bind(server); this.selectedId = server.id;
        GameProcess process = this.ui.running.get(server.id); boolean busy = this.ui.installState(server) == LauncherUi.InstallState.BUSY; boolean locked = busy || process != null;
        InstancesPage.drawTile(ImGui.getWindowDrawList(), server, ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(), px(60));
        ImGui.dummy(px(60), px(60)); ImGui.sameLine(0, px(14)); ImGui.beginGroup();
        Widgets.text(Fonts.heading, Theme.TEXT, server.name); Widgets.pill(server.release.displayName(), Theme.EMBER, Theme.EMBER, .14f, Icons.Icon.CUBE); ImGui.sameLine();
        Widgets.pill(process != null ? "Running" : busy ? "Working" : "Stopped", Theme.MUTED, Theme.MUTED, .14f, Icons.Icon.SERVERS);
        ImGui.endGroup(); ImGui.dummy(0, px(12));
        boolean accepted; try { accepted = GameLauncher.eulaAccepted(server); } catch (IOException e) { accepted = false; }
        if (!accepted) {
            Widgets.textWrapped(Fonts.label, Theme.MUTED, "Accept the Minecraft EULA to start this server.");
            if (Widgets.secondary("srv-eula-read", "Read EULA", Icons.Icon.EXTERNAL)) Desktop.browse("https://aka.ms/MinecraftEULA"); ImGui.sameLine();
            if (Widgets.primary("srv-eula-accept", "I accept", Icons.Icon.CHECK, 0, px(34), !locked)) {
                try { GameLauncher.acceptEula(server); } catch (IOException e) { this.ui.fail(e); }
            }
        }
        if (process == null) {
            if (Widgets.primary("srv-start", busy ? "Preparing" : "Start server", Icons.Icon.PLAY, 0, px(42), accepted && !busy)) this.ui.startServer(server);
        } else {
            if (Widgets.button("srv-stop", this.stopping ? "Stopping…" : "Stop server", Icons.Icon.STOP, Widgets.Variant.SECONDARY, 0, px(42), !this.stopping)) { this.ui.stop(server); this.stopping = true; }
            if (this.stopping) {
                ImGui.sameLine(); if (Widgets.secondary("srv-force", "Force stop", Icons.Icon.ALERT)) process.kill();
                Widgets.textWrapped(Fonts.label, Theme.SUN, "Force stop may lose unsaved changes. Allow the server time to save first.");
            }
        }
        Release latest = this.ui.latestRelease();
        if (latest != null && this.ui.isOutdated(server)) { ImGui.sameLine();
            if (Widgets.button("srv-update", "Update", Icons.Icon.UPDATE, Widgets.Variant.SECONDARY, 0, px(42), !locked)) this.ui.dialogs.openChangeRelease(server, latest);
        }
        ImGui.dummy(0, px(6));
        if (Widgets.iconButton("srv-folder", Icons.Icon.FOLDER, px(36), "Open folder", true)) Desktop.open(server.gameFolder()); ImGui.sameLine();
        if (Widgets.iconButton("srv-build", Icons.Icon.SWAP, px(36), "Change build", !locked)) this.ui.dialogs.openChangeRelease(server, null); ImGui.sameLine();
        if (Widgets.iconButton("srv-export", Icons.Icon.EXPORT, px(36), "Export as .abnw", !locked)) this.ui.exportArchive(server); ImGui.sameLine();
        if (Widgets.iconButton("srv-rename", Icons.Icon.EDIT, px(36), "Rename", !locked)) this.ui.dialogs.openRename(server); ImGui.sameLine();
        if (Widgets.iconButton("srv-delete", Icons.Icon.TRASH, px(36), "Delete", !locked)) this.ui.dialogs.openDelete(server);
        ImGui.dummy(0, px(8)); ImGui.pushID("server-tabs");
        this.tab = this.ui.instancesPage.tabs(new String[]{"Settings", "Operators", "Permissions", "Icon", "Console"}, this.tab);
        ImGui.popID(); ImGui.dummy(0, px(10));
        if (ImGui.beginChild("srv-tab", 0, 0, ImGuiChildFlags.None, 0)) {
            switch (this.tab) { case 0 -> this.drawSettings(server, locked); case 1 -> this.access.draw(server, locked, false);
                case 2 -> this.access.draw(server, locked, true); case 3 -> this.ui.instancesPage.drawIcon(server); default -> this.drawLog(server, process); }
        }
        ImGui.endChild();
    }
    private void drawSettings(final Instance server, final boolean locked) {
        Widgets.textWrapped(Fonts.label, Theme.MUTED, "Created " + Format.date(server.created) + " · uptime recorded " + Format.duration(server.totalPlayMillis));
        if (locked) Widgets.textWrapped(Fonts.label, Theme.MUTED, "Stop the server and wait for its tasks to finish to change settings.");
        boolean publicSupported = ServerAdministration.supportsPublic(server.release); ImGui.beginDisabled(locked || !publicSupported);
        if (Widgets.toggle("srv-public", server.publicServer && publicSupported, !locked && publicSupported)) { server.publicServer = !server.publicServer; this.ui.saveQuietly(server); }
        ImGui.sameLine(); Widgets.text(Fonts.body, publicSupported && !locked ? Theme.TEXT : Theme.FAINT, "List this server publicly"); ImGui.endDisabled();
        Widgets.textWrapped(Fonts.label, Theme.MUTED, publicSupported ? "Public servers appear in game through Steam. Connecting by address remains available."
            : "Public listing requires ABNW " + ServerAdministration.PUBLIC_MIN_VERSION + " or newer. Choose Change build to upgrade.");
        ImGui.dummy(0, px(12)); ImGui.beginDisabled(locked);
        Widgets.fieldLabel("Memory", "Maximum server memory"); ImGui.sliderInt("##srv-memory", this.memory, 1024, SettingsPage.maxMemoryMb(), "%d MB");
        Widgets.fieldLabel("Java arguments", "Additional arguments passed to the server JVM"); ImGui.inputTextMultiline("##srv-jvm", this.jvm, -1, px(65));
        if (Widgets.secondary("srv-launch-save", "Save launch settings", Icons.Icon.CHECK)) {
            try { org.teamzetaverse.launcher.util.CommandLine.split(this.jvm.get()); server.memoryMb = SettingsPage.roundTo(this.memory[0], 256);
                server.extraJvmArgs = this.jvm.get(); this.ui.instances.save(server); } catch (Exception e) { this.ui.fail(e); }
        }
        ImGui.separator(); if (this.settingsError != null) Widgets.textWrapped(Fonts.body, Theme.SUN, this.settingsError);
        if (this.properties != null) {
            Widgets.fieldLabel("Server properties", "Changes apply on the next start. World name determines where permission files are saved.");
            for (var field : this.fields.entrySet()) { ImGui.setNextItemWidth(Math.max(px(140), ImGui.getContentRegionAvailX() * .6f)); ImGui.inputText(field.getKey(), field.getValue()); }
            Widgets.fieldLabel("Additional property", "Edit any server.properties key.");
            ImGui.inputTextWithHint("##property-key", "Property name", this.propertyName); ImGui.inputTextWithHint("##property-value", "Value", this.propertyValue);
            if (Widgets.secondary("property-load", "Load value", Icons.Icon.REFRESH)) this.propertyValue.set(this.properties.getProperty(this.propertyName.get().trim(), "")); ImGui.sameLine();
            if (Widgets.secondary("property-set", "Set value", Icons.Icon.PLUS)) {
                String key = this.propertyName.get().trim(); if (!key.isEmpty()) { this.properties.setProperty(key, this.propertyValue.get()); if (this.fields.containsKey(key)) this.fields.get(key).set(this.propertyValue.get()); }
            }
            if (Widgets.primary("properties-save", "Save server properties", Icons.Icon.CHECK, 0, px(36), true)) {
                try {
                    Properties updated = new Properties(); updated.putAll(this.properties); this.fields.forEach((key, value) -> updated.setProperty(key, value.get()));
                    int port = Integer.parseInt(updated.getProperty("server-port"));
                    if (port < 1 || port > 65535 || Integer.parseInt(updated.getProperty("max-players")) < 1) throw new IllegalArgumentException("Use a port from 1–65535 and at least one player slot.");
                    for (String key : new String[]{"online-mode", "white-list"}) if (!List.of("true", "false").contains(updated.getProperty(key))) throw new IllegalArgumentException(key + " must be true or false.");
                    ServerAdministration.saveProperties(server, updated); this.properties = updated; this.settingsError = null; this.access.invalidate();
                } catch (Exception e) { this.settingsError = e.getMessage(); }
            }
        }
        ImGui.endDisabled(); ImGui.dummy(0, px(10));
        if (Widgets.secondary("srv-mods", "Mods folder", Icons.Icon.CUBE)) Desktop.open(server.modsFolder()); ImGui.sameLine();
        if (Widgets.secondary("srv-world", "World folder", Icons.Icon.FOLDER)) {
            try { Desktop.open(ServerAdministration.worldFolder(server)); } catch (IOException e) { this.ui.fail(e); }
        }
    }
    private void drawLog(final Instance server, final GameProcess process) {
        if (ImGui.checkbox("Follow output", this.followLog)) this.followLog = !this.followLog; ImGui.sameLine();
        if (Widgets.secondary("srv-copy-log", "Copy log", Icons.Icon.COPY)) ImGui.setClipboardText(String.join("\n", process != null ? process.lines() : this.lastLogs.getOrDefault(server.id, List.of()))); ImGui.sameLine();
        if (Widgets.secondary("srv-logs-folder", "Log folder", Icons.Icon.FOLDER)) Desktop.open(server.gameFolder().resolve("logs"));
        List<String> lines; boolean changed = false;
        if (process != null) { if (process.version() != this.logVersion) { this.logVersion = process.version(); this.logLines = process.lines(); changed = true; } lines = this.logLines; }
        else lines = this.lastLogs.getOrDefault(server.id, List.of());
        if (ImGui.beginChild("srv-log", 0, Math.max(px(80), ImGui.getContentRegionAvailY() - px(60)), ImGuiChildFlags.None, 0)) {
            if (lines.isEmpty()) Widgets.text(Fonts.label, Theme.MUTED, "Server output will appear here.");
            for (String line : lines) Widgets.text(Fonts.label, line.contains("ERROR") ? Theme.SUN : Theme.MUTED, line);
            if (changed && this.followLog) ImGui.setScrollHereY(1f);
        }
        ImGui.endChild(); ImGui.beginDisabled(process == null || this.stopping);
        ImGui.setNextItemWidth(Math.max(px(120), ImGui.getContentRegionAvailX() - px(110)));
        boolean enter = ImGui.inputTextWithHint("##srv-command", "Server command (without /)", this.command, ImGuiInputTextFlags.EnterReturnsTrue); ImGui.sameLine();
        boolean send = Widgets.button("srv-send", "Send", Icons.Icon.TERMINAL, Widgets.Variant.SECONDARY, 0, px(32), process != null && !this.command.get().isBlank());
        if ((send || enter) && process != null) { try { process.sendCommand(this.command.get().replaceFirst("^/", "")); this.command.set(""); } catch (IOException e) { this.ui.fail(e); } }
        ImGui.endDisabled();
    }
}
