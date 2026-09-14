package org.teamzetaverse.launcher.instance;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.release.ReleaseService;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Json;

public final class InstanceArchive {
    private static final Set<String> SKIPPED = Set.of("logs", "crash-reports", "debug", ".fabric", "natives");
    private static final long MAX_UNPACKED = 8L * 1024 * 1024 * 1024;

    private InstanceArchive() {
    }

    public static void export(final Instance instance, final Path target, final Progress progress) throws IOException, Progress.CancelledException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(instance.gameFolder())) {
            walk.filter(Files::isRegularFile).filter(path -> {
                Path relative = instance.gameFolder().relativize(path);
                return relative.getNameCount() == 0 || !SKIPPED.contains(relative.getName(0).toString());
            }).forEach(files::add);
        }
        progress.stage("Exporting " + instance.name, files.size() + 1);
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            zip.putNextEntry(new ZipEntry(Instance.FILE));
            zip.write(Json.GSON.toJson(exportCopy(instance)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            progress.advance(1);
            for (Path file : files) {
                progress.checkCancelled();
                String name = Instance.GAME_FOLDER + "/" + instance.gameFolder().relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(file, zip);
                zip.closeEntry();
                progress.advance(1);
            }
        } catch (IOException | Progress.CancelledException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Instance exportCopy(final Instance instance) {
        Instance copy = new Instance();
        copy.name = instance.name;
        copy.release = instance.release;
        copy.renderer = instance.renderer;
        copy.memoryMb = instance.memoryMb;
        copy.extraJvmArgs = instance.extraJvmArgs;
        return copy;
    }

    public static Instance peek(final Path archive) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(Instance.FILE)) {
                    Instance instance = Json.GSON.fromJson(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), Instance.class);
                    if (instance == null || instance.release == null) {
                        break;
                    }
                    ReleaseService.checkListed(instance.release);
                    return instance;
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("This is not a valid .abnw file: " + e.getMessage(), e);
        }
        throw new IOException("This is not an ABNW instance export (it has no instance.json).");
    }

    public static Instance importArchive(final Path archive, final InstanceStore store, final ReleaseService.Resolved resolved, final Progress progress)
        throws IOException, Progress.CancelledException {
        Instance described = peek(archive);
        Release release = resolved.release();
        Path folder = store.newFolder(described.name);
        Instance instance = null;
        try {
            Path game = folder.resolve(Instance.GAME_FOLDER);
            Files.createDirectories(game);
            progress.stage("Importing " + described.name, 0);
            long unpacked = 0;
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    progress.checkCancelled();
                    if (entry.isDirectory() || !entry.getName().startsWith(Instance.GAME_FOLDER + "/")) {
                        continue;
                    }
                    Path target = folder.resolve(entry.getName()).normalize();
                    if (!target.startsWith(game)) {
                        throw new IOException("The archive tries to write outside its instance: " + entry.getName());
                    }
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[1 << 16];
                        int read;
                        while ((read = zip.read(buffer)) > 0) {
                            unpacked += read;
                            if (unpacked > MAX_UNPACKED) {
                                throw new IOException("The archive unpacks to more than 8 GB.");
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
            instance = new Instance();
            instance.id = folder.getFileName().toString();
            instance.name = described.name == null || described.name.isBlank() ? release.displayName() : described.name;
            instance.release = release;
            instance.renderer = described.renderer == null ? "auto" : described.renderer;
            instance.memoryMb = Math.max(0, described.memoryMb);
            instance.extraJvmArgs = described.extraJvmArgs == null ? "" : described.extraJvmArgs;
            instance.created = System.currentTimeMillis();
            instance.folder(folder);
            Files.createDirectories(instance.modsFolder());
            Files.writeString(instance.librariesFile(), resolved.librariesJson(), java.nio.charset.StandardCharsets.UTF_8);
            store.save(instance);
            store.register(instance);
            return instance;
        } catch (IOException | Progress.CancelledException | RuntimeException e) {
            InstanceStore.deleteRecursively(folder);
            throw e;
        }
    }
}
