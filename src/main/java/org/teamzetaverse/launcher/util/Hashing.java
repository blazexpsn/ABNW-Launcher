package org.teamzetaverse.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {
    private Hashing() {
    }

    public static String sha1(final Path file) throws IOException {
        return digest(file, "SHA-1");
    }

    public static String sha256(final Path file) throws IOException {
        return digest(file, "SHA-256");
    }

    public static String sha256(final byte[] bytes) {
        return HexFormat.of().formatHex(messageDigest("SHA-256").digest(bytes));
    }

    private static String digest(final Path file, final String algorithm) throws IOException {
        MessageDigest digest = messageDigest(algorithm);
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest messageDigest(final String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is unavailable", e);
        }
    }

    public static boolean matches(final Path file, final String algorithm, final String expected, final long size) throws IOException {
        requireHash(expected, file.toString());
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (size >= 0 && Files.size(file) != size) {
            return false;
        }
        return digest(file, algorithm).equalsIgnoreCase(expected);
    }

    public static String requireHash(final String expected, final String what) throws IOException {
        if (expected == null || !expected.matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")) {
            throw new IOException("Refusing to use " + what + " without a SHA-1 or SHA-256 to verify it against.");
        }
        return expected;
    }
}
