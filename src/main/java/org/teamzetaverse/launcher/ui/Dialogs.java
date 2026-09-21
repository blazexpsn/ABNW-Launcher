package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;
import static org.teamzetaverse.launcher.ui.Theme.u32;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.io.IOException;
import org.teamzetaverse.launcher.auth.MicrosoftAuth;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.release.ReleaseService;
import org.teamzetaverse.launcher.task.Progress;

final class Dialogs {
    private static final String NEW_INSTANCE = "New instance";
    private static final String CHANGE_RELEASE = "Change build";
    private static final String SIGN_IN = "Sign in";
    private static final String RENAME = "Rename";
    private static final String DELETE = "Delete";
    private static final String ERROR = "Something went wrong";

    private final LauncherUi ui;
    private String pending;

    private ReleaseService.Listing listing;
    private String listingError;
    private boolean listingLoading;
    private int selectedRelease = -1;
    private String preselectId;

    private final ImString newName = new ImString(64);
    private int newRenderer;
    private final int[] newMemory = {4096};

    private Instance target;
    private final ImString renameText = new ImString(64);

    private MicrosoftAuth.DeviceCode deviceCode;
    private Progress signInProgress;
    private double copiedAt = -10;

    Dialogs(final LauncherUi ui) {
        this.ui = ui;
    }

    void openNewInstance() {
        this.newName.set("");
        this.newRenderer = InstancesPage.indexOf(LauncherUi.RENDERERS, this.ui.config.defaultRenderer);
        this.newMemory[0] = this.ui.config.defaultMemoryMb;
        this.preselectId = null;
        this.loadListing();
        this.pending = NEW_INSTANCE;
    }

    void openChangeRelease(final Instance instance, final Release preselect) {
        this.target = instance;
        this.preselectId = preselect == null ? null : preselect.id;
        this.loadListing();
        this.pending = CHANGE_RELEASE;
    }

    void openRename(final Instance instance) {
        this.target = instance;
        this.renameText.set(instance.name);
        this.pending = RENAME;
    }

    void openDelete(final Instance instance) {
        this.target = instance;
        this.pending = DELETE;
    }

    void openSignIn(final MicrosoftAuth.DeviceCode code, final Progress progress) {
        this.deviceCode = code;
        this.signInProgress = progress;
        this.pending = SIGN_IN;
    }

    void signInFinished() {
        this.deviceCode = null;
        this.signInProgress = null;
    }

    void draw() {
        if (this.pending != null) {
            ImGui.openPopup(this.pending);
            this.pending = null;
        } else if (!this.ui.errors.isEmpty() && !ImGui.isPopupOpen(ERROR) && !ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId)) {
            ImGui.openPopup(ERROR);
        }
        this.drawNewInstance();
        this.drawChangeRelease();
        this.drawSignIn();
        this.drawRename();
        this.drawDelete();
        this.drawError();
    }

    private boolean begin(final String name, final float width, final String title, final String subtitle) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + viewport.getWorkSizeX() * 0.5f, viewport.getWorkPosY() + viewport.getWorkSizeY() * 0.5f,
            ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowSize(px(width), 0, ImGuiCond.Always);
        float[] bg = Theme.rgba(Theme.SURFACE, 1f);
        float[] border = Theme.rgba(Theme.BORDER, 1f);
        boolean pixel = Pixel.ready();
        ImGui.pushStyleColor(ImGuiCol.PopupBg, bg[0], bg[1], bg[2], pixel ? 0f : 1f);
        ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], pixel ? 0f : 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(28), px(26));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(12), px(10));
        boolean open = ImGui.beginPopupModal(name, null, ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.AlwaysAutoResize);
        if (!open) {
            ImGui.popStyleVar(3);
            ImGui.popStyleColor(2);
            return false;
        }
        if (pixel) {
            Pixel.windowPanel();
        }
        Icons.Icon mascot = switch (name) {
            case NEW_INSTANCE, RENAME -> Icons.Icon.PENGUIN_BOX;
            case CHANGE_RELEASE -> Icons.Icon.UPDATE;
            case SIGN_IN -> Icons.Icon.PENGUIN_KEY;
            case DELETE -> Icons.Icon.PENGUIN_WARN;
            default -> Icons.Icon.PENGUIN_ALERT;
        };
        float headerX = ImGui.getCursorScreenPosX();
        float headerY = ImGui.getCursorScreenPosY();
        float headerWidth = ImGui.getContentRegionAvailX();
        float titleWidth = Math.max(px(80), headerWidth - px(88 + 46));
        float titleHeight = Widgets.textHeight(Fonts.title, title, titleWidth);
        float headerHeight = Math.max(px(72), titleHeight);
        ImDrawList header = ImGui.getWindowDrawList();
        Icons.draw(header, mascot, headerX + px(16), headerY + (headerHeight - px(72)) * 0.5f + px(18), px(48), u32(0xFFFFFF));
        Widgets.drawTextWrapped(header, Fonts.title, headerX + px(88), headerY + (headerHeight - titleHeight) * 0.5f,
            u32(Theme.TEXT), title, titleWidth);
        ImGui.setCursorScreenPos(headerX + headerWidth - px(34), headerY);
        if (Widgets.iconButton("dialog-close", Icons.Icon.CLOSE, px(34), null, true)) {
            this.onClose(name);
            ImGui.closeCurrentPopup();
        }
        ImGui.setCursorScreenPos(headerX, headerY);
        ImGui.dummy(headerWidth, headerHeight);
        if (subtitle != null) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() - px(6));
            ImGui.pushTextWrapPos(px(width) - px(56));
            Widgets.textWrapped(Fonts.body, Theme.MUTED, subtitle);
            ImGui.popTextWrapPos();
        }
        ImGui.dummy(0, px(6));
        return true;
    }

    private void end() {
        ImGui.endPopup();
        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private void onClose(final String name) {
        if (SIGN_IN.equals(name) && this.signInProgress != null) {
            this.signInProgress.cancel();
            this.signInFinished();
        }
    }

    private void footerButtons(final String cancelId, final Runnable cancel, final String primaryId, final String primaryLabel, final Icons.Icon icon,
                               final boolean enabled, final Runnable primary, final boolean danger) {
        ImGui.dummy(0, px(8));
        float primaryWidth = Widgets.textWidth(danger ? Fonts.label : Fonts.button, primaryLabel) + px(icon != null ? 17 + 9 + 36 : 36);
        float cancelWidth = Widgets.textWidth(Fonts.label, "Cancel") + px(36);
        Widgets.alignRight(primaryWidth + cancelWidth + px(10));
        if (Widgets.button(cancelId, "Cancel", null, Widgets.Variant.GHOST, cancelWidth, px(42), true)) {
            cancel.run();
        }
        ImGui.sameLine(0, px(10));
        if (Widgets.button(primaryId, primaryLabel, icon, danger ? Widgets.Variant.DANGER : Widgets.Variant.PRIMARY, primaryWidth, px(42), enabled)) {
            primary.run();
        }
    }

    private void loadListing() {
        if (this.listingLoading) {
            return;
        }
        this.listingLoading = true;
        this.listingError = null;
        this.ui.tasks.submit("Loading builds", progress -> this.ui.releases.fetchListing(), result -> {
            this.listingLoading = false;
            this.listing = result;
            this.selectedRelease = -1;
            String wanted = this.preselectId != null ? this.preselectId : result.latest();
            for (int i = 0; i < result.releases().size(); i++) {
                if (result.releases().get(i).id.equals(wanted)) {
                    this.selectedRelease = i;
                }
            }
            if (this.selectedRelease < 0 && !result.releases().isEmpty()) {
                this.selectedRelease = 0;
            }
        }, error -> {
            this.listingLoading = false;
            this.listingError = "Couldn't load the list of builds. " + error.getMessage();
        });
    }

    private Release releasePicker(final String currentId) {
        float width = ImGui.getContentRegionAvailX();
        if (this.listingLoading) {
            Widgets.progress(-1, width, px(6));
            Widgets.mascotMessage(Icons.Icon.PENGUIN_CLOCK, "Finding builds…", Theme.MUTED);
            return null;
        }
        if (this.listingError != null) {
            Widgets.mascotMessage(Icons.Icon.PENGUIN_ALERT, this.listingError, Theme.ERROR);
            if (Widgets.secondary("releases-retry", "Try again", Icons.Icon.REFRESH)) {
                this.loadListing();
            }
            return null;
        }
        if (this.listing == null || this.listing.releases().isEmpty()) {
            Widgets.mascotMessage(Icons.Icon.PENGUIN_EMPTY, "No builds have been published yet. Check back soon.", Theme.MUTED);
            return null;
        }
        float rowHeight = px(62);
        float listHeight = Math.min(px(270), this.listing.releases().size() * (rowHeight + px(6)));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, px(6));
        if (ImGui.beginChild("releases", width, listHeight, ImGuiChildFlags.None, 0)) {
            for (int i = 0; i < this.listing.releases().size(); i++) {
                Release release = this.listing.releases().get(i);
                if (this.releaseRow(i, release, currentId, rowHeight)) {
                    this.selectedRelease = i;
                }
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
        return this.selectedRelease >= 0 && this.selectedRelease < this.listing.releases().size() ? this.listing.releases().get(this.selectedRelease) : null;
    }

    private boolean releaseRow(final int index, final Release release, final String currentId, final float h) {
        String id = "release-" + index;
        float w = ImGui.getContentRegionAvailX();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        boolean selected = index == this.selectedRelease;
        float hv = Motion.hover(id + "#hover", hovered);
        float sv = Motion.to(id + "#sel", selected ? 1f : 0f, 16f);
        ImDrawList dl = ImGui.getWindowDrawList();
        Pixel.rect(dl, x, y, x + w, y + h, u32(Theme.mix(Theme.mix(Theme.SURFACE_HI, Theme.SURFACE_HOVER, hv), Theme.SURFACE_HOVER, sv)));
        Pixel.frame(dl, x, y, x + w, y + h, u32(Theme.mix(Theme.BORDER_SOFT, Theme.EMBER, sv)), px(sv > 0.5f ? 1.5f : 1f));
        float radio = px(18);
        float rx = x + px(16);
        float ry = y + (h - radio) * 0.5f;
        Pixel.frame(dl, rx + radio * 0.5f - radio * 0.5f, ry + radio * 0.5f - radio * 0.5f, rx + radio * 0.5f + radio * 0.5f, ry + radio * 0.5f + radio * 0.5f, u32(Theme.mix(Theme.FAINT, Theme.EMBER, sv)), px(1.6f));
        Pixel.dot(dl, rx + radio * 0.5f, ry + radio * 0.5f, radio * 0.26f * sv, u32(Theme.EMBER));
        float tx = rx + radio + px(14);
        Widgets.drawText(dl, Fonts.label, tx, y + px(12), u32(Theme.TEXT), release.displayName());
        String date = release.releaseTime == null || release.releaseTime.length() < 10 ? "" : Format.date(release.releaseTime);
        Widgets.drawText(dl, Fonts.small, tx, y + px(12) + Fonts.label.size() + px(4), u32(Theme.MUTED),
            "Built on Minecraft " + release.minecraft + (date.isEmpty() ? "" : "  ·  " + date));
        float pillRight = x + w - px(14);
        if (release.id.equals(currentId)) {
            float pw = Widgets.pillWidth("Current", null);
            Widgets.drawPill(dl, pillRight - pw, y + (h - px(22)) * 0.5f, "Current", Theme.MUTED, 0xFFFFFF, 0.07f, null);
            pillRight -= pw + px(6);
        }
        if (this.listing != null && release.id.equals(this.listing.latest())) {
            float pw = Widgets.pillWidth("Latest", null);
            Widgets.drawPill(dl, pillRight - pw, y + (h - px(22)) * 0.5f, "Latest", Theme.SUN, Theme.SUN, 0.13f, null);
            float penguin = px(34);
            Icons.draw(dl, Icons.Icon.PENGUIN_HAPPY, pillRight - pw - penguin * 1.3f - px(4), y + (h - penguin) * 0.5f, penguin, u32(0xFFFFFF));
        }
        return clicked;
    }

    private void drawNewInstance() {
        if (!this.begin(NEW_INSTANCE, 640, "New instance", "Pick a build and ABNW takes care of the rest.")) {
            return;
        }
        Widgets.fieldLabel("Build", null);
        Release release = this.releasePicker(null);
        ImGui.dummy(0, px(6));
        float width = ImGui.getContentRegionAvailX();
        Widgets.fieldLabel("Name", null);
        Widgets.beginField();
        ImGui.setNextItemWidth(width);
        ImGui.inputTextWithHint("##new-name", release == null ? "My world" : release.displayName(), this.newName);
        Widgets.endField();
        ImGui.dummy(0, px(4));
        float half = (width - px(16)) * 0.5f;
        ImGui.beginGroup();
        Widgets.fieldLabel("Renderer", null);
        this.newRenderer = Widgets.segmented("new-renderer", LauncherUi.RENDERER_LABELS, this.newRenderer, half);
        ImGui.endGroup();
        ImGui.sameLine(0, px(16));
        ImGui.beginGroup();
        Widgets.fieldLabel("Memory", null);
        Widgets.beginField();
        ImGui.setNextItemWidth(half);
        ImGui.sliderInt("##new-memory", this.newMemory, 2048, SettingsPage.maxMemoryMb(), "%d MB");
        Widgets.endField();
        ImGui.endGroup();

        this.footerButtons("new-cancel", ImGui::closeCurrentPopup, "new-create", "Create instance", Icons.Icon.PLUS, release != null, () -> {
            String name = this.newName.get().trim().isEmpty() ? release.displayName() : this.newName.get().trim();
            this.ui.createInstance(name, release, SettingsPage.roundTo(this.newMemory[0], 256), LauncherUi.RENDERERS[this.newRenderer]);
            ImGui.closeCurrentPopup();
        }, false);
        this.end();
    }

    private void drawChangeRelease() {
        String subtitle = this.target == null ? null : "Your worlds, mods and settings in " + this.target.name + " stay exactly where they are.";
        if (!this.begin(CHANGE_RELEASE, 620, "Change build", subtitle)) {
            return;
        }
        if (this.target == null) {
            ImGui.closeCurrentPopup();
            this.end();
            return;
        }
        Release release = this.releasePicker(this.target.release.id);
        boolean same = release != null && release.id.equals(this.target.release.id) && release.minecraft.equals(this.target.release.minecraft);
        Instance instance = this.target;
        this.footerButtons("change-cancel", ImGui::closeCurrentPopup, "change-go", "Switch build", Icons.Icon.SWAP, release != null && !same, () -> {
            this.ui.switchRelease(instance, release);
            ImGui.closeCurrentPopup();
        }, false);
        this.end();
    }

    private void drawSignIn() {
        if (!this.begin(SIGN_IN, 520, "Sign in with Microsoft", "Use the account that owns Minecraft: Java Edition or has PC Game Pass.")) {
            return;
        }
        MicrosoftAuth.DeviceCode code = this.deviceCode;
        if (code == null) {
            ImGui.closeCurrentPopup();
            this.end();
            return;
        }
        float width = ImGui.getContentRegionAvailX();
        Widgets.text(Fonts.small, Theme.MUTED, "1.  Open " + code.verificationUri().replace("https://", ""));
        Widgets.text(Fonts.small, Theme.MUTED, "2.  Enter this code");
        ImGui.dummy(0, px(4));

        ImDrawList dl = ImGui.getWindowDrawList();
        float h = px(92);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        Pixel.rect(dl, x, y, x + width, y + h, u32(Theme.BG));
        Pixel.frame(dl, x, y, x + width, y + h, u32(Theme.EMBER, 0.35f), px(1));
        String spaced = String.join(" ", code.userCode().split(""));
        float tw = Widgets.textWidth(Fonts.hero, spaced);
        Widgets.drawText(dl, Fonts.hero, x + (width - tw) * 0.5f, y + (h - Fonts.hero.size()) * 0.5f - px(2), u32(Theme.SUN), spaced);
        ImGui.dummy(width, h);
        ImGui.dummy(0, px(6));

        boolean copied = ImGui.getTime() - this.copiedAt < 2.0;
        if (Widgets.primary("signin-open", copied ? "Code copied — opening…" : "Copy code & open browser", copied ? Icons.Icon.CHECK : Icons.Icon.EXTERNAL,
            -1, px(48), true)) {
            ImGui.setClipboardText(code.userCode());
            Desktop.browse(code.verificationUri());
            this.copiedAt = ImGui.getTime();
        }
        ImGui.dummy(0, px(4));
        Progress progress = this.signInProgress;
        String status = progress == null || progress.status().isEmpty() ? "Waiting for you to finish in the browser…" : progress.status();
        Widgets.progress(-1, width, px(4));
        Widgets.text(Fonts.small, Theme.FAINT, status);
        this.end();
    }

    private void drawRename() {
        if (!this.begin(RENAME, 460, "Rename instance", null)) {
            return;
        }
        if (this.target == null) {
            ImGui.closeCurrentPopup();
            this.end();
            return;
        }
        if (ImGui.isWindowAppearing()) {
            ImGui.setKeyboardFocusHere();
        }
        Widgets.beginField();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean submitted = ImGui.inputText("##rename", this.renameText, ImGuiInputTextFlags.EnterReturnsTrue);
        Widgets.endField();
        Instance instance = this.target;
        Runnable apply = () -> {
            try {
                this.ui.instances.rename(instance, this.renameText.get());
            } catch (IOException e) {
                this.ui.fail(e);
            }
            ImGui.closeCurrentPopup();
        };
        if (submitted && !this.renameText.get().isBlank()) {
            apply.run();
        }
        this.footerButtons("rename-cancel", ImGui::closeCurrentPopup, "rename-go", "Save", Icons.Icon.CHECK, !this.renameText.get().isBlank(), apply, false);
        this.end();
    }

    private void drawDelete() {
        String subtitle = this.target == null ? null
            : this.target.name + " and everything in it — worlds, mods and settings — will be removed from this computer. Export it first if you want to keep a copy.";
        if (!this.begin(DELETE, 500, "Delete instance?", subtitle)) {
            return;
        }
        if (this.target == null) {
            ImGui.closeCurrentPopup();
            this.end();
            return;
        }
        Instance instance = this.target;
        this.footerButtons("delete-cancel", ImGui::closeCurrentPopup, "delete-go", "Delete forever", Icons.Icon.TRASH, true, () -> {
            try {
                this.ui.instances.delete(instance);
                if (instance.id.equals(this.ui.selectedInstanceId)) {
                    this.ui.selectedInstanceId = null;
                }
            } catch (IOException e) {
                this.ui.fail(e);
            }
            ImGui.closeCurrentPopup();
        }, true);
        this.end();
    }

    private void drawError() {
        if (!this.begin(ERROR, 520, "Something went wrong", null)) {
            return;
        }
        if (this.ui.errors.isEmpty()) {
            ImGui.closeCurrentPopup();
            this.end();
            return;
        }
        String message = this.ui.errors.get(0);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX());
        Widgets.textWrapped(Fonts.body, Theme.MUTED, message);
        ImGui.popTextWrapPos();
        ImGui.dummy(0, px(8));
        Widgets.alignRight(px(40) + px(10) + Widgets.textWidth(Fonts.button, "Got it") + px(36));
        if (Widgets.iconButton("error-copy", Icons.Icon.COPY, px(40), "Copy details", true)) {
            ImGui.setClipboardText(message);
        }
        ImGui.sameLine(0, px(10));
        if (Widgets.primary("error-ok", "Got it", null, 0, px(40), true)) {
            this.ui.errors.remove(0);
            if (this.ui.errors.isEmpty()) {
                ImGui.closeCurrentPopup();
            }
        }
        this.end();
    }
}
