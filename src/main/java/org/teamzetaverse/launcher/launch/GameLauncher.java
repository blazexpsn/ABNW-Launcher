package org.teamzetaverse.launcher.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.teamzetaverse.launcher.BuildInfo;
import org.teamzetaverse.launcher.LauncherConfig;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.auth.Account;
import org.teamzetaverse.launcher.instance.Instance;
import org.teamzetaverse.launcher.minecraft.InstalledGame;
import org.teamzetaverse.launcher.minecraft.Rules;
import org.teamzetaverse.launcher.util.Json;

public final class GameLauncher {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");

    private final LauncherPaths paths;
    private final LauncherConfig config;

    public GameLauncher(final LauncherPaths paths, final LauncherConfig config) {
        this.paths = paths;
        this.config = config;
    }

    public GameProcess launch(final Instance instance, final InstalledGame game, final Account account, final Consumer<GameProcess> onExit)
        throws IOException {
        JsonObject version = game.versionJson();
        Files.createDirectories(instance.gameFolder());
        Files.createDirectories(instance.nativesFolder());

        Map<String, String> tokens = new HashMap<>();
        tokens.put("auth_player_name", account.name);
        tokens.put("version_name", instance.release.displayName());
        tokens.put("game_directory", instance.gameFolder().toString());
        tokens.put("assets_root", game.assetsDir().toString());
        tokens.put("game_assets", game.assetsDir().toString());
        tokens.put("assets_index_name", game.assetIndexId());
        tokens.put("auth_uuid", account.uuid);
        tokens.put("auth_access_token", account.devOffline ? "0" : account.minecraftToken);
        tokens.put("auth_session", account.devOffline ? "0" : account.minecraftToken);
        tokens.put("clientid", this.config.effectiveClientId());
        tokens.put("auth_xuid", account.devOffline ? "0" : account.xuid);
        tokens.put("user_type", account.devOffline ? "legacy" : "msa");
        tokens.put("user_properties", "{}");
        tokens.put("version_type", "ABNW");
        tokens.put("natives_directory", instance.nativesFolder().toString());
        tokens.put("launcher_name", "ABNWLauncher");
        tokens.put("launcher_version", BuildInfo.VERSION);
        tokens.put("classpath", game.classpath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        tokens.put("classpath_separator", File.pathSeparator);
        tokens.put("library_directory", game.librariesDir().toString());

        Map<String, Boolean> features = Map.of();

        List<String> command = new ArrayList<>();
        command.add(game.javaExecutable().toString());

        int memory = instance.memoryMb > 0 ? instance.memoryMb : this.config.defaultMemoryMb;
        command.add("-Xms" + Math.min(512, memory) + "m");
        command.add("-Xmx" + memory + "m");

        String renderer = switch (String.valueOf(instance.renderer)) {
            case "vulkan", "opengl" -> instance.renderer;
            default -> "auto";
        };
        command.add("-Dabnw.renderer=" + renderer);
        command.add("-Dabnw.renderer.validation=false");

        if (game.loggingArgument() != null) {
            command.add(game.loggingArgument());
        }
        if (instance.extraJvmArgs != null && !instance.extraJvmArgs.isBlank()) {
            try {
                command.addAll(org.teamzetaverse.launcher.util.CommandLine.split(instance.extraJvmArgs));
            } catch (IllegalArgumentException e) {
                throw new IOException("The extra Java arguments for " + instance.name + " are invalid: " + e.getMessage());
            }
        }

        JsonObject arguments = Json.object(version, "arguments");
        if (arguments != null) {
            appendTemplate(command, arguments.getAsJsonArray("jvm"), tokens, features);
            command.add(Json.string(version, "mainClass"));
            appendTemplate(command, arguments.getAsJsonArray("game"), tokens, features);
        } else {
            command.add("-Djava.library.path=" + instance.nativesFolder());
            command.add("-cp");
            command.add(tokens.get("classpath"));
            command.add(Json.string(version, "mainClass"));
            String legacy = Json.string(version, "minecraftArguments");
            if (legacy != null) {
                for (String part : legacy.split(" ")) {
                    command.add(substitute(part, tokens));
                }
            }
        }

        ProcessBuilder builder = new ProcessBuilder(command).directory(instance.gameFolder().toFile()).redirectErrorStream(true);
        steamOverlayEnvironment(builder.environment());
        Path log = this.paths.logs().resolve(instance.id + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log");
        Process process = builder.start();
        GameProcess running = new GameProcess(instance.id, process, log, onExit);
        running.append("[launcher] Starting " + instance.name + " (" + instance.release.displayName() + ", renderer " + renderer + ")");
        running.append("[launcher] " + redact(command, account));
        return running;
    }

    private static void steamOverlayEnvironment(final Map<String, String> environment) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            return;
        }
        environment.put("SteamAppId", "480");
        environment.put("SteamGameId", "480");
        environment.put("SteamOverlayGameId", "480");
        environment.put("ENABLE_VK_LAYER_VALVE_steam_overlay_1", "1");
        if (!os.contains("linux")) {
            return;
        }
        Path home = Path.of(System.getProperty("user.home"));
        for (Path steam : List.of(
            home.resolve(".steam/steam"),
            home.resolve(".local/share/Steam"),
            home.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam"))) {
            Path renderer = steam.resolve("ubuntu12_64/gameoverlayrenderer.so");
            if (Files.isRegularFile(renderer)) {
                String existing = environment.get("LD_PRELOAD");
                environment.put("LD_PRELOAD", existing == null || existing.isBlank() ? renderer.toString() : existing + ":" + renderer);
                return;
            }
        }
    }

    private static void appendTemplate(final List<String> command, final JsonArray template, final Map<String, String> tokens, final Map<String, Boolean> features) {
        if (template == null) {
            return;
        }
        for (JsonElement element : template) {
            if (element.isJsonPrimitive()) {
                command.add(substitute(element.getAsString(), tokens));
                continue;
            }
            JsonObject conditional = element.getAsJsonObject();
            if (!Rules.allowed(conditional.getAsJsonArray("rules"), features)) {
                continue;
            }
            JsonElement value = conditional.get("value");
            if (value == null) {
                continue;
            }
            if (value.isJsonArray()) {
                for (JsonElement part : value.getAsJsonArray()) {
                    command.add(substitute(part.getAsString(), tokens));
                }
            } else {
                command.add(substitute(value.getAsString(), tokens));
            }
        }
    }

    private static String substitute(final String text, final Map<String, String> tokens) {
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = tokens.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String redact(final List<String> command, final Account account) {
        String joined = String.join(" ", command);
        return account.minecraftToken.isEmpty() ? joined : joined.replace(account.minecraftToken, "<access token>");
    }
}
