package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.launch.GameLauncher;
import org.teamzetaverse.launcher.launch.GameProcess;
import org.teamzetaverse.launcher.release.Release;

final class ServersPage {
    private final LauncherUi ui;
    private final Map<String, List<String>> lastLogs = new HashMap<>();
    private final ImString newName = new ImString(64);
    private final int[] newMemory = {2048};
    private boolean creating;
    private String selectedId;
    private int logVersion = -1;
    private List<String> logLines = List.of();

    ServersPage(final LauncherUi ui) {
        this.ui = ui;
    }

    void select(final Instance instance) {
        this.selectedId = instance.id;
        this.logVersion = -1;
    }

    void rememberLog(final Instance instance, final List<String> lines) {
        this.lastLogs.put(instance.id, lines);
        this.logVersion = -1;
    }

    private Optional<Instance> selected(final List<Instance> servers) {
        for (Instance server : servers) {
            if (server.id.equals(this.selectedId)) {
                return Optional.of(server);
            }
        }
        return servers.isEmpty() ? Optional.empty() : Optional.of(servers.get(0));
    }

    void draw() {
        List<Instance> servers = this.ui.instances.servers();
        Widgets.text(Fonts.title, Theme.TEXT, "Servers");
        ImGui.sameLine();
        float newWidth = Widgets.textWidth(Fonts.button, "New server") + px(17 + 9 + 36);
        Widgets.alignRight(newWidth);
        if (Widgets.primary("srv-new", "New server", Icons.Icon.PLUS, 0, px(40), !this.creating)) {
            this.creating = true;
            this.newName.set("");
        }
        ImGui.setCursorPosY(ImGui.getCursorPosY() - px(8));
        Widgets.text(Fonts.body, Theme.MUTED, servers.isEmpty()
            ? "A server runs the same build you play, from the same files."
            : servers.size() == 1 ? "1 server" : servers.size() + " servers");
        ImGui.dummy(0, px(10));

        if (this.creating) {
            this.drawCreate();
        }

        if (servers.isEmpty()) {
            if (!this.creating) {
                this.drawEmpty();
            }
            return;
        }

        float listWidth = px(300);
        float height = Math.max(px(380), ImGui.getContentRegionAvailY() - px(4));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(8), px(8));
        if (ImGui.beginChild("srv-list", listWidth, height, ImGuiChildFlags.None, 0)) {
            Optional<Instance> current = this.selected(servers);
            for (Instance server : servers) {
                if (this.serverCard(server, current.isPresent() && current.get().id.equals(server.id))) {
                    this.select(server);
                }
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
        ImGui.sameLine(0, px(20));
        Optional<Instance> current = this.selected(servers);
        if (current.isPresent()) {
            if (Widgets.beginCard("srv-details", 0, height, Theme.SURFACE, px(28), px(26))) {
                this.drawDetails(current.get());
            }
            Widgets.endCard();
        }
    }

    private void drawCreate() {
        float width = ImGui.getContentRegionAvailX();
        if (Widgets.beginCard("srv-create", width, 0, Theme.SURFACE, px(24), px(22))) {
            Widgets.text(Fonts.heading, Theme.TEXT, "New server");
            ImGui.dummy(0, px(10));
            Widgets.text(Fonts.label, Theme.MUTED, "Name");
            ImGui.setNextItemWidth(px(300));
            ImGui.inputText("##srv-name", this.newName);
            ImGui.dummy(0, px(8));
            Widgets.text(Fonts.label, Theme.MUTED, "Memory");
            ImGui.setNextItemWidth(px(300));
            ImGui.sliderInt("##srv-memory", this.newMemory, 1024, 16384);
            ImGui.dummy(0, px(12));
            Release latest = this.ui.latestRelease();
            boolean ready = latest != null && !this.newName.get().isBlank();
            if (Widgets.primary("srv-create-go", "Create server", Icons.Icon.CHECK, 0, px(40), ready)) {
                this.creating = false;
                this.ui.createServer(this.newName.get().trim(), latest, this.newMemory[0]);
            }
            ImGui.sameLine(0, px(10));
            if (Widgets.secondary("srv-create-cancel", "Cancel", Icons.Icon.CLOSE)) {
                this.creating = false;
            }
        }
        Widgets.endCard();
        ImGui.dummy(0, px(14));
    }

    private void drawEmpty() {
        float width = ImGui.getContentRegionAvailX();
        if (Widgets.beginCard("srv-empty", width, 0, Theme.SURFACE, px(40), px(40))) {
            float inner = ImGui.getContentRegionAvailX();
            ImDrawList dl = ImGui.getWindowDrawList();
            float icon = px(56);
            float x = ImGui.getCursorScreenPosX() + (inner - icon) * 0.5f;
            float y = ImGui.getCursorScreenPosY();
            Icons.draw(dl, Icons.Icon.SERVERS, x, y, icon, u32(0xFFFFFF));
            ImGui.dummy(inner, icon + px(16));
            this.centered(Fonts.heading, Theme.TEXT, "No servers yet");
            this.centered(Fonts.body, Theme.MUTED, "A server runs from the same build as the game, so there is nothing extra to download.");
        }
        Widgets.endCard();
    }

    private void centered(final Fonts.Face face, final int color, final String text) {
        float inner = ImGui.getContentRegionAvailX();
        float w = Widgets.textWidth(face, text);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0, (inner - w) * 0.5f));
        Widgets.text(face, color, text);
    }

    private boolean serverCard(final Instance server, final boolean active) {
        boolean running = this.ui.running.get(server.id) != null;
        float width = ImGui.getContentRegionAvailX();
        if (Widgets.beginCard("srv-card-" + server.id, width, 0,
            active ? Theme.SURFACE_HI : Theme.SURFACE, px(16), px(14))) {
            Widgets.text(Fonts.button, Theme.TEXT, Widgets.ellipsize(Fonts.button, server.name, width - px(40)));
            Widgets.text(Fonts.label, Theme.MUTED, server.release.displayName());
            if (running) {
                ImGui.sameLine();
                Widgets.alignRight(Widgets.pillWidth("Running", Icons.Icon.PLAY));
                Widgets.pill("Running", Theme.OK, Theme.OK, 0.14f, Icons.Icon.PLAY);
            } else if (server.publicServer) {
                ImGui.sameLine();
                Widgets.alignRight(Widgets.pillWidth("Public", Icons.Icon.SERVERS));
                Widgets.pill("Public", Theme.EMBER, Theme.EMBER, 0.14f, Icons.Icon.SERVERS);
            }
        }
        Widgets.endCard();
        return ImGui.isItemClicked();
    }

    private void drawDetails(final Instance server) {
        GameProcess process = this.ui.running.get(server.id);
        boolean busy = this.ui.installState(server) == LauncherUi.InstallState.BUSY;
        Widgets.text(Fonts.heading, Theme.TEXT, server.name);
        Widgets.text(Fonts.body, Theme.MUTED, server.release.displayName() + "  ·  " + server.memoryMb + " MB");
        ImGui.dummy(0, px(14));

        boolean accepted;
        try {
            accepted = GameLauncher.eulaAccepted(server);
        } catch (IOException e) {
            accepted = false;
        }

        if (!accepted) {
            Widgets.mascotMessage(Icons.Icon.ALERT, "A server needs the Minecraft EULA accepted before it can start.", Theme.MUTED);
            ImGui.dummy(0, px(10));
            if (Widgets.secondary("srv-eula-read", "Read the EULA", Icons.Icon.EXTERNAL)) {
                Desktop.browse("https://aka.ms/MinecraftEULA");
            }
            ImGui.sameLine(0, px(10));
            if (Widgets.primary("srv-eula-accept", "I accept the EULA", Icons.Icon.CHECK, 0, px(40), true)) {
                try {
                    GameLauncher.acceptEula(server);
                } catch (IOException e) {
                    this.ui.fail(e);
                }
            }
            ImGui.dummy(0, px(14));
        }

        if (process == null) {
            if (Widgets.primary("srv-start", "Start server", Icons.Icon.PLAY, 0, px(48), accepted && !busy)) {
                this.ui.startServer(server);
            }
        } else if (Widgets.button("srv-stop", "Stop server", Icons.Icon.STOP, Widgets.Variant.SECONDARY, 0, px(48), true)) {
            this.ui.stop(server);
        }
        ImGui.sameLine(0, px(10));
        Widgets.alignRight(px(40) * 2 + px(6));
        ImGui.setCursorPosY(ImGui.getCursorPosY() + px(4));
        if (Widgets.iconButton("srv-folder", Icons.Icon.FOLDER, px(40), "Open folder", true)) {
            Desktop.open(server.gameFolder());
        }
        ImGui.sameLine(0, px(6));
        if (Widgets.iconButton("srv-delete", Icons.Icon.TRASH, px(40), "Delete", process == null && !busy)) {
            this.ui.dialogs.openDelete(server);
        }
        ImGui.dummy(0, px(16));

        Widgets.overline("Visibility", Theme.MUTED);
        ImGui.dummy(0, px(6));
        if (Widgets.toggle("srv-public", server.publicServer)) {
            server.publicServer = !server.publicServer;
            this.ui.saveQuietly(server);
        }
        ImGui.sameLine(0, px(10));
        Widgets.text(Fonts.body, Theme.TEXT, "List this server publicly");
        Widgets.textWrapped(Fonts.label, Theme.MUTED,
            "Public servers appear in game without port forwarding. Connecting by address keeps working either way.");
        ImGui.dummy(0, px(16));

        this.drawLog(server, process);
    }

    private void drawLog(final Instance server, final GameProcess process) {
        Widgets.overline("Console", Theme.MUTED);
        ImGui.dummy(0, px(6));
        List<String> lines;
        if (process != null) {
            if (process.version() != this.logVersion) {
                this.logVersion = process.version();
                this.logLines = process.lines();
            }
            lines = this.logLines;
        } else {
            lines = this.lastLogs.getOrDefault(server.id, List.of());
        }
        if (lines.isEmpty()) {
            Widgets.text(Fonts.label, Theme.MUTED, "Nothing yet.");
            return;
        }
        float height = Math.max(px(120), ImGui.getContentRegionAvailY() - px(8));
        if (ImGui.beginChild("srv-log", 0, height, ImGuiChildFlags.None, 0)) {
            for (String line : lines) {
                Widgets.text(Fonts.label, Theme.MUTED, line);
            }
            if (process != null) {
                ImGui.setScrollHereY(1.0f);
            }
        }
        ImGui.endChild();
    }
}
