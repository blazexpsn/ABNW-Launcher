package org.teamzetaverse.cosmetics.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** A locally verified ABNW grant. Verification performs no I/O or network requests. */
public final class SignedEntitlement {
    private static final int MAX_BYTES = 512 * 1024;
    private final String accountId;
    private final long revision;
    private final Map<String, Long> grants;

    private SignedEntitlement(String accountId, long revision, Map<String, Long> grants) {
        this.accountId = accountId;
        this.revision = revision;
        this.grants = Map.copyOf(grants);
    }

    public String accountId() { return this.accountId; }
    public long revision() { return this.revision; }
    public Set<String> ownedAt(long epochSeconds) {
        Set<String> result = new HashSet<>();
        this.grants.forEach((id, expiry) -> { if (expiry == 0 || epochSeconds < expiry) result.add(id); });
        return Set.copyOf(result);
    }

    /** Wire and disk envelope: key ID, payload length, canonical payload, 64-byte Ed25519 signature. */
    public static byte[] envelope(String keyId, byte[] payload, byte[] signature) throws IOException {
        if (!keyId.matches("[a-zA-Z0-9_-]{1,32}") || payload.length > MAX_BYTES || signature.length != 64) {
            throw new IOException("Invalid entitlement envelope.");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] key = keyId.getBytes(StandardCharsets.US_ASCII);
            out.writeShort(key.length); out.write(key);
            out.writeInt(payload.length); out.write(payload); out.write(signature);
        }
        return bytes.toByteArray();
    }

    public static SignedEntitlement verify(byte[] envelope, Map<String, PublicKey> trustedKeys,
                                            String expectedAccount, String expectedCredentialHash, long minimumRevision) throws IOException {
        if (envelope.length > MAX_BYTES + 104) throw new IOException("Entitlement is too large.");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(envelope))) {
            String keyId = ascii(in, 32);
            int length = in.readInt();
            if (length < 0 || length > MAX_BYTES || length + 64 != in.available()) throw new IOException("Invalid entitlement length.");
            byte[] payload = in.readNBytes(length);
            byte[] signature = in.readNBytes(64);
            PublicKey key = trustedKeys.get(keyId);
            if (key == null) throw new IOException("This launcher does not trust the entitlement signing key.");
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key); verifier.update(payload);
            if (!verifier.verify(signature)) throw new IOException("The cosmetic entitlement signature is invalid.");
            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload))) {
                if (data.readInt() != 0x41424e45 || data.readUnsignedShort() != 1) throw new IOException("Unsupported entitlement format.");
                String accountId = ascii(data, 37);
                String credentialHash = ascii(data, 64);
                long revision = data.readLong();
                long issuedAt = data.readLong();
                if (!accountId.matches("abnw_[0-9a-f]{32}") || !accountId.equals(expectedAccount)
                    || !credentialHash.matches("[0-9a-f]{64}") || !credentialHash.equals(expectedCredentialHash)
                    || revision < Math.max(0, minimumRevision) || revision > 9007199254740991L
                    || issuedAt < 0 || issuedAt > 9007199254740991L) {
                    throw new IOException("Entitlement belongs to another identity, credential, or an older revision.");
                }
                int count = data.readUnsignedShort();
                if (count > 4096) throw new IOException("Too many cosmetic grants.");
                Map<String, Long> grants = new LinkedHashMap<>();
                String previous = "";
                for (int i = 0; i < count; i++) {
                    String id = ascii(data, 97);
                    long expiry = data.readLong();
                    if (!id.matches("[a-z0-9_]{1,32}:[a-z0-9_]{1,64}") || id.compareTo(previous) <= 0
                        || expiry < 0 || expiry > 9007199254740991L) throw new IOException("Noncanonical cosmetic grant.");
                    grants.put(id, expiry); previous = id;
                }
                if (data.available() != 0) throw new IOException("Trailing entitlement data.");
                return new SignedEntitlement(accountId, revision, grants);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Cannot verify cosmetic entitlement.", e);
        }
    }

    public static Map<String, PublicKey> bundledKeys() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = SignedEntitlement.class.getResourceAsStream("/META-INF/abnw-cosmetics/entitlement-keys.properties")) {
            if (in != null) properties.load(in);
        }
        Map<String, PublicKey> keys = new HashMap<>();
        try {
            for (String id : properties.stringPropertyNames()) {
                keys.put(id, KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(properties.getProperty(id).trim()))));
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IOException("Invalid bundled entitlement verification key.", e); }
        return Map.copyOf(keys);
    }

    private static String ascii(DataInputStream in, int max) throws IOException {
        int length = in.readUnsignedShort();
        if (length < 1 || length > max) throw new IOException("Invalid entitlement string length.");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        for (byte b : bytes) if (b < 0 || b < 32) throw new IOException("Non-ASCII entitlement field.");
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
