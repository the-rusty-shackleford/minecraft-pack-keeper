/* Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package com.nfx.packkeeper.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An immutable, named update of two vanilla client options.
 * AF: id names one application; the numbers are its render distance in chunks
 * and entity draw multiplier. RI: id is a safe single filename, render distance
 * is 2..32 and entity scaling is finite and 0.5..5. No other option is writable.
 */
public record DistanceUpdate(String id, int renderDistance, double entityDistanceScaling) {
    /**
     * requires: non-null id. effects: constructs the update.
     * throws: IllegalArgumentException for an unsafe id or out-of-range value;
     * NullPointerException for null id.
     */
    public DistanceUpdate {
        Objects.requireNonNull(id);
        if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}")
                || renderDistance < 2 || renderDistance > 32
                || !Double.isFinite(entityDistanceScaling)
                || entityDistanceScaling < 0.5 || entityDistanceScaling > 5.0) {
            throw new IllegalArgumentException("Invalid options update id or distance values");
        }
    }

    /**
     * requires: non-null options text. effects: returns text with only the two
     * named values replaced or appended; preserves all unrelated characters.
     * throws: NullPointerException if options is null.
     */
    public String applyTo(String options) {
        Objects.requireNonNull(options);
        return replace(replace(options, "renderDistance", Integer.toString(renderDistance)),
                "entityDistanceScaling", Double.toString(entityDistanceScaling));
    }

    private static String replace(String text, String key, String value) {
        var matcher = Pattern.compile("(?m)^" + key + ":[^\\r\\n]*").matcher(text);
        if (matcher.find()) {
            return matcher.replaceAll(key + ":" + value);
        }
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String separator = text.isEmpty() || text.endsWith("\n") || text.endsWith("\r") ? "" : newline;
        return text + separator + key + ":" + value + newline;
    }
}
