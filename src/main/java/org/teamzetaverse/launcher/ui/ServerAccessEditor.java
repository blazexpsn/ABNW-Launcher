package org.teamzetaverse.launcher.ui;

import static org.teamzetaverse.launcher.ui.Theme.px;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.server.PlayerProfiles;
import org.teamzetaverse.launcher.server.ServerAdministration;
import org.teamzetaverse.launcher.util.Json;

/** Guided editors for the game's operator, group and per-player permission formats. */
final class ServerAccessEditor {
    private final LauncherUi ui;
    private String bound;
    private String error;
    private String status;
    private boolean loading;
    private boolean lookup;
    private long generation;
    private JsonArray operators = new JsonArray();
    private JsonObject players = new JsonObject();
    private Map<String, JsonObject> groups = new LinkedHashMap<>();
    private JsonArray catalog = new JsonArray();
    private final ImString username = new ImString(64);
    private PlayerProfiles.Profile profile;
    private final int[] level = {4};
    private boolean bypass;
    private String selectedGroup;
    private final ImString groupId = new ImString(256);
    private final ImString parents = new ImString(4096);
    private final ImString nodes = new ImString(32768);
    private final ImInt weight = new ImInt(0);
    private final ImInt applies = new ImInt(0);
    private final ImString playerGroups = new ImString(4096);
    private final ImString playerNodes = new ImString(32768);
    private final ImString search = new ImString(256);
    private boolean deny;
    private int permissionTab;
    private static final String[] APPLIES = {"Assigned players", "Everyone", "Operator level 1+", "Operator level 2+", "Operator level 3+", "Operator level 4"};

    ServerAccessEditor(final LauncherUi ui) { this.ui = ui; }

    private Path gameJar(final Instance instance) {
        return instance.release.isResolved() ? this.ui.paths.patchedJars().resolve(instance.release.patchedJarName()) : null;
    }

    void invalidate() { this.bound = null; this.generation++; }

    private void bind(final Instance instance) {
        String key = instance.id + ":" + instance.release.id;
        if (key.equals(this.bound)) return;
        this.bound = key;
        this.username.set(""); this.profile = null; this.lookup = false;
        this.selectedGroup = null; this.groupId.set(""); this.parents.set(""); this.nodes.set("");
        this.playerGroups.set(""); this.playerNodes.set(""); this.error = null; this.status = null;
        this.loading = false;
        long request = ++this.generation;
        try {
            this.operators = ServerAdministration.operators(instance);
            this.players = ServerAdministration.players(instance);
            this.groups = ServerAdministration.groups(instance, this.gameJar(instance));
            this.catalog = ServerAdministration.catalog(instance).getAsJsonArray("nodes");
            this.loading = true;
            if (!this.groups.isEmpty()) this.chooseGroup(this.groups.keySet().iterator().next());
        } catch (Exception e) { if (request == this.generation) this.error = e.getMessage(); }
    }

    void draw(final Instance instance, final boolean locked, final boolean permissions) {
        this.bind(instance);
        if (locked) Widgets.textWrapped(Fonts.label, Theme.MUTED, "Stop the server and wait for its tasks to finish to edit access. Restart after saving to apply changes.");
        if (Widgets.secondary("access-reload", "Reload files", Icons.Icon.REFRESH)) { this.invalidate(); this.bind(instance); }
        if (this.error != null) Widgets.textWrapped(Fonts.body, Theme.SUN, this.error);
        if (this.status != null) Widgets.textWrapped(Fonts.label, Theme.OK, this.status);
        if (!this.loading) return;
        ImGui.beginDisabled(locked || this.lookup);
        if (permissions) {
            this.permissionTab = Widgets.segmented("permission-mode", new String[]{"Groups", "Players"}, this.permissionTab, Math.min(px(340), ImGui.getContentRegionAvailX()));
            ImGui.dummy(0, px(8));
            if (this.permissionTab == 0) this.drawGroups(instance);
            else this.drawPlayers(instance);
        } else this.drawOperators(instance);
        ImGui.endDisabled();
    }

    private void lookupPlayer(final Instance instance) {
        this.lookup = true; this.profile = null; this.error = null;
        String name = this.username.get().trim();
        long request = this.generation;
        this.ui.tasks.submit("Looking up a player for " + instance.name, progress -> PlayerProfiles.lookup(name), found -> {
            if (request != this.generation) return;
            this.lookup = false; this.useProfile(found);
        }, failure -> {
            if (request != this.generation) return;
            this.lookup = false; this.error = failure.getMessage();
        });
    }

    private void useProfile(final PlayerProfiles.Profile profile) {
        this.profile = profile; this.username.set(profile.name());
        JsonObject entry = this.players.getAsJsonObject("players").getAsJsonObject(profile.uuid().toString());
        this.playerGroups.set(entry == null ? "" : String.join("\n", ServerAdministration.strings(entry.getAsJsonArray("groups"))));
        this.playerNodes.set(entry == null ? "" : String.join("\n", ServerAdministration.strings(entry.getAsJsonArray("permissions"))));
    }

    private void drawLookup(final Instance instance) {
        Widgets.fieldLabel("Minecraft username", "Looks up the account's current username and UUID.");
        ImGui.setNextItemWidth(Math.max(px(120), ImGui.getContentRegionAvailX() - px(130)));
        if (ImGui.inputText("##access-name", this.username)) this.profile = null;
        ImGui.sameLine();
        if (Widgets.button("access-lookup", this.lookup ? "Looking up…" : "Look up", Icons.Icon.USER,
            Widgets.Variant.SECONDARY, 0, px(32), !this.username.get().isBlank() && !this.lookup)) this.lookupPlayer(instance);
        if (this.profile != null) Widgets.textWrapped(Fonts.label, Theme.OK, this.profile.name() + " · " + this.profile.uuid());
        Widgets.textWrapped(Fonts.label, Theme.MUTED, "Account UUIDs apply to authenticated players. Offline servers may use different UUIDs; select an existing player entry in that case.");
        ImGui.dummy(0, px(6));
    }

    private void drawOperators(final Instance instance) {
        this.drawLookup(instance);
        Widgets.fieldLabel("Operator level", "1: moderation · 2: game commands · 3: administration · 4: owner");
        ImGui.sliderInt("##op-level", this.level, 1, 4);
        if (ImGui.checkbox("Bypass the player limit", this.bypass)) this.bypass = !this.bypass;
        if (Widgets.button("op-save", "Add / update operator", Icons.Icon.CHECK, Widgets.Variant.PRIMARY, 0, px(36), this.profile != null)) {
            this.attempt(() -> {
                JsonArray updated = this.operators.deepCopy();
                ServerAdministration.putOperator(updated, this.profile, this.level[0], this.bypass);
                ServerAdministration.saveOperators(instance, updated); this.operators = updated;
            }, "Saved ops.json. Operators take effect on the next server start.");
        }
        ImGui.separator();
        if (this.operators.isEmpty()) Widgets.text(Fonts.label, Theme.MUTED, "No operators yet.");
        for (int i = 0; i < this.operators.size(); i++) {
            JsonObject entry = this.operators.get(i).getAsJsonObject();
            ImGui.pushID("op-" + i);
            Widgets.textWrapped(Fonts.body, Theme.TEXT, Json.string(entry, "name") + " · level " + Json.number(entry, "level", 4));
            Widgets.text(Fonts.label, Theme.MUTED, Json.string(entry, "uuid"));
            if (Widgets.secondary("edit", "Edit", Icons.Icon.EDIT)) {
                this.useProfile(new PlayerProfiles.Profile(UUID.fromString(Json.string(entry, "uuid")), Json.string(entry, "name")));
                this.level[0] = (int) Json.number(entry, "level", 4);
                this.bypass = entry.has("bypassesPlayerLimit") && entry.get("bypassesPlayerLimit").getAsBoolean();
            }
            ImGui.sameLine();
            final int index = i;
            if (Widgets.secondary("remove", "Remove operator", Icons.Icon.TRASH)) this.attempt(() -> {
                JsonArray updated = this.operators.deepCopy(); updated.remove(index);
                ServerAdministration.saveOperators(instance, updated); this.operators = updated;
            }, "Removed operator from ops.json.");
            ImGui.popID();
        }
    }

    private void chooseGroup(final String id) {
        this.selectedGroup = id; this.groupId.set(id);
        JsonObject group = this.groups.get(id);
        this.parents.set(String.join("\n", ServerAdministration.strings(group.getAsJsonArray("inherits"))));
        this.nodes.set(String.join("\n", ServerAdministration.strings(group.getAsJsonArray("permissions"))));
        this.weight.set((int) Json.number(group, "weight", 0));
        JsonObject to = group.getAsJsonObject("applies_to"); this.applies.set(0);
        if (to != null && to.has("everyone") && to.get("everyone").getAsBoolean()) this.applies.set(1);
        else if (to != null && to.has("op_level")) {
            String value = to.get("op_level").getAsString();
            this.applies.set(switch (value) { case "1", "moderators" -> 2; case "2", "gamemasters" -> 3;
                case "3", "admins" -> 4; case "4", "owners" -> 5; default -> 0; });
        }
    }

    private void drawGroups(final Instance instance) {
        Widgets.textWrapped(Fonts.label, Theme.MUTED, "Groups are saved into the world's datapacks/" + ServerAdministration.PACK_FILE + ". Higher weights win when groups disagree.");
        if (ImGui.beginCombo("Group", this.selectedGroup == null ? "New group" : this.selectedGroup)) {
            for (String id : this.groups.keySet()) if (ImGui.selectable(id, id.equals(this.selectedGroup))) this.chooseGroup(id);
            ImGui.endCombo();
        }
        if (Widgets.secondary("group-new", "New group", Icons.Icon.PLUS)) {
            this.selectedGroup = null; this.groupId.set("myserver:builder"); this.weight.set(10);
            this.parents.set("minecraft:default"); this.nodes.set(""); this.applies.set(0);
        }
        Widgets.fieldLabel("Group id", "namespace:name, for example myserver:builder");
        ImGui.beginDisabled(this.selectedGroup != null);
        ImGui.inputText("##group-id", this.groupId); ImGui.endDisabled();
        ImGui.inputInt("Weight", this.weight);
        ImGui.combo("Automatically includes", this.applies, APPLIES);
        Widgets.fieldLabel("Inherits", "One group id per line. Groups from other datapacks can be entered here.");
        ImGui.inputTextMultiline("##group-parents", this.parents, -1, px(65));
        this.drawNodes(this.nodes, "group");
        if (Widgets.primary("group-save", "Save group datapack", Icons.Icon.CHECK, 0, px(38), !this.groupId.get().isBlank())) this.attempt(() -> {
            String id = this.groupId.get().trim();
            Map<String, JsonObject> updated = new LinkedHashMap<>(this.groups);
            updated.put(ServerAdministration.groupId(id), ServerAdministration.group(ServerAdministration.lines(this.parents.get()),
                this.weight.get(), this.applies.get(), ServerAdministration.lines(this.nodes.get())));
            ServerAdministration.saveGroups(instance, this.gameJar(instance), updated);
            this.groups = updated; this.selectedGroup = id;
        }, "Saved group datapack. Restart the server to load it.");
        if (this.selectedGroup != null && !this.selectedGroup.startsWith("minecraft:")) {
            ImGui.sameLine();
            if (Widgets.secondary("group-remove", "Delete group", Icons.Icon.TRASH)) this.attempt(() -> {
                for (JsonObject group : this.groups.values()) if (ServerAdministration.strings(group.getAsJsonArray("inherits")).contains(this.selectedGroup))
                    throw new IllegalArgumentException("Remove this group from other groups' inheritance first.");
                for (JsonElement player : this.players.getAsJsonObject("players").asMap().values())
                    if (ServerAdministration.strings(player.getAsJsonObject().getAsJsonArray("groups")).contains(this.selectedGroup))
                        throw new IllegalArgumentException("Remove this group from its players first.");
                Map<String, JsonObject> updated = new LinkedHashMap<>(this.groups); updated.remove(this.selectedGroup);
                ServerAdministration.saveGroups(instance, this.gameJar(instance), updated); this.groups = updated;
                this.selectedGroup = null; this.groupId.set("");
            }, "Deleted group from the managed datapack.");
        }
    }

    private void drawPlayers(final Instance instance) {
        if (ImGui.beginCombo("Existing player", this.profile == null ? "Choose a player" : this.profile.name())) {
            for (var entry : this.players.getAsJsonObject("players").entrySet()) {
                String name = Json.string(entry.getValue().getAsJsonObject(), "name");
                if (ImGui.selectable((name == null ? entry.getKey() : name) + "##" + entry.getKey()))
                    this.useProfile(new PlayerProfiles.Profile(UUID.fromString(entry.getKey()), name == null ? entry.getKey() : name));
            }
            ImGui.endCombo();
        }
        this.drawLookup(instance);
        Widgets.fieldLabel("Memberships", "One group id per line; choose from your groups or enter ids from other datapacks.");
        if (ImGui.beginCombo("Add group", "Choose a group")) {
            for (String id : this.groups.keySet()) if (ImGui.selectable(id)) this.append(this.playerGroups, id);
            ImGui.endCombo();
        }
        ImGui.inputTextMultiline("##player-groups", this.playerGroups, -1, px(80));
        this.drawNodes(this.playerNodes, "player");
        if (Widgets.primary("player-save", "Save player permissions", Icons.Icon.CHECK, 0, px(38), this.profile != null)) this.attempt(() -> {
            JsonObject updated = this.players.deepCopy();
            ServerAdministration.putPlayer(updated, this.profile, ServerAdministration.lines(this.playerGroups.get()), ServerAdministration.lines(this.playerNodes.get()));
            ServerAdministration.savePlayers(instance, updated); this.players = updated;
        }, "Saved memberships and overrides to the world's permissions.json.");
        if (Widgets.button("player-clear", "Clear memberships and overrides", Icons.Icon.TRASH, Widgets.Variant.SECONDARY, 0, px(36), this.profile != null)) this.attempt(() -> {
            JsonObject updated = this.players.deepCopy();
            ServerAdministration.putPlayer(updated, this.profile, List.of(), List.of());
            ServerAdministration.savePlayers(instance, updated); this.players = updated;
            this.playerGroups.set(""); this.playerNodes.set("");
        }, "Cleared explicit memberships and overrides. Automatic groups still apply.");
    }

    private void drawNodes(final ImString target, final String id) {
        ImGui.pushID(id);
        Widgets.fieldLabel("Permission nodes", "One per line. Prefix - to deny. Wildcards and pasted custom nodes are supported.");
        ImGui.inputTextMultiline("##nodes", target, -1, px(120));
        ImGui.inputTextWithHint("##node-search", "Search available nodes…", this.search);
        if (ImGui.checkbox("Add selected node as a deny", this.deny)) this.deny = !this.deny;
        if (ImGui.beginChild("node-catalog", 0, px(130))) {
            String filter = this.search.get().trim().toLowerCase(java.util.Locale.ROOT);
            for (JsonElement element : this.catalog) {
                JsonObject entry = element.getAsJsonObject(); String node = Json.string(entry, "node");
                String description = Json.string(entry, "description");
                if (!node.contains(filter) && (description == null || !description.toLowerCase(java.util.Locale.ROOT).contains(filter))) continue;
                if (ImGui.selectable(node)) this.append(target, (this.deny ? "-" : "") + node);
                if (ImGui.isItemHovered() && description != null) ImGui.setTooltip(description);
            }
        }
        ImGui.endChild(); ImGui.popID();
    }

    private void append(final ImString target, final String value) {
        List<String> entries = ServerAdministration.lines(target.get());
        if (!entries.contains(value)) target.set(target.get().strip() + (target.get().isBlank() ? "" : "\n") + value);
    }

    private void attempt(final Edit edit, final String status) {
        try { edit.run(); this.error = null; this.status = status; }
        catch (Exception e) { this.error = e.getMessage(); this.status = null; }
    }

    @FunctionalInterface private interface Edit { void run() throws Exception; }
}
