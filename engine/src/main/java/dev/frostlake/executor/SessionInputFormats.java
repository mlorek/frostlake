/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.frostlake.executor;

import dev.frostlake.values.BinaryValue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The input formats the session has set, which every reading of TEXT as a date, a time or a timestamp follows when no
 * format of its own is written: a cast, a typed literal, TO_DATE, TO_TIME and the TO_TIMESTAMP family and their TRY_
 * forms, and a value written into a column (all live-verified). A format other than AUTO is the only reading: text it
 * does not fit is not recognized, an ISO date and a string of digits included, and the TRY_ forms answer NULL for it.
 *
 * <p>DATE_INPUT_FORMAT, TIME_INPUT_FORMAT and TIMESTAMP_INPUT_FORMAT, the last for all three timestamp kinds, and
 * BINARY_INPUT_FORMAT, which every reading of TEXT as BINARY follows when no format of its own is written: a cast, a
 * one-argument TO_BINARY and TRY_TO_BINARY, and a value written into a column (live-verified). A COPY load takes them
 * over with its file format's DATE_FORMAT, TIME_FORMAT, TIMESTAMP_FORMAT and BINARY_FORMAT. Pinned per statement in a
 * ThreadLocal, as {@link SessionOutputFormats} is: the conversions live deep in static helpers with no session to ask,
 * and one engine serves many sessions.
 */
public final class SessionInputFormats {

    public static final String DATE = "DATE_INPUT_FORMAT";
    public static final String TIME = "TIME_INPUT_FORMAT";
    public static final String TIMESTAMP = "TIMESTAMP_INPUT_FORMAT";
    /** How a text reads as BINARY — HEX (the default), BASE64, or UTF8 (also spelled UTF-8), in any case. */
    public static final String BINARY = "BINARY_INPUT_FORMAT";

    private static final String[] NAMES = {DATE, TIME, TIMESTAMP, BINARY};

    private static final ThreadLocal<Map<String, String>> PINNED = new ThreadLocal<Map<String, String>>();

    private SessionInputFormats() {
    }

    /** The parameters these formats are read from. */
    public static String[] names() {
        return NAMES.clone();
    }

    /**
     * Pin this statement's formats, returning what was pinned before so the caller can restore it.
     *
     * @param set the formats the session has set, by parameter name; a name it has not set is absent
     * @return the formats pinned before
     */
    public static Map<String, String> pin(final Map<String, String> set) {
        final Map<String, String> previous = PINNED.get();
        PINNED.set(set == null || set.isEmpty() ? null : new HashMap<String, String>(set));
        return previous;
    }

    /** What is pinned now, for {@link #restore} to put back after a reading that takes some formats over. */
    public static Map<String, String> pinned() {
        return PINNED.get();
    }

    /** Put back whatever {@link #pin} displaced; a null restores the unpinned state. */
    public static void restore(final Map<String, String> previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    /**
     * Pin the same formats without one of them, for a reading that asks for AUTO itself — TO_DATE(x, 'AUTO') reads
     * AUTO whatever the session has set (live-verified) — returning what was pinned before.
     *
     * @param name the parameter to leave out
     * @return the formats pinned before
     */
    public static Map<String, String> without(final String name) {
        final Map<String, String> previous = PINNED.get();
        if (previous != null && previous.containsKey(name)) {
            final Map<String, String> rest = new HashMap<String, String>(previous);
            rest.remove(name);
            PINNED.set(rest.isEmpty() ? null : rest);
        }
        return previous;
    }

    /**
     * Pin the same formats with some taken over, for a reading that brings formats of its own — a COPY load's
     * DATE_FORMAT, TIME_FORMAT, TIMESTAMP_FORMAT and BINARY_FORMAT — returning what was pinned before. A format given
     * as AUTO, or not given, leaves the session's own in force (documented).
     *
     * @param formats the formats by parameter name ({@link #DATE}, {@link #TIME}, {@link #TIMESTAMP}, {@link #BINARY})
     * @return the formats pinned before
     */
    public static Map<String, String> override(final Map<String, String> formats) {
        final Map<String, String> previous = PINNED.get();
        final Map<String, String> merged = previous == null
            ? new HashMap<String, String>() : new HashMap<String, String>(previous);
        for (final Map.Entry<String, String> format : formats.entrySet()) {
            if (format.getValue() != null && !"AUTO".equalsIgnoreCase(format.getValue())) {
                merged.put(format.getKey(), format.getValue());
            }
        }
        PINNED.set(merged.isEmpty() ? null : merged);
        return previous;
    }

    /** The format text becomes a DATE by, or null for AUTO. */
    public static String date() {
        return usable(DATE);
    }

    /** The format text becomes a TIME by, or null for AUTO. */
    public static String time() {
        return usable(TIME);
    }

    /** The format text becomes a timestamp of any kind by, or null for AUTO. */
    public static String timestamp() {
        return usable(TIMESTAMP);
    }

    /**
     * A text read as BINARY bytes: as BASE64 or as its UTF-8 bytes where the session's BINARY_INPUT_FORMAT, or a
     * load's BINARY_FORMAT, says so, as hex digits otherwise.
     *
     * @param text the text
     * @return its bytes
     */
    public static BinaryValue binary(final String text) {
        final Map<String, String> pinned = PINNED.get();
        return binary(text, pinned == null ? null : pinned.get(BINARY));
    }

    /**
     * A text read as BINARY bytes by a format of its own: BASE64, UTF8 (also spelled UTF-8), or hex digits for HEX or
     * no format at all, the name in any case.
     *
     * @param text   the text
     * @param format the format, or null for hex
     * @return its bytes
     */
    public static BinaryValue binary(final String text, final String format) {
        if ("BASE64".equalsIgnoreCase(format)) {
            return BinaryValue.fromBase64Input(text);
        }
        if ("UTF8".equalsIgnoreCase(format) || "UTF-8".equalsIgnoreCase(format)) {
            return BinaryValue.of(text.getBytes(StandardCharsets.UTF_8));
        }
        return BinaryValue.fromHexInput(text);
    }

    /**
     * Whether a name is one of the binary formats: HEX, BASE64, UTF8 or UTF-8, in any case.
     *
     * @param format the name
     * @return true for a binary format
     */
    public static boolean isBinaryFormat(final String format) {
        return "HEX".equalsIgnoreCase(format) || "BASE64".equalsIgnoreCase(format) || "UTF8".equalsIgnoreCase(format)
            || "UTF-8".equalsIgnoreCase(format);
    }

    private static String usable(final String name) {
        final Map<String, String> pinned = PINNED.get();
        final String format = pinned == null ? null : pinned.get(name);
        return format == null || "AUTO".equalsIgnoreCase(format) ? null : format;
    }
}
