package dev.mauricioandrade.kakeizu.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 from the JDK, no extra dependency. Format: {@code pbkdf2-sha256:iterations:salt:hash}.
 * <p>
 * Generate a hash with: {@code java src/main/java/dev/mauricioandrade/kakeizu/infrastructure/security/PasswordHasher.java}
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000; // OWASP 2023 recommendation for PBKDF2-HMAC-SHA256
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private PasswordHasher() {
    }

    public static String hash(char[] password) {
        var salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        var encoder = Base64.getEncoder();
        return String.join(":", PREFIX, String.valueOf(ITERATIONS),
                encoder.encodeToString(salt), encoder.encodeToString(derive(password, salt, ITERATIONS)));
    }

    public static boolean verify(char[] password, String encoded) {
        String[] parts = encoded.split(":");
        if (parts.length != 4 || !parts[0].equals(PREFIX)) {
            throw new IllegalArgumentException("Not a " + PREFIX + " hash");
        }
        var decoder = Base64.getDecoder();
        byte[] expected = decoder.decode(parts[3]);
        byte[] actual = derive(password, decoder.decode(parts[2]), Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(expected, actual); // constant time
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) {
        var console = System.console();
        if (console == null) {
            System.err.println("Run this from a terminal");
            System.exit(1);
        }
        char[] password = console.readPassword("Admin password: ");
        if (!java.util.Arrays.equals(password, console.readPassword("Repeat: "))) {
            System.err.println("Passwords do not match");
            System.exit(1);
        }
        System.out.println(hash(password));
    }
}
