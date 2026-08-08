package com.example.urlshorten.util;

import java.math.BigInteger;

public final class Base62 {

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final char[] CHARS = ALPHABET.toCharArray();
    private static final int BASE = CHARS.length;

    private Base62() {
    }

    public static String encode(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be positive, got: " + value);
        }
        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            sb.append(CHARS[(int) (remaining % BASE)]);
            remaining /= BASE;
        }
        return sb.reverse().toString();
    }

    public static String encode(byte[] input) {
        BigInteger value = new BigInteger(1, input);
        if (value.equals(BigInteger.ZERO)) {
            return "0";
        }

        StringBuilder encoded = new StringBuilder();
        BigInteger base = BigInteger.valueOf(BASE);
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = value.divideAndRemainder(base);
            encoded.append(CHARS[divRem[1].intValue()]);
            value = divRem[0];
        }
        return encoded.reverse().toString();
    }

    public static boolean isValidCode(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (ALPHABET.indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
