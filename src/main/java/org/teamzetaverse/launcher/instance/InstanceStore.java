package org.teamzetaverse.launcher.instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.teamzetaverse.launcher.LauncherPaths;
import org.teamzetaverse.launcher.release.ReleaseService;
import org.teamzetaverse.launcher.util.Json;

public final class InstanceStore {
    private final LauncherPaths paths;
    private final List<Instance> instances = new CopyOnWriteArrayList<>();

    public InstanceStore(final LauncherPaths paths) {
        this.paths = paths;
        this.reload();
    }

    public void reload() {
        this.instances.clear();
        Path root = this.paths.instances();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path folder : folders) {
                Path file = folder.resolve(Instance.FILE);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    Instance instance = Json.read(file, Instance.class);
                    instance.id = folder.getFileName().toString();
                    instance.folder(folder);
                    if (instance.icon == null) {
                        instance.icon = Instance.ICON_PENGUIN;
                    }
                    if (instance.iconFile == null) {
                        instance.iconFile = "";
                    }
                    if (instance.penguin == null) {
                        instance.penguin = new java.util.LinkedHashMap<>();
                    }
                    if (instance.kind == null || instance.kind.isBlank()) {
                        instance.kind = Instance.KIND_CLIENT;
                    }
                    this.instances.add(instance);
                } catch (IOException e) {
                    System.err.println("Skipping instance " + folder.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list instances: " + e.getMessage());
        }
        this.instances.sort(Comparator.comparing((Instance i) -> i.name.toLowerCase(Locale.ROOT)));
        this.assignMissingServerPorts();
    }

    public List<Instance> all() {
        return this.instances;
    }

    public List<Instance> clients() {
        return this.instances.stream().filter(i -> !i.isServer()).toList();
    }

    public List<Instance> servers() {
        return this.instances.stream().filter(Instance::isServer).toList();
    }

    public int nextServerPort() {
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        for (Instance instance : this.instances) {
            if (instance.isServer() && instance.serverPort > 0) {
                taken.add(instance.serverPort);
            }
        }
        int port = Instance.DEFAULT_SERVER_PORT;
        while (taken.contains(port) && port < 65535) {
            port++;
        }
        return port;
    }

    private void assignMissingServerPorts() {
        for (Instance instance : this.instances) {
            if (!instance.isServer() || instance.serverPort > 0) {
                continue;
            }
            instance.serverPort = this.nextServerPort();
            try {
                this.save(instance);
            } catch (IOException e) {
                System.err.println("Could not give " + instance.name + " its own port: " + e.getMessage());
            }
        }
    }

    public Optional<Instance> find(final String id) {
        return this.instances.stream().filter(i -> i.id.equals(id)).findFirst();
    }

    public Instance create(final String name, final ReleaseService.Resolved resolved, final int memoryMb, final String renderer) throws IOException {
        return this.create(name, resolved, memoryMb, renderer, Instance.KIND_CLIENT);
    }

    public Instance create(final String name, final ReleaseService.Resolved resolved, final int memoryMb, final String renderer, final String kind) throws IOException {
        Path folder = this.newFolder(name);
        Instance instance = new Instance();
        instance.kind = Instance.KIND_SERVER.equals(kind) ? Instance.KIND_SERVER : Instance.KIND_CLIENT;
        if (instance.isServer()) {
            instance.serverPort = this.nextServerPort();
        }
        instance.id = folder.getFileName().toString();
        instance.name = name.isBlank() ? resolved.release().displayName() : name.trim();
        instance.release = resolved.release();
        instance.memoryMb = memoryMb;
        instance.renderer = renderer;
        instance.created = System.currentTimeMillis();
        instance.folder(folder);
        Files.createDirectories(instance.gameFolder());
        Files.createDirectories(instance.modsFolder());
        Files.writeString(instance.librariesFile(), resolved.librariesJson(), StandardCharsets.UTF_8);
        this.save(instance);
        this.instances.add(instance);
        this.instances.sort(Comparator.comparing((Instance i) -> i.name.toLowerCase(Locale.ROOT)));
        return instance;
    }

    public void setRelease(final Instance instance, final ReleaseService.Resolved resolved) throws IOException {
        instance.release = resolved.release();
        Files.writeString(instance.librariesFile(), resolved.librariesJson(), StandardCharsets.UTF_8);
        this.save(instance);
    }

    public void save(final Instance instance) throws IOException {
        Json.write(instance.folder().resolve(Instance.FILE), instance);
    }

    public void rename(final Instance instance, final String name) throws IOException {
        if (name == null || name.isBlank()) {
            return;
        }
        instance.name = name.trim();
        this.save(instance);
        this.instances.sort(Comparator.comparing((Instance i) -> i.name.toLowerCase(Locale.ROOT)));
    }

    public void delete(final Instance instance) throws IOException {
        this.instances.remove(instance);
        deleteRecursively(instance.folder());
    }

    public String readLibraries(final Instance instance) throws IOException {
        return Files.readString(instance.librariesFile(), StandardCharsets.UTF_8);
    }

    Path newFolder(final String name) throws IOException {
        String base = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9 ._-]", "").replace(' ', '-');
        if (base.isEmpty()) {
            base = "instance";
        }
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        Files.createDirectories(this.paths.instances());
        Path folder = this.paths.instances().resolve(base);
        for (int n = 2; Files.exists(folder); n++) {
            folder = this.paths.instances().resolve(base + "-" + n);
        }
        Files.createDirectories(folder);
        return folder;
    }

    void register(final Instance instance) {
        this.instances.add(instance);
        this.instances.sort(Comparator.comparing((Instance i) -> i.name.toLowerCase(Locale.ROOT)));
    }

    static void deleteRecursively(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void validate(final Instance instance) throws IOException {
        ReleaseService.checkListed(instance.release);
    }
}
