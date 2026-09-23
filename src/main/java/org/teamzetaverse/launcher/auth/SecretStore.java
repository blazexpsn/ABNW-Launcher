package org.teamzetaverse.launcher.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.teamzetaverse.launcher.util.FileMoves;
import org.teamzetaverse.launcher.util.OperatingSystem;

abstract class SecretStore {
    private static final String SERVICE = "ABNWLauncher";
    private static final String ACCOUNT = "accounts";

    abstract String load() throws IOException;

    abstract void save(String secret) throws IOException;

    abstract String describe();

    static SecretStore platform(final Path protectedFile) {
        return platform(protectedFile, ACCOUNT);
    }

    static SecretStore platform(final Path protectedFile, final String account) {
        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> new WindowsDataProtection(protectedFile);
            case MACOS -> new MacKeychain(account);
            case LINUX -> new LinuxSecretService(account);
        };
    }

    static SecretStore ownerOnlyFile(final Path file) {
        return new OwnerOnlyFile(file);
    }

    private static String encode(final String secret) {
        return Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(final String encoded) throws IOException {
        try {
            return new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("stored secret is corrupt", e);
        }
    }

    private record Result(int exitCode, String stdout) {
    }

    private static Result run(final List<String> command, final String stdin) throws IOException {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        String output;
        try (InputStream out = process.getInputStream()) {
            output = new String(out.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8);
        }
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.get(0) + " did not finish");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running " + command.get(0), e);
        }
        return new Result(process.exitValue(), output);
    }

    private static final class WindowsDataProtection extends SecretStore {
        private static final String PROTECT = "Add-Type -AssemblyName System.Security;"
            + "$d=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());"
            + "$p=[Security.Cryptography.ProtectedData]::Protect($d,[Text.Encoding]::UTF8.GetBytes('ABNWLauncher'),'CurrentUser');"
            + "[Console]::Out.Write([Convert]::ToBase64String($p))";
        private static final String UNPROTECT = "Add-Type -AssemblyName System.Security;"
            + "$d=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());"
            + "$p=[Security.Cryptography.ProtectedData]::Unprotect($d,[Text.Encoding]::UTF8.GetBytes('ABNWLauncher'),'CurrentUser');"
            + "[Console]::Out.Write([Convert]::ToBase64String($p))";

        private final Path file;

        private WindowsDataProtection(final Path file) {
            this.file = file;
        }

        private static List<String> powershell(final String script) {
            return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script);
        }

        @Override
        String load() throws IOException {
            if (!Files.isRegularFile(this.file)) {
                return null;
            }
            Result result = run(powershell(UNPROTECT), Files.readString(this.file, StandardCharsets.US_ASCII));
            if (result.exitCode() != 0 || result.stdout().isBlank()) {
                throw new IOException("Windows could not decrypt the saved accounts (were they saved by another Windows user?)");
            }
            return decode(result.stdout());
        }

        @Override
        void save(final String secret) throws IOException {
            Result result = run(powershell(PROTECT), encode(secret));
            if (result.exitCode() != 0 || result.stdout().isBlank()) {
                throw new IOException("Windows data protection is unavailable");
            }
            Files.createDirectories(this.file.toAbsolutePath().getParent());
            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.writeString(temp, result.stdout().trim(), StandardCharsets.US_ASCII);
            FileMoves.replace(temp, this.file);
        }

        @Override
        String describe() {
            return "Windows data protection";
        }
    }

    private static final class MacKeychain extends SecretStore {
        private final String account;
        private MacKeychain(String account) { this.account = account; }
        @Override
        String load() throws IOException {
            Result result = run(List.of("security", "find-generic-password", "-a", this.account, "-s", SERVICE, "-w"), null);
            if (result.exitCode() == 44) {
                return null;
            }
            if (result.exitCode() != 0) {
                throw new IOException("the macOS keychain refused to return the saved accounts");
            }
            return decode(result.stdout());
        }

        @Override
        void save(final String secret) throws IOException {
            String command = "add-generic-password -U -a " + this.account + " -s " + SERVICE + " -l \"ABNW Launcher accounts\" -w " + encode(secret) + "\n";
            Result result = run(List.of("security", "-i"), command);
            if (result.exitCode() != 0) {
                throw new IOException("the macOS keychain is unavailable");
            }
        }

        @Override
        String describe() {
            return "the macOS keychain";
        }
    }

    private static final class LinuxSecretService extends SecretStore {
        private final String account;
        private LinuxSecretService(String account) { this.account = account; }
        @Override
        String load() throws IOException {
            Result result = run(List.of("secret-tool", "lookup", "service", SERVICE, "account", this.account), null);
            if (result.exitCode() != 0 || result.stdout().isBlank()) {
                return null;
            }
            return decode(result.stdout());
        }

        @Override
        void save(final String secret) throws IOException {
            Result result = run(List.of("secret-tool", "store", "--label=ABNW Launcher accounts", "service", SERVICE, "account", this.account), encode(secret));
            if (result.exitCode() != 0) {
                throw new IOException("the Secret Service keyring is unavailable");
            }
        }

        @Override
        String describe() {
            return "the Secret Service keyring";
        }
    }

    private static final class OwnerOnlyFile extends SecretStore {
        private final Path file;

        private OwnerOnlyFile(final Path file) {
            this.file = file;
        }

        @Override
        String load() throws IOException {
            return Files.isRegularFile(this.file) ? decode(Files.readString(this.file, StandardCharsets.US_ASCII)) : null;
        }

        @Override
        void save(final String secret) throws IOException {
            Files.createDirectories(this.file.toAbsolutePath().getParent());
            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.deleteIfExists(temp);
            Files.createFile(temp);
            restrictToOwner(temp);
            Files.writeString(temp, encode(secret), StandardCharsets.US_ASCII);
            FileMoves.replace(temp, this.file);
            restrictToOwner(this.file);
        }

        private static void restrictToOwner(final Path path) throws IOException {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
                return;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) {
                UserPrincipal owner = Files.getOwner(path);
                AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
                acl.setAcl(List.of(entry));
            }
        }

        @Override
        String describe() {
            return "a file only this user can read";
        }
    }
}
