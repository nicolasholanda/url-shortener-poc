package com.nicolasholanda.urlshortener.id;

public final class Base62Codec {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();
    private static final int[] LOOKUP = new int[128];

    static {
        for (int i = 0; i < LOOKUP.length; i++) {
            LOOKUP[i] = -1;
        }
        for (int i = 0; i < BASE; i++) {
            LOOKUP[ALPHABET.charAt(i)] = i;
        }
    }

    private Base62Codec() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder builder = new StringBuilder(11);
        long remaining = value;
        while (remaining > 0) {
            builder.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return builder.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("encoded value must not be empty");
        }

        long value = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int digit = c < LOOKUP.length ? LOOKUP[c] : -1;
            if (digit < 0) {
                throw new IllegalArgumentException("invalid base62 character: " + c);
            }
            value = value * BASE + digit;
        }
        return value;
    }

    public static boolean isValid(String candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.length() > 16) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c >= LOOKUP.length || LOOKUP[c] < 0) {
                return false;
            }
        }
        return true;
    }
}
