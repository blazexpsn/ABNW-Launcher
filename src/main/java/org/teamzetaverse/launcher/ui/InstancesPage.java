package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.launch.GameProcess;
import org.teamzetaverse.launcher.release.Release;

final class InstancesPage {
    private static final int[][] TILE_COLORS = {
        {0xB423EC, 0xE3A2FD}, {0x7C2ABA, 0x561A83}, {0xC95BF2, 0xB423EC}, {0x561A83, 0x2A0D3D}, {0xE3A2FD, 0xC95BF2}, {0x9258AA, 0x3B0F5A}
    };

    private final LauncherUi ui;
    private final Map<String, List<String>> lastLogs = new HashMap<>();
    private String editorsFor;
    private final ImInt editRenderer = new ImInt(0);
    private final int[] editMemory = {4096};
    private final ImString editJvmArgs = new ImString(512);
    private int tab;
    private boolean follow = true;
    private int logVersion = -1;
    private List<String> logLines = List.of();

    InstancesPage(final LauncherUi ui) {
        this.ui = ui;
    }

    void rememberLog(final Instance instance, final List<String> lines) {
        this.lastLogs.put(instance.id, lines);
        this.logVersion = -1;
    }

    static void drawTile(final ImDrawList dl, final Instance instance, final float x, final float y, final float size) {
        int[] colors = TILE_COLORS[Math.floorMod(instance.id.hashCode(), TILE_COLORS.length)];
        float r = size * 0.28f;
        Pixel.rect(dl, x, y, x + size, y + size, u32(colors[1]));
        dl.addRectFilledMultiColor(x + r * 0.3f, y + r * 0.3f, x + size - r * 0.3f, y + size - r * 0.3f, u32(colors[0]), u32(colors[0], 0.6f),
            u32(colors[1], 0.2f), u32(colors[0], 0.85f));
        PenguinWardrobe wardrobe = PenguinWardrobe.current();
        ImageCache.Texture custom = wardrobe == null ? null : wardrobe.customIcon(instance);
        if (custom != null) {
            float aspect = custom.aspect();
            float cu = aspect > 1f ? (1f - 1f / aspect) * 0.5f : 0f;
            float cv = aspect < 1f ? (1f - aspect) * 0.5f : 0f;
            dl.addImage(custom.id(), x, y, x + size, y + size, cu, cv, 1f - cu, 1f - cv, u32(0xFFFFFF));
        } else if (wardrobe != null && !Instance.ICON_IMAGE.equals(instance.icon)) {
            dl.pushClipRect(x, y, x + size, y + size, true);
            PenguinWardrobe.drawBust(dl, x, y, size, wardrobe.look(instance));
            dl.popClipRect();
        } else {
            String initial = instance.name.isBlank() ? "?" : instance.name.substring(0, 1).toUpperCase();
            Fonts.Face face = size >= px(52) ? Fonts.title : Fonts.heading;
            float tw = Widgets.textWidth(face, initial);
            Widgets.drawText(dl, face, x + (size - tw) * 0.5f, y + (size - face.size()) * 0.5f - px(1), u32(Theme.ON_EMBER, 0.9f), initial);
        }
        Pixel.frame(dl, x, y, x + size, y + size, u32(colors[1]), Math.max(1f, r * 0.45f));
    }

    void draw() {
        List<Instance> all = this.ui.instances.all();
        Widgets.text(Fonts.title, Theme.TEXT, "Instances");
        ImGui.sameLine();
        float newWidth = Widgets.textWidth(Fonts.button, "New instance") + px(17 + 9 + 36);
        float importWidth = Widgets.textWidth(Fonts.label, "Import") + px(17 + 9 + 36);
        Widgets.alignRight(newWidth + importWidth + px(10));
        if (Widgets.secondary("inst-import", "Import", Icons.Icon.IMPORT)) {
            this.ui.importArchive();
        }
        ImGui.sameLine(0, px(10));
        if (Widgets.primary("inst-new", "New instance", Icons.Icon.PLUS, 0, px(40), true)) {
            this.ui.dialogs.openNewInstance();
        }
        ImGui.setCursorPosY(ImGui.getCursorPosY() - px(8));
        Widgets.text(Fonts.body, Theme.MUTED, all.isEmpty() ? "Every instance is its own world, with its own build, mods and settings."
            : all.size() == 1 ? "1 instance" : all.size() + " instances");
        ImGui.dummy(0, px(10));

        if (all.isEmpty()) {
            this.drawEmpty();
            return;
        }

        float listWidth = px(300);
        float gap = px(20);
        float height = Math.max(px(420), ImGui.getContentRegionAvailY() - px(4));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(8), px(8));
        if (ImGui.beginChild("inst-list", listWidth, height, ImGuiChildFlags.None, 0)) {
            Optional<Instance> selected = this.ui.selectedInstance();
            for (Instance instance : all) {
                if (this.instanceCard(instance, selected.isPresent() && selected.get().id.equals(instance.id))) {
                    this.ui.select(instance);
                }
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
        ImGui.sameLine(0, gap);
        Optional<Instance> selected = this.ui.selectedInstance();
        if (selected.isPresent()) {
            if (Widgets.beginCard("inst-details", 0, height, Theme.SURFACE, px(28), px(26))) {
                this.drawDetails(selected.get());
            }
            Widgets.endCard();
        }
    }

    private void drawEmpty() {
        float width = ImGui.getContentRegionAvailX();
        if (Widgets.beginCard("inst-empty", width, 0, Theme.SURFACE, px(40), px(40))) {
            float inner = ImGui.getContentRegionAvailX();
            ImDrawList dl = ImGui.getWindowDrawList();
            float icon = px(56);
            float x = ImGui.getCursorScreenPosX() + (inner - icon) * 0.5f;
            float y = ImGui.getCursorScreenPosY();
            Icons.draw(dl, Icons.Icon.PENGUIN_EMPTY, x, y, icon, u32(0xFFFFFF));
            ImGui.dummy(inner, icon + px(16));
            this.centered(Fonts.heading, Theme.TEXT, "No worlds yet");
            this.centered(Fonts.body, Theme.MUTED, "Create an instance and ABNW installs everything it needs.");
            ImGui.dummy(0, px(10));
            float bw = Widgets.textWidth(Fonts.button, "Create your first instance") + px(17 + 9 + 36);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + (inner - bw) * 0.5f);
            if (Widgets.primary("inst-empty-new", "Create your first instance", Icons.Icon.PLUS, bw, px(46), true)) {
                this.ui.dialogs.openNewInstance();
            }
        }
        Widgets.endCard();
    }

    private void centered(final Fonts.Face face, final int color, final String text) {
        float w = Widgets.textWidth(face, text);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0, (ImGui.getContentRegionAvailX() - w) * 0.5f));
        Widgets.text(face, color, text);
    }

    private boolean instanceCard(final Instance instance, final boolean selected) {
        String id = "inst-card-" + instance.id;
        float w = ImGui.getContentRegionAvailX();
        float h = px(76);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        float hv = Motion.hover(id + "#hover", hovered);
        float sv = Motion.to(id + "#selected", selected ? 1f : 0f, 14f);
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.rect(dl, x, y, x + w, y + h, u32(Theme.mix(Theme.mix(Theme.SURFACE, Theme.SURFACE_HI, hv), Theme.SURFACE_HI, sv)));
        Pixel.frame(dl, x, y, x + w, y + h, u32(Theme.mix(Theme.BORDER_SOFT, Theme.EMBER, sv * 0.8f)), px(1));
        float tile = px(46);
        drawTile(dl, instance, x + px(14), y + (h - tile) * 0.5f, tile);
        float tx = x + px(14) + tile + px(13);
        float tw = w - (tx - x) - px(14);
        boolean playing = this.ui.running.containsKey(instance.id);
        Widgets.drawText(dl, Fonts.label, tx, y + px(15), u32(Theme.TEXT), Widgets.ellipsize(Fonts.label, instance.name, tw - (playing ? px(20) : 0)));
        String sub = instance.release.displayName() + "  ·  " + (playing ? "Playing now" : instance.lastPlayed > 0 ? Format.relative(instance.lastPlayed) : "Not played yet");
        Widgets.drawText(dl, Fonts.small, tx, y + px(15) + Fonts.label.size() + px(4), u32(playing ? Theme.OK : Theme.MUTED), Widgets.ellipsize(Fonts.small, sub, tw));
        if (playing) {
            float pulse = 0.5f + 0.5f * (float)Math.sin(ImGui.getTime() * 3.0);
            Pixel.dot(dl, x + w - px(20), y + px(22), px(7), u32(Theme.OK, 0.18f * pulse));
            Pixel.dot(dl, x + w - px(20), y + px(22), px(3.5f), u32(Theme.OK));
        } else if (this.ui.isOutdated(instance)) {
            Pixel.dot(dl, x + w - px(20), y + px(22), px(3.5f), u32(Theme.SUN));
        }
        return clicked;
    }

    private void bindEditors(final Instance instance) {
        if (instance.id.equals(this.editorsFor)) {
            return;
        }
        this.editorsFor = instance.id;
        this.editRenderer.set(indexOf(LauncherUi.RENDERERS, instance.renderer));
        this.editMemory[0] = instance.memoryMb > 0 ? instance.memoryMb : this.ui.config.defaultMemoryMb;
        this.editJvmArgs.set(instance.extraJvmArgs == null ? "" : instance.extraJvmArgs);
        this.logVersion = -1;
    }

    private void drawDetails(final Instance instance) {
        this.bindEditors(instance);
        GameProcess process = this.ui.running.get(instance.id);
        LauncherUi.InstallState state = this.ui.installState(instance);
        boolean busy = state == LauncherUi.InstallState.BUSY;
        ImDrawList dl = ImGui.getWindowDrawList();

        float tile = px(68);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        drawTile(dl, instance, x, y, tile);
        ImGui.dummy(tile, tile);
        ImGui.sameLine(0, px(18));
        ImGui.beginGroup();
        ImGui.setCursorPosY(ImGui.getCursorPosY() + px(2));
        Widgets.text(Fonts.title, Theme.TEXT, Widgets.ellipsize(Fonts.title, instance.name, ImGui.getContentRegionAvailX()));
        ImGui.setCursorPosY(ImGui.getCursorPosY() - px(4));
        Widgets.pill(instance.release.displayName(), Theme.EMBER, Theme.EMBER, 0.14f, Icons.Icon.CUBE);
        ImGui.sameLine(0, px(6));
        Widgets.pill(LauncherUi.RENDERER_LABELS[indexOf(LauncherUi.RENDERERS, instance.renderer)], Theme.MUTED, 0xFFFFFF, 0.06f, Icons.Icon.CHIP);
        ImGui.sameLine(0, px(6));
        if (process != null) {
            Widgets.pill("Playing", Theme.OK, Theme.OK, 0.14f, Icons.Icon.PLAY);
        } else if (state == LauncherUi.InstallState.NEEDS_INSTALL) {
            Widgets.pill("Not installed", Theme.MUTED, 0xFFFFFF, 0.06f, Icons.Icon.DOWNLOAD);
        } else if (busy) {
            Widgets.pill("Working", Theme.SUN, Theme.SUN, 0.12f, Icons.Icon.CLOCK);
        } else {
            Widgets.pill("Ready", Theme.OK, Theme.OK, 0.12f, Icons.Icon.CHECK);
        }
        ImGui.endGroup();
        ImGui.dummy(0, px(12));

        if (process != null) {
            if (Widgets.button("det-stop", "Stop", Icons.Icon.STOP, Widgets.Variant.SECONDARY, px(170), px(48), true)) {
                this.ui.stop(instance);
            }
        } else if (Widgets.primary("det-play", busy ? "Preparing" : "Play", Icons.Icon.PLAY, px(170), px(48), !busy)) {
            this.ui.play(instance);
        }
        Release latest = this.ui.latestRelease();
        if (this.ui.isOutdated(instance) && latest != null) {
            ImGui.sameLine(0, px(10));
            if (Widgets.button("det-update", "Update to " + latest.displayName(), Icons.Icon.UPDATE, Widgets.Variant.SECONDARY, 0, px(48), process == null && !busy)) {
                this.ui.dialogs.openChangeRelease(instance, latest);
            }
        }
        ImGui.sameLine(0, px(10));
        float icons = px(40) * 5 + px(6) * 4;
        Widgets.alignRight(icons);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + px(4));
        if (Widgets.iconButton("det-folder", Icons.Icon.FOLDER, px(40), "Open folder", true)) {
            Desktop.open(instance.gameFolder());
        }
        ImGui.sameLine(0, px(6));
        if (Widgets.iconButton("det-swap", Icons.Icon.SWAP, px(40), "Change build", process == null && !busy)) {
            this.ui.dialogs.openChangeRelease(instance, null);
        }
        ImGui.sameLine(0, px(6));
        if (Widgets.iconButton("det-export", Icons.Icon.EXPORT, px(40), "Export as .abnw", process == null && !busy)) {
            this.ui.exportArchive(instance);
        }
        ImGui.sameLine(0, px(6));
        if (Widgets.iconButton("det-rename", Icons.Icon.EDIT, px(40), "Rename", !busy)) {
            this.ui.dialogs.openRename(instance);
        }
        ImGui.sameLine(0, px(6));
        if (Widgets.iconButton("det-delete", Icons.Icon.TRASH, px(40), "Delete", process == null && !busy)) {
            this.ui.dialogs.openDelete(instance);
        }
        ImGui.dummy(0, px(10));

        this.tab = this.tabs(new String[]{"Overview", "Icon", "Game log"}, this.tab);
        ImGui.dummy(0, px(12));
        if (this.tab == 0) {
            this.drawOverview(instance, busy || process != null);
        } else if (this.tab == 1) {
            this.drawIcon(instance);
        } else {
            this.drawLog(instance, process);
        }
    }

    private void drawIcon(final Instance instance) {
        PenguinWardrobe wardrobe = this.ui.wardrobe;
        this.ui.loadCosmetics();
        float width = ImGui.getContentRegionAvailX();
        ImDrawList dl = ImGui.getWindowDrawList();
        Widgets.fieldLabel("Icon", "A penguin you can dress up, or any picture you like.");
        boolean image = Instance.ICON_IMAGE.equals(instance.icon);
        int picked = Widgets.segmented("icon-mode", new String[]{"Penguin", "Custom image"}, image ? 1 : 0, Math.min(width, px(360)));
        if (picked == 1 && !image) {
            if (instance.customIcon() == null) {
                this.chooseIcon(instance);
            } else {
                instance.icon = Instance.ICON_IMAGE;
                this.ui.saveQuietly(instance);
            }
        } else if (picked == 0 && image) {
            instance.icon = Instance.ICON_PENGUIN;
            this.ui.saveQuietly(instance);
        }
        ImGui.dummy(0, px(12));

        if (Instance.ICON_IMAGE.equals(instance.icon)) {
            float tile = px(96);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            drawTile(dl, instance, x, y, tile);
            ImGui.dummy(tile, tile);
            ImGui.sameLine(0, px(18));
            ImGui.beginGroup();
            Widgets.textWrapped(Fonts.body, Theme.MUTED, "Your picture is shown as it is. Penguin accessories only go on the penguin.");
            ImGui.dummy(0, px(6));
            if (Widgets.secondary("icon-choose", "Choose another image", Icons.Icon.IMAGE, true)) {
                this.chooseIcon(instance);
            }
            ImGui.endGroup();
            return;
        }

        float preview = px(150);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        int[] colors = TILE_COLORS[Math.floorMod(instance.id.hashCode(), TILE_COLORS.length)];
        Pixel.rect(dl, x, y, x + preview, y + preview, u32(colors[1]));
        dl.addRectFilledMultiColor(x, y, x + preview, y + preview, u32(colors[0], 0.5f), u32(colors[0], 0.3f), u32(colors[1], 0.2f),
            u32(colors[0], 0.4f));
        PenguinWardrobe.drawFull(dl, x, y, preview, wardrobe.look(instance));
        Pixel.frame(dl, x, y, x + preview, y + preview, u32(colors[1]), px(3));
        ImGui.dummy(preview, preview);
        ImGui.sameLine(0, px(20));
        ImGui.beginGroup();
        float swatch = px(54);
        float gap = px(6);
        float rowWidth = Math.max(swatch, width - preview - px(20));
        int perRow = Math.max(1, (int)((rowWidth + gap) / (swatch + gap)));
        for (int s = 0; s < PenguinWardrobe.SLOTS.length; s++) {
            String slot = PenguinWardrobe.SLOTS[s];
            Widgets.text(Fonts.label, Theme.MUTED, PenguinWardrobe.SLOT_LABELS[s]);
            String chosen = wardrobe.chosenId(instance, slot);
            List<PenguinWardrobe.Accessory> items = wardrobe.accessories(slot);
            if (this.swatch(slot + "-none", List.of(), PenguinWardrobe.NONE.equals(chosen), false, "Nothing", swatch)) {
                wardrobe.wear(instance, slot, PenguinWardrobe.NONE);
                this.ui.saveQuietly(instance);
            }
            int count = 1;
            for (PenguinWardrobe.Accessory item : items) {
                if (count % perRow != 0) {
                    ImGui.sameLine(0, gap);
                }
                boolean owned = wardrobe.owns(item);
                String tip = owned ? item.name() : item.name() + " (" + item.price() + " in the Cosmetics store)";
                if (this.swatch(slot + "-" + item.id(), List.of(item.texture()), item.id().equals(chosen), !owned, tip, swatch)) {
                    if (owned) {
                        wardrobe.wear(instance, slot, item.id());
                        this.ui.saveQuietly(instance);
                    } else {
                        this.ui.navigate(LauncherUi.Page.COSMETICS);
                    }
                }
                count++;
            }
            ImGui.dummy(0, px(6));
        }
        ImGui.endGroup();
    }

    private boolean swatch(final String id, final List<String> textures, final boolean selected, final boolean locked, final String tooltip,
                           final float size) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("sw-" + id, size, size);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            Widgets.tooltip(tooltip);
        }
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.rect(dl, x, y, x + size, y + size, u32(hovered ? Theme.SURFACE_HOVER : Theme.SURFACE_HI));
        dl.pushClipRect(x, y, x + size, y + size, true);
        PenguinWardrobe.drawBust(dl, x + px(2), y + px(2), size - px(4), textures);
        dl.popClipRect();
        if (locked) {
            Pixel.rect(dl, x, y, x + size, y + size, u32(Theme.BG, 0.55f));
            Icons.draw(dl, Icons.Icon.HEART, x + size - px(18), y + px(4), px(14), u32(Theme.SUN));
        }
        Pixel.frame(dl, x, y, x + size, y + size, u32(selected ? Theme.EMBER : Theme.BORDER_SOFT), selected ? px(2) : px(1));
        return clicked;
    }

    private void chooseIcon(final Instance instance) {
        Path chosen = Desktop.chooseImage();
        if (chosen == null) {
            return;
        }
        try {
            String lower = chosen.getFileName().toString().toLowerCase(Locale.ROOT);
            String extension = lower.endsWith(".png") ? "png" : lower.endsWith(".jpg") ? "jpg" : lower.endsWith(".jpeg") ? "jpeg" : null;
            if (extension == null) {
                throw new IOException("Instance icons can be PNG or JPEG images.");
            }
            if (Files.size(chosen) > 8L * 1024 * 1024) {
                throw new IOException("That image is over 8 MB. Choose a smaller one.");
            }
            Path previous = Instance.ICON_IMAGE.equals(instance.icon) ? instance.customIcon() : null;
            String name = "icon-" + System.currentTimeMillis() + "." + extension;
            Files.copy(chosen, instance.folder().resolve(name));
            instance.iconFile = name;
            instance.icon = Instance.ICON_IMAGE;
            this.ui.saveQuietly(instance);
            if (previous != null) {
                Files.deleteIfExists(previous);
            }
        } catch (IOException e) {
            this.ui.fail(e);
        }
    }

    private int tabs(final String[] labels, final int selected) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float x0 = ImGui.getCursorScreenPosX();
        float y0 = ImGui.getCursorScreenPosY();
        float h = px(38);
        float fullWidth = ImGui.getContentRegionAvailX();
        dl.addLine(x0, y0 + h, x0 + fullWidth, y0 + h, u32(Theme.BORDER_SOFT), px(1));
        int result = selected;
        float cursor = x0;
        for (int i = 0; i < labels.length; i++) {
            float w = Widgets.textWidth(Fonts.label, labels[i]) + px(28);
            ImGui.setCursorScreenPos(cursor, y0);
            if (ImGui.invisibleButton("tab-" + i, w, h)) {
                result = i;
            }
            boolean hovered = ImGui.isItemHovered();
            if (hovered) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }
            float av = Motion.to("tab#" + i, i == selected ? 1f : 0f, 16f);
            int color = u32(Theme.mix(hovered ? Theme.TEXT : Theme.MUTED, Theme.TEXT, av));
            Widgets.drawText(dl, Fonts.label, cursor + px(14), y0 + (h - Fonts.label.size()) * 0.5f - px(2), color, labels[i]);
            float line = (w - px(20)) * av;
            Pixel.rect(dl, cursor + (w - line) * 0.5f, y0 + h - px(2), cursor + (w + line) * 0.5f, y0 + h + px(1), u32(Theme.EMBER, av));
            cursor += w + px(4);
        }
        ImGui.setCursorScreenPos(x0, y0);
        ImGui.dummy(fullWidth, h + px(1));
        return result;
    }

    private void drawOverview(final Instance instance, final boolean locked) {
        float width = ImGui.getContentRegionAvailX();
        float gap = px(12);
        float statWidth = (width - gap * 2) / 3f;
        this.stat("stat-last", Icons.Icon.CLOCK, "Last played", instance.lastPlayed > 0 ? Format.relative(instance.lastPlayed) : "Not yet", statWidth);
        ImGui.sameLine(0, gap);
        this.stat("stat-time", Icons.Icon.PLAY, "Time played", Format.duration(instance.totalPlayMillis), statWidth);
        ImGui.sameLine(0, gap);
        this.stat("stat-created", Icons.Icon.SPARK, "Created", Format.date(instance.created), statWidth);
        ImGui.dummy(0, px(14));

        Widgets.text(Fonts.heading, Theme.TEXT, "Game settings");
        ImGui.dummy(0, px(2));
        float half = (width - px(20)) * 0.5f;
        ImGui.beginGroup();
        Widgets.fieldLabel("Renderer", "Vulkan is fastest; OpenGL works everywhere.");
        int renderer = Widgets.segmented("det-renderer", LauncherUi.RENDERER_LABELS, this.editRenderer.get(), half);
        if (renderer != this.editRenderer.get()) {
            this.editRenderer.set(renderer);
            instance.renderer = LauncherUi.RENDERERS[renderer];
            this.ui.saveQuietly(instance);
        }
        ImGui.endGroup();
        ImGui.sameLine(0, px(20));
        ImGui.beginGroup();
        Widgets.fieldLabel("Memory", "How much RAM the game may use.");
        Widgets.beginField();
        ImGui.setNextItemWidth(half);
        if (ImGui.sliderInt("##det-memory", this.editMemory, 2048, SettingsPage.maxMemoryMb(), "%d MB")) {
            instance.memoryMb = SettingsPage.roundTo(this.editMemory[0], 256);
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            this.ui.saveQuietly(instance);
        }
        Widgets.endField();
        ImGui.endGroup();
        ImGui.dummy(0, px(8));

        Widgets.fieldLabel("Java arguments", "Advanced. Extra flags passed to Java when the game starts.");
        Widgets.beginField();
        ImGui.setNextItemWidth(width);
        ImGui.beginDisabled(locked);
        Fonts.mono.push();
        ImGui.inputTextWithHint("##det-jvm", "-XX:+UseZGC", this.editJvmArgs);
        ImGui.popFont();
        if (ImGui.isItemDeactivatedAfterEdit()) {
            instance.extraJvmArgs = this.editJvmArgs.get().trim();
            this.ui.saveQuietly(instance);
        }
        ImGui.endDisabled();
        Widgets.endField();
        ImGui.dummy(0, px(14));

        Widgets.text(Fonts.heading, Theme.TEXT, "Folders");
        ImGui.dummy(0, px(2));
        if (Widgets.secondary("det-open-game", "Game folder", Icons.Icon.FOLDER)) {
            Desktop.open(instance.gameFolder());
        }
        ImGui.sameLine(0, px(8));
        if (Widgets.secondary("det-open-mods", "Mods", Icons.Icon.CUBE)) {
            Desktop.open(instance.modsFolder());
        }
        ImGui.sameLine(0, px(8));
        if (Widgets.secondary("det-open-saves", "Worlds", Icons.Icon.INSTANCES)) {
            Desktop.open(instance.gameFolder().resolve("saves"));
        }
        ImGui.sameLine(0, px(8));
        if (Widgets.secondary("det-open-shots", "Screenshots", Icons.Icon.IMAGE)) {
            Desktop.open(instance.gameFolder().resolve("screenshots"));
        }
    }

    private void stat(final String id, final Icons.Icon icon, final String label, final String value, final float width) {
        float h = px(74);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.card(dl, x, y, x + width, y + h, 0f, u32(Theme.SURFACE_HI));
        float iconSize = px(16);
        Icons.draw(dl, icon, x + px(16), y + px(16), iconSize, u32(Theme.EMBER));
        Widgets.drawText(dl, Fonts.small, x + px(40), y + px(15), u32(Theme.MUTED), label);
        Widgets.drawText(dl, Fonts.heading, x + px(16), y + px(15) + iconSize + px(9), u32(Theme.TEXT),
            Widgets.ellipsize(Fonts.heading, value, width - px(32)));
        ImGui.invisibleButton(id, width, h);
    }

    private void drawLog(final Instance instance, final GameProcess process) {
        List<String> lines;
        if (process != null) {
            if (process.version() != this.logVersion) {
                this.logVersion = process.version();
                this.logLines = process.lines();
            }
            lines = this.logLines;
        } else {
            lines = this.lastLogs.getOrDefault(instance.id, List.of());
        }

        Widgets.text(Fonts.label, Theme.TEXT, "Follow output");
        ImGui.sameLine(0, px(10));
        ImGui.setCursorPosY(ImGui.getCursorPosY() - px(2));
        if (Widgets.toggle("log-follow", this.follow)) {
            this.follow = !this.follow;
        }
        ImGui.sameLine();
        Widgets.alignRight(px(34) * 2 + px(6));
        ImGui.setCursorPosY(ImGui.getCursorPosY() - px(5));
        if (Widgets.iconButton("log-copy", Icons.Icon.COPY, px(34), "Copy log", !lines.isEmpty())) {
            ImGui.setClipboardText(String.join("\n", lines));
        }
        ImGui.sameLine(0, px(6));
        if (Widgets.iconButton("log-folder", Icons.Icon.FOLDER, px(34), "Open logs folder", true)) {
            Desktop.open(this.ui.paths.logs());
        }
        ImGui.dummy(0, px(4));

        float[] bg = Theme.rgba(0x0E0418, 1f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg[0], bg[1], bg[2], 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(16), px(14));
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(8), px(2));
        boolean open = ImGui.beginChild("log", 0, Math.max(px(260), ImGui.getContentRegionAvailY()), ImGuiChildFlags.AlwaysUseWindowPadding,
            ImGuiWindowFlags.HorizontalScrollbar);
        ImGui.popStyleVar(3);
        ImGui.popStyleColor();
        if (open) {
            if (lines.isEmpty()) {
                Widgets.text(Fonts.mono, Theme.FAINT, process == null ? "Start the game to see its output here." : "Waiting for output…");
            } else {
                Fonts.mono.push();
                for (String line : lines) {
                    int color = line.contains("ERROR") || line.contains("Exception") || line.startsWith("\tat ") ? Theme.ERROR
                        : line.contains("WARN") ? Theme.WARN
                        : line.startsWith("[launcher]") ? Theme.SUN
                        : 0xE7E4E7;
                    Theme.pushText(color);
                    ImGui.textUnformatted(line);
                    ImGui.popStyleColor();
                }
                ImGui.popFont();
                if (this.follow && process != null) {
                    ImGui.setScrollHereY(1f);
                }
            }
        }
        ImGui.endChild();
    }

    static int indexOf(final String[] values, final String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }
}
