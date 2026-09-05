package com.seongmin.spike.error.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** fingerprint = sha256(errorType | normalize(message) | topAppFrame(stackTrace))[:32]. */
@Component
public class Fingerprinter {
    public String generate(String errorType, String message, String stackTrace) {
        String raw = errorType + "|" + normalize(message) + "|" + topAppFrame(stackTrace);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalize(String message) {
        if (message == null) return "";
        return message
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "#")
                .replaceAll("\\b[0-9a-fA-F]{8,}\\b", "#")
                .replaceAll("\\d+", "#")
                .replaceAll("'[^']*'|\"[^\"]*\"", "?")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** 첫 애플리케이션 프레임의 Class.method (파일·라인 제외). 없으면 빈 문자열. */
    static String topAppFrame(String stackTrace) {
        if (stackTrace == null) return "";
        for (String line : stackTrace.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("at ")) continue;
            String frame = t.substring(3);
            if (frame.startsWith("java.") || frame.startsWith("jakarta.") || frame.startsWith("jdk.")
                    || frame.startsWith("org.springframework.") || frame.startsWith("sun.")) continue;
            int paren = frame.indexOf('(');
            return paren > 0 ? frame.substring(0, paren) : frame;
        }
        return "";
    }
}
