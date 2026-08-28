package com.alexeys.translate_allinone.utils.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ApiKeyCipher {
    public static final String PREFIX = "enc:v1:";
    public static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ApiKeyCipher() {
    }

    public static byte[] loadOrCreateKey(Path path) {
        try {
            if (Files.exists(path)) {
                String text = Files.readString(path).trim();
                byte[] decoded = Base64.getDecoder().decode(text);
                if (decoded.length == KEY_LENGTH) {
                    return decoded;
                }
            }
            return createKey(path);
        } catch (Exception e) {
            return createKey(path);
        }
    }

    public static boolean isCiphertext(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plaintext, UUID uuid, String providerId, byte[] key) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad(uuid, providerId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, payload, IV_LENGTH, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt API key for provider: " + providerId, e);
        }
    }

    public static Optional<String> decrypt(String ciphertext, UUID uuid, String providerId, byte[] key) {
        if (!isCiphertext(ciphertext)) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                return Optional.empty();
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad(uuid, providerId));
            return Optional.of(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static byte[] aad(UUID uuid, String providerId) {
        String provider = providerId == null ? "" : providerId;
        return (uuid.toString() + "\n" + provider).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] createKey(Path path) {
        byte[] key = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(key);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeKey(path, key);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create API key cipher file: " + path, e);
        }
    }

    private static void writeKey(Path path, byte[] key) throws Exception {
        byte[] encoded = Base64.getEncoder().encodeToString(key).getBytes(StandardCharsets.UTF_8);
        Files.write(path, encoded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrictPermissions(path);
    }

    private static void restrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
        } catch (Exception ignored) {
        }
    }
}