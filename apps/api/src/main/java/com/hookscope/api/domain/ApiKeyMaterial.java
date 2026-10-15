package com.hookscope.api.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiKeyMaterial {
    private static final Pattern FORMAT = Pattern.compile("^(hs_[0-9a-f]{16})_[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyMaterial() {}

    public static Generated generate() {
        byte[] prefixBytes = new byte[8];
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(prefixBytes);
        RANDOM.nextBytes(secretBytes);
        String prefix = "hs_" + HexFormat.of().formatHex(prefixBytes);
        String value = prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        return new Generated(value, prefix, hash(value));
    }

    public static Optional<String> prefixOf(String value) {
        if (value == null) return Optional.empty();
        Matcher matcher = FORMAT.matcher(value);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static boolean matches(String value, String expectedHash) {
        return MessageDigest.isEqual(HexFormat.of().parseHex(hash(value)), HexFormat.of().parseHex(expectedHash));
    }

    public record Generated(String value, String prefix, String hash) {
        @Override public String toString() { return "Generated[prefix=" + prefix + ", value=REDACTED]"; }
    }
}
