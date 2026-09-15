package org.teamzetaverse.launcher.minecraft;

import com.davidehrmann.vcdiff.VCDiffDecoderBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.release.Release;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Hashing;

final class AbnwPatcher {
    private static final int MAX_TARGET_SIZE = 512 * 1024 * 1024;

    private AbnwPatcher() {
    }

    static Path ensure(final Release release, final Path vanillaJar, final Path deltasDir, final Path jarsDir, final Progress progress)
        throws IOException, Progress.CancelledException {
        Path output = jarsDir.resolve(release.patchedJarName());
        progress.stage("Checking the " + release.displayName() + " jar...", 0);
        if (Hashing.matches(output, release.target.sha256, release.target.size)) {
            return output;
        }

        Path delta = deltasDir.resolve(release.deltaFileName());
        if (!Hashing.matches(delta, release.delta.sha256, release.delta.size)) {
            progress.stage("Downloading the " + release.displayName() + " patch", release.delta.size);
            Http.download(release.delta.url, delta, release.delta.sha256, progress::advance, progress::isCancelled);
        }
        progress.checkCancelled();

        progress.stage("Building the " + release.displayName() + " jar...", 0);
        byte[] source = Files.readAllBytes(vanillaJar);
        if (!Hashing.sha256(source).equalsIgnoreCase(release.source.sha256)) {
            throw new IOException("The Minecraft " + release.minecraft + " jar is not the one " + release.displayName()
                + " was built for. Delete " + vanillaJar + " and try again.");
        }
        byte[] rebuilt;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(source.length);
            VCDiffDecoderBuilder.builder()
                .withMaxTargetFileSize(MAX_TARGET_SIZE)
                .buildSimple()
                .decode(source, Files.readAllBytes(delta), out);
            rebuilt = out.toByteArray();
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(delta);
            throw new IOException("The " + release.displayName() + " patch could not be applied: " + e.getMessage(), e);
        }
        if ((release.target.size >= 0 && rebuilt.length != release.target.size) || !Hashing.sha256(rebuilt).equalsIgnoreCase(release.target.sha256)) {
            Files.deleteIfExists(delta);
            throw new IOException("The rebuilt " + release.displayName() + " jar does not match the release. Try again.");
        }

        Files.createDirectories(jarsDir);
        Path temp = jarsDir.resolve(output.getFileName() + ".part");
        Files.write(temp, rebuilt);
        org.teamzetaverse.launcher.util.FileMoves.replace(temp, output);
        return output;
    }
}
