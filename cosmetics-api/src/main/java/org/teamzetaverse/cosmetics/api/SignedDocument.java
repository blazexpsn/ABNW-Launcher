package org.teamzetaverse.cosmetics.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** A signed public document, such as the cosmetics registry. */
public record SignedDocument(String keyId, byte[] payload, byte[] signature) {
    public static SignedDocument parse(String json) throws IOException {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String keyId = object.get("keyId").getAsString();
            byte[] payload = Base64.getDecoder().decode(object.get("payload").getAsString());
            byte[] signature = Base64.getDecoder().decode(object.get("signature").getAsString());
            if (!keyId.matches("[A-Za-z0-9_-]{1,32}") || payload.length == 0 || payload.length > 1024 * 1024 || signature.length != 64) {
                throw new IOException("Invalid signed cosmetics document.");
            }
            return new SignedDocument(keyId, payload, signature);
        } catch (RuntimeException e) {
            throw new IOException("The cosmetics service sent an invalid signed document.", e);
        }
    }

    public void verify(Map<String, PublicKey> trustedKeys) throws IOException {
        PublicKey key = trustedKeys.get(this.keyId);
        if (key == null) throw new IOException("The launcher does not trust this cosmetics registry key.");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(this.payload);
            if (!verifier.verify(this.signature)) throw new IOException("The cosmetics registry signature is invalid.");
        } catch (GeneralSecurityException e) {
            throw new IOException("The cosmetics registry could not be verified.", e);
        }
    }

    public String text() { return new String(this.payload, StandardCharsets.UTF_8); }

    public static Map<String, PublicKey> bundledKeys() throws IOException {
        Properties properties = new Properties();
        try (var in = SignedDocument.class.getResourceAsStream("/META-INF/abnw-cosmetics/registry-keys.properties")) {
            if (in != null) properties.load(in);
        }
        Map<String, PublicKey> keys = new HashMap<>();
        try {
            for (String id : properties.stringPropertyNames()) {
                keys.put(id, KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
                    Base64.getDecoder().decode(properties.getProperty(id).trim()))));
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Invalid bundled cosmetics registry key.", e);
        }
        return Map.copyOf(keys);
    }
}
