package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;

import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.teamzetaverse.launcher.LauncherConfig;

/** Startup notice. Dismissal lasts for this process unless the user explicitly saves it. */
final class SteamMultiplayerNotice {
    private static final String POPUP = "Steam, Spacewar & Multiplayer Notice";
    private boolean acknowledged;
    private boolean understood;
    private boolean hideAgain;

    boolean isAcknowledged(final LauncherConfig config) {
        return this.acknowledged || config.hideSteamMultiplayerNotice;
    }

    void draw(final LauncherConfig config) {
        if (this.isAcknowledged(config)) return;
        if (!ImGui.isPopupOpen(POPUP)) ImGui.openPopup(POPUP);
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + viewport.getWorkSizeX() / 2,
            viewport.getWorkPosY() + viewport.getWorkSizeY() / 2, ImGuiCond.Always, .5f, .5f);
        ImGui.setNextWindowSize(Math.min(px(820), viewport.getWorkSizeX() - px(40)),
            Math.min(px(700), viewport.getWorkSizeY() - px(40)), ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, px(20), px(16));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, px(8), px(8));
        if (ImGui.beginPopupModal(POPUP, null, ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings)) {
            if (ImGui.beginChild("steam-notice-text", 0, Math.max(px(100), ImGui.getContentRegionAvailY() - px(136)), ImGuiChildFlags.None, 0)) {
                heading("Steam, Spacewar & Multiplayer Notice");
                paragraph("ABNW supports both Steam multiplayer and traditional direct-IP multiplayer.");
                paragraph("For the intended ABNW multiplayer experience, we strongly recommend installing Steam, creating a free Steam account, signing in, and making sure Steam is running before launching ABNW. Steam is currently used by ABNW for features including Steam friends, invites, multiplayer discovery/networking, and voice chat.");
                paragraph("If you cannot access or use Steam, ABNW servers can still support direct IP and port connections. However, Steam-specific functionality, including Steam friends, Steam invites, Steam discovery and ABNW's current voice-chat implementation, will not be available through that connection method.");
                link("Install Steam", "https://store.steampowered.com/about/");
                heading("Why does Steam show Spacewar?");
                paragraph("ABNW currently uses Spacewar (AppID 480) for its Steamworks integration. Spacewar is Valve's official Steamworks example application; Valve's own Steamworks documentation identifies the example application's AppID as 480 and demonstrates features including matchmaking, networking, friends and voice chat.");
                link("Steamworks example documentation", "https://partner.steamgames.com/doc/sdk/api/example");
                paragraph("Spacewar is safe and free. If Steam installs Spacewar or shows ABNW as using Spacewar, this is expected behaviour and does not mean that you have downloaded an unknown third-party game.");
                paragraph("Spacewar itself is extremely small. Its Steam depot is approximately 778.94 KiB to download and 1.82 MiB once installed (SteamDB figures checked 2 October 2026). These figures may change if Valve updates the application.");
                link("Spacewar depot sizes on SteamDB", "https://steamdb.info/app/480/depots/");
                paragraph("You do not need to purchase anything for Spacewar, and you do not normally need to launch or configure Spacewar yourself.");
                heading("Important Steam Invite Warning");
                Widgets.textWrapped(Fonts.body, Theme.SUN, "Make sure both you and your friends have fully launched ABNW before accepting a Steam invite.");
                paragraph("Your friends are encouraged to have Steam open before starting ABNW, but they should not accept an ABNW Steam invite before their copy of ABNW has finished launching and initialized Steam.");
                paragraph("If an invite is accepted too early, Steam may launch the actual Spacewar example application instead of connecting the player to ABNW.");
                paragraph("Unfortunately, this happens before ABNW is in control of the Steam connection, so ABNW cannot prevent or correct this behaviour. If it happens, close Spacewar, launch ABNW normally, wait for it to finish starting, and then send or accept the invite again.");
                heading("Terms and Services");
                paragraph("Steam-based ABNW multiplayer functionality uses Valve's Steam services. Your use of those services remains subject to the Steam Subscriber Agreement and any other applicable Valve terms and policies. Valve's own documentation describes Steam use as subject to acceptance of the Steam Subscriber Agreement.");
                link("Steam Subscriber Agreement", "https://store.steampowered.com/subscriber_agreement/");
                link("Steamworks terms documentation", "https://partner.steamgames.com/doc/marketing/branding/ssa?language=english");
                paragraph("ABNW is also a Minecraft-based project. Your use of Minecraft and Minecraft-based multiplayer remains subject to the applicable Minecraft End User License Agreement, Minecraft Usage Guidelines, Microsoft Services Agreement, and other applicable Minecraft/Microsoft terms. Mojang's current EULA states that the Minecraft EULA and Microsoft Services Agreement apply to Minecraft services.");
                link("Minecraft EULA", "https://www.minecraft.net/en-us/eula");
                link("Minecraft Usage Guidelines", "https://www.minecraft.net/en-us/usage-guidelines");
                link("Microsoft Services Agreement", "https://www.microsoft.com/servicesagreement");
                paragraph("Because ABNW voice chat currently uses Steam, use of the voice-chat service is also subject to the applicable Valve/Steam terms and policies, in addition to ABNW's own multiplayer rules.");
                paragraph("ABNW is an independent project and is not affiliated with, sponsored by, or endorsed by Valve Corporation, Mojang Studios, or Microsoft.");
            }
            ImGui.endChild();
            ImGui.separator();
            paragraph("Before continuing, please confirm that you understand the above information.");
            if (ImGui.checkbox("I understand", this.understood)) this.understood = !this.understood;
            if (ImGui.checkbox("Don't show this notice again", this.hideAgain)) this.hideAgain = !this.hideAgain;
            if (Widgets.primary("steam-notice-continue", "Continue", Icons.Icon.CHECK, 0, px(36), this.understood)) {
                this.acknowledged = true;
                config.hideSteamMultiplayerNotice = this.hideAgain;
                config.save();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
        ImGui.popStyleVar(2);
    }

    private static void heading(final String text) {
        ImGui.dummy(0, px(4));
        Widgets.textWrapped(Fonts.heading, Theme.TEXT, text);
    }

    private static void paragraph(final String text) {
        Widgets.textWrapped(Fonts.body, Theme.TEXT, text);
    }

    private static void link(final String label, final String url) {
        if (Widgets.secondary("steam-notice-link-" + label, label, Icons.Icon.EXTERNAL)) Desktop.browse(url);
    }
}
