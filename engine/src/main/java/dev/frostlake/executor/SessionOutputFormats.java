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

import java.util.HashMap;
import java.util.Map;

/**
 * The output formats the session has set, which every STRING rendering of a date, a time or a timestamp follows —
 * {@code ::VARCHAR}, {@code ||}, TO_VARCHAR and TO_CHAR without a format, a string function's argument, LIKE, and a
 * value embedded in a VARIANT (all live-verified). A format the session has not set leaves the built-in rendering.
 *
 * <p>TIMESTAMP_NTZ, TIMESTAMP_LTZ and TIMESTAMP_TZ each take their own parameter, and an EMPTY one — the default for
 * LTZ and TZ — falls back to TIMESTAMP_OUTPUT_FORMAT, which the NTZ default of its own shields NTZ from until NTZ is
 * set to ''. DATE and TIME stand apart from all of them.
 *
 * <p>Pinned per statement in a ThreadLocal, as {@link SessionTimestampMapping} is: the rendering lives deep in static
 * helpers with no session to ask, and one engine serves many sessions.
 */
public final class SessionOutputFormats {

    public static final String DATE = "DATE_OUTPUT_FORMAT";
    public static final String TIME = "TIME_OUTPUT_FORMAT";
    public static final String TIMESTAMP = "TIMESTAMP_OUTPUT_FORMAT";
    public static final String TIMESTAMP_NTZ = "TIMESTAMP_NTZ_OUTPUT_FORMAT";
    public static final String TIMESTAMP_LTZ = "TIMESTAMP_LTZ_OUTPUT_FORMAT";
    public static final String TIMESTAMP_TZ = "TIMESTAMP_TZ_OUTPUT_FORMAT";

    /** The built-in TIMESTAMP_OUTPUT_FORMAT, which an emptied NTZ format falls back to. */
    private static final String TIMESTAMP_DEFAULT = "YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM";

    private static final String[] NAMES = {DATE, TIME, TIMESTAMP, TIMESTAMP_NTZ, TIMESTAMP_LTZ, TIMESTAMP_TZ};

    private static final ThreadLocal<Map<String, String>> PINNED = new ThreadLocal<Map<String, String>>();

    private SessionOutputFormats() {
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

    /** Put back whatever {@link #pin} displaced; a null restores the unpinned state. */
    public static void restore(final Map<String, String> previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    /** The format a DATE renders with, or null for the built-in one. */
    public static String date() {
        return usable(set(DATE));
    }

    /** The format a TIME renders with, or null for the built-in one. */
    public static String time() {
        return usable(set(TIME));
    }

    /** The format a TIMESTAMP_NTZ renders with, or null for the built-in one. */
    public static String timestampNtz() {
        final String own = set(TIMESTAMP_NTZ);
        if (own == null) {
            return null;
        }
        if (own.isEmpty()) {
            final String generic = set(TIMESTAMP);
            return generic == null || generic.isEmpty() ? TIMESTAMP_DEFAULT : usable(generic);
        }
        return usable(own);
    }

    /** The format a TIMESTAMP_LTZ renders with, or null for the built-in one. */
    public static String timestampLtz() {
        return zoned(TIMESTAMP_LTZ);
    }

    /** The format a TIMESTAMP_TZ renders with, or null for the built-in one. */
    public static String timestampTz() {
        return zoned(TIMESTAMP_TZ);
    }

    private static String zoned(final String name) {
        final String own = set(name);
        if (own != null && !own.isEmpty()) {
            return usable(own);
        }
        final String generic = set(TIMESTAMP);
        return generic == null || generic.isEmpty() ? null : usable(generic);
    }

    /** A format to apply, or null: AUTO reads as the built-in rendering. */
    private static String usable(final String format) {
        return format == null || "AUTO".equalsIgnoreCase(format) ? null : format;
    }

    private static String set(final String name) {
        final Map<String, String> pinned = PINNED.get();
        return pinned == null ? null : pinned.get(name);
    }
}
