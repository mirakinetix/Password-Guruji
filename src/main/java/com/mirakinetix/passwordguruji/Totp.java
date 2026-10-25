package com.mirakinetix.passwordguruji;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Locale;

final class Totp {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}

    static String currentCode(String secret) throws Exception {
        byte[] key = decodeBase32(secret);
        byte[] message = ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 30_000L).array();
        byte[] hash = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } finally {
            java.util.Arrays.fill(key, (byte)0);
            java.util.Arrays.fill(message, (byte)0);
            if (hash != null) java.util.Arrays.fill(hash, (byte)0);
        }
    }

    static int secondsRemaining() {
        return 30 - (int)((System.currentTimeMillis() / 1000L) % 30);
    }

    private static byte[] decodeBase32(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Enter a Base32 authenticator secret.");
        String value = input.replaceAll("[=\\s-]", "").toUpperCase(Locale.ROOT);
        if (value.length() > 128) throw new IllegalArgumentException("Authenticator secret is too long.");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : value.toCharArray()) {
            int digit = BASE32.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Authenticator secret contains a character outside Base32.");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        byte[] result = out.toByteArray();
        if (bits > 0 && (buffer & ((1 << bits) - 1)) != 0) throw new IllegalArgumentException("Authenticator secret has invalid Base32 padding.");
        if (result.length < 10) { java.util.Arrays.fill(result, (byte)0); throw new IllegalArgumentException("Authenticator secret is too short."); }
        return result;
    }
}
