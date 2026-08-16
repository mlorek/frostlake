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

import java.util.Locale;

/**
 * The flavour a BARE {@code TIMESTAMP} means, which the session's TIMESTAMP_TYPE_MAPPING parameter
 * names.
 *
 * <p>Live resolves the bare word against whatever the mapping says AT THE MOMENT it is written, on
 * every spelling that can carry it — {@code CAST(x AS TIMESTAMP)}, {@code x::TIMESTAMP},
 * {@code TO_TIMESTAMP(x)} and a column declared {@code TIMESTAMP}. It is resolved ONCE and kept: a
 * column created under TIMESTAMP_LTZ still describes as TIMESTAMP_LTZ after the session switches back.
 *
 * <p><strong>DATETIME does NOT follow it.</strong> It is an alias for TIMESTAMP_NTZ specifically, not
 * for the mapped TIMESTAMP, and stays NTZ under every mapping — measured on both engines. The three
 * explicit spellings are likewise untouched; only the bare word is unresolved.
 *
 * <p>Held in a ThreadLocal pinned for the statement, the same way {@link SessionZone} carries the
 * session's zone into the value layer, and for the same reason: one engine serves many sessions.
 */
public final class SessionTimestampMapping {

    /** What a bare TIMESTAMP means when nothing has set the parameter. */
    public static final String DEFAULT = "TIMESTAMP_NTZ";

    private static final ThreadLocal<String> PINNED = new ThreadLocal<String>();

    private SessionTimestampMapping() {
    }

    /** The flavour a bare TIMESTAMP currently means. */
    public static String current() {
        final String pinned = PINNED.get();
        return pinned != null ? pinned : DEFAULT;
    }

    /** Whether the mapping names a zone-carrying flavour, which is the only case that changes anything. */
    public static boolean isZoned() {
        return "TIMESTAMP_LTZ".equals(current()) || "TIMESTAMP_TZ".equals(current());
    }

    /**
     * Pin this statement's mapping, returning what was pinned before so the caller can restore it. The
     * value is validated where it is SET, so an unrecognised one simply falls back to the default.
     */
    public static String pin(final Object mappingName) {
        final String previous = PINNED.get();
        PINNED.set(resolve(mappingName));
        return previous;
    }

    /** Put back whatever {@link #pin} displaced; a null restores the unpinned state. */
    public static void restore(final String previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    private static String resolve(final Object mappingName) {
        if (mappingName == null) {
            return DEFAULT;
        }
        final String text = String.valueOf(mappingName).trim().replace("'", "").toUpperCase(Locale.ROOT);
        return "TIMESTAMP_LTZ".equals(text) || "TIMESTAMP_TZ".equals(text) ? text : DEFAULT;
    }
}
