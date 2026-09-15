package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;

import imgui.ImGui;
import imgui.type.ImString;
import java.nio.file.Files;
import java.nio.file.Path;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.discord.DiscordPresence;
import org.teamzetaverse.launcher.update.UpdateChecker;

final class SettingsPage {
    private final LauncherUi ui;
    private final ImString javaPath = new ImString(512);
    private final ImString clientId = new ImString(128);
    private final int[] memory = {4096};
    private final float[] scale = {1f};
    private boolean bound;
    private boolean showAdvanced;

    SettingsPage(final LauncherUi ui) {
        this.ui = ui;
    }

    static int maxMemoryMb() {
        long total = 16L * 1024;
        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            total = os.getTotalMemorySize() / (1024 * 1024);
        }
        return (int)Math.max(4096, Math.min(64L * 1024, total - 2048));
    }

    static int roundTo(final int value, final int step) {
        return Math.max(step, Math.round(value / (float)step) * step);
    }

    private void bind() {
        if (this.bound) {
            return;
        }
        this.bound = true;
        this.javaPath.set(this.ui.config.javaPath == null ? "" : this.ui.config.javaPath);
        this.clientId.set(this.ui.config.msaClientId == null ? "" : this.ui.config.msaClientId);
        this.memory[0] = this.ui.config.defaultMemoryMb;
        this.scale[0] = this.ui.uiScale();
    }

    void draw() {
        this.bind();
        float full = ImGui.getContentRegionAvailX();
        float width = Math.min(full, px(880));
        ImGui.setCursorPosX(ImGui.getCursorPosX() + (full - width) * 0.5f);
        ImGui.beginGroup();
        Widgets.pageHeader("Settings", "Make the launcher yours. Changes save automatically.");

        this.section("set-game", Icons.Icon.CUBE, "New instances", width, () -> {
            this.row("Renderer", "The renderer new instances start with. Each instance can change it.", () -> {
                int current = InstancesPage.indexOf(LauncherUi.RENDERERS, this.ui.config.defaultRenderer);
                int next = Widgets.segmented("set-renderer", LauncherUi.RENDERER_LABELS, current, this.controlWidth());
                if (next != current) {
                    this.ui.config.defaultRenderer = LauncherUi.RENDERERS[next];
                    this.ui.config.save();
                }
            });
            Widgets.divider();
            this.row("Memory", "How much RAM new instances may use.", () -> {
                Widgets.beginField();
                ImGui.setNextItemWidth(this.controlWidth());
                if (ImGui.sliderInt("##set-memory", this.memory, 2048, maxMemoryMb(), "%d MB")) {
                    this.ui.config.defaultMemoryMb = roundTo(this.memory[0], 256);
                }
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    this.ui.config.save();
                }
                Widgets.endField();
            });
        });

        this.section("set-java", Icons.Icon.COFFEE, "Java", width, () -> {
            this.row("Java executable", "Leave empty and ABNW downloads exactly the Java it needs.", () -> {
                float cw = this.controlWidth();
                Widgets.beginField();
                ImGui.setNextItemWidth(cw - px(40) - px(8));
                ImGui.inputTextWithHint("##set-java", "Managed by ABNW", this.javaPath);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    this.ui.config.javaPath = this.javaPath.get().trim();
                    this.ui.config.save();
                }
                Widgets.endField();
                ImGui.sameLine(0, px(8));
                if (Widgets.iconButton("set-java-browse", Icons.Icon.FOLDER, px(40), "Browse", true)) {
                    Path chosen = Desktop.chooseJava();
                    if (chosen != null) {
                        this.javaPath.set(chosen.toString());
                        this.ui.config.javaPath = chosen.toString();
                        this.ui.config.save();
                    }
                }
            });
        });

        this.section("set-discord", Icons.Icon.DISCORD_WAVE, "Discord", width, () -> {
            this.row("Rich Presence", this.discordHelp(), () -> {
                float cw = this.controlWidth();
                String state = switch (this.ui.discord.state()) {
                    case CONNECTED -> "Connected";
                    case CONNECTING -> "Connecting…";
                    case DISCORD_NOT_RUNNING -> "Waiting for Discord";
                    case DISABLED -> "Off";
                    case NOT_CONFIGURED -> "Unavailable";
                };
                float stateWidth = Widgets.textWidth(Fonts.small, state);
                ImGui.setCursorPosX(ImGui.getCursorPosX() + cw - px(42) - px(12) - stateWidth);
                ImGui.setCursorPosY(ImGui.getCursorPosY() + px(3));
                Widgets.text(Fonts.small, this.ui.discord.state() == DiscordPresence.State.CONNECTED ? Theme.OK : Theme.FAINT, state);
                ImGui.sameLine(0, px(12));
                ImGui.setCursorPosY(ImGui.getCursorPosY() - px(3));
                ImGui.beginDisabled(!this.ui.discord.isConfigured());
                if (Widgets.toggle("set-discord-toggle", this.ui.config.discordPresence && this.ui.discord.isConfigured())) {
                    this.ui.config.discordPresence = !this.ui.config.discordPresence;
                    this.ui.config.save();
                    this.ui.discord.setEnabled(this.ui.config.discordPresence);
                }
                ImGui.endDisabled();
            });
        });

        this.section("set-look", Icons.Icon.IMAGE, "Appearance", width, () -> {
            this.row("Interface size", "Applies the next time you open the launcher.", () -> {
                Widgets.beginField();
                ImGui.setNextItemWidth(this.controlWidth());
                if (ImGui.sliderFloat("##set-scale", this.scale, 0.75f, 2f, "%.2fx")) {
                    this.ui.config.uiScale = Math.round(this.scale[0] * 20f) / 20f;
                }
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    this.ui.config.save();
                }
                Widgets.endField();
            });
            Widgets.divider();
            this.row("Home screen showcase", "Drop your favourite screenshots into the showcase folder to feature them on Home.", () -> {
                if (Widgets.secondary("set-showcase", "Open showcase folder", Icons.Icon.FOLDER)) {
                    try {
                        Files.createDirectories(this.ui.paths.showcase());
                    } catch (java.io.IOException ignored) {
                    }
                    Desktop.open(this.ui.paths.showcase());
                }
                ImGui.sameLine(0, px(8));
                if (Widgets.iconButton("set-showcase-refresh", Icons.Icon.REFRESH, px(40), "Refresh", true)) {
                    new Thread(this.ui::scanScreenshots, "ABNW screenshot scan").start();
                }
            });
        });

        this.section("set-updates", Icons.Icon.DOWNLOAD, "Updates", width, () -> {
            UpdateChecker.Status status = this.ui.updateStatus;
            UpdateChecker.LauncherUpdate update = status.launcherUpdate();
            String help = update != null ? "Launcher v" + update.version() + " is ready to download."
                : status.checked() ? "You're on the latest launcher. Checked " + Format.relative(status.checkedAt().toEpochMilli()).toLowerCase() + "."
                : "Checking for updates…";
            this.row(BuildInfo.isDevBuild() ? "Development build" : "Launcher v" + BuildInfo.VERSION, help, () -> {
                if (update != null) {
                    if (Widgets.primary("set-get-update", "Download", Icons.Icon.DOWNLOAD, 0, px(40), true)) {
                        Desktop.browse(update.url());
                    }
                    ImGui.sameLine(0, px(8));
                }
                if (Widgets.secondary("set-check-updates", this.ui.checkingUpdates ? "Checking…" : "Check now", Icons.Icon.REFRESH, !this.ui.checkingUpdates)) {
                    this.ui.checkForUpdatesNow();
                }
            });
        });

        this.section("set-storage", Icons.Icon.FOLDER, "Storage", width, () -> {
            this.row("Launcher data", this.ui.paths.root().toString(), () -> {
                if (Widgets.secondary("set-open-data", "Open folder", Icons.Icon.FOLDER)) {
                    Desktop.open(this.ui.paths.root());
                }
                ImGui.sameLine(0, px(8));
                if (Widgets.secondary("set-open-logs", "Logs", Icons.Icon.TERMINAL)) {
                    Desktop.open(this.ui.paths.logs());
                }
            });
        });

        ImGui.dummy(0, px(4));
        if (Widgets.link("set-advanced", this.showAdvanced ? "Hide advanced settings" : "Show advanced settings",
            this.showAdvanced ? Icons.Icon.CHEVRON_DOWN : Icons.Icon.CHEVRON_RIGHT)) {
            this.showAdvanced = !this.showAdvanced;
        }
        ImGui.dummy(0, px(8));
        if (this.showAdvanced) {
            this.section("set-advanced-card", Icons.Icon.WRENCH, "Advanced", width, () -> {
                this.row("Microsoft client ID", "Only change this if you know you need to. Leave empty to use the built-in one.", () -> {
                    Widgets.beginField();
                    ImGui.setNextItemWidth(this.controlWidth());
                    Fonts.mono.push();
                    ImGui.inputTextWithHint("##set-client", "Built-in", this.clientId);
                    ImGui.popFont();
                    if (ImGui.isItemDeactivatedAfterEdit()) {
                        this.ui.config.msaClientId = this.clientId.get().trim();
                        this.ui.config.save();
                    }
                    Widgets.endField();
                });
            });
        }
        ImGui.endGroup();
    }

    private String discordHelp() {
        if (!this.ui.discord.isConfigured()) {
            return "Rich Presence isn't available in this build.";
        }
        return "Show friends that you're playing ABNW, which build, and for how long.";
    }

    private float controlWidth() {
        return Math.min(px(340), ImGui.getContentRegionAvailX());
    }

    private void section(final String id, final Icons.Icon icon, final String title, final float width, final Runnable body) {
        if (Widgets.beginCard(id, width, 0, Theme.SURFACE, px(26), px(22))) {
            Widgets.cardTitle(icon, title);
            ImGui.dummy(0, px(4));
            body.run();
        }
        Widgets.endCard();
        ImGui.dummy(0, px(8));
    }

    private void row(final String label, final String help, final Runnable control) {
        float total = ImGui.getContentRegionAvailX();
        float controlWidth = Math.min(px(340), total * 0.48f);
        float textWidth = total - controlWidth - px(24);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        ImGui.beginGroup();
        Widgets.text(Fonts.label, Theme.TEXT, label);
        if (help != null && !help.isEmpty()) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() - px(6));
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + textWidth);
            Fonts.small.push();
            Theme.pushText(Theme.FAINT);
            ImGui.textUnformatted(help);
            ImGui.popStyleColor();
            ImGui.popFont();
            ImGui.popTextWrapPos();
        }
        ImGui.endGroup();
        float textBottom = ImGui.getCursorPosY();
        ImGui.sameLine(0, 0);
        ImGui.setCursorPosX(startX + total - controlWidth);
        ImGui.setCursorPosY(startY);
        ImGui.beginGroup();
        ImGui.pushItemWidth(controlWidth);
        control.run();
        ImGui.popItemWidth();
        ImGui.endGroup();
        if (ImGui.getCursorPosY() < textBottom) {
            ImGui.setCursorPosY(textBottom);
            ImGui.dummy(0, 0);
        }
    }
}
