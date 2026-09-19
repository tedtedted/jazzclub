package com.tedredington.jazzclub.ui;

import java.util.Map;

/**
 * pianobar's format strings: {@code %x} is replaced by the value registered for {@code x}.
 * An unknown {@code %x} is printed unchanged, and a trailing lone {@code %} is dropped, as in pianobar.
 */
public final class CustomFormat {

    private CustomFormat() {
    }

    public static String render(String format, Map<Character, String> values) {
        StringBuilder out = new StringBuilder(format.length() + 32);
        boolean afterPercent = false;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '%' && !afterPercent) {
                afterPercent = true;
            } else if (afterPercent) {
                String value = values.get(c);
                if (value != null) {
                    out.append(value);
                } else if (!values.containsKey(c)) {
                    out.append('%').append(c);
                }
                afterPercent = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
