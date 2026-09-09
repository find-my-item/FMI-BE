package com.fmi.external.translation.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TranslationTextNormalizer {

    private TranslationTextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.replaceAll("[^\\p{IsHangul}\\p{IsAlphabetic}0-9\\s]", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    public static String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
