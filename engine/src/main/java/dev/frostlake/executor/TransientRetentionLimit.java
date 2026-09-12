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

/**
 * A TRANSIENT object keeps at most ONE day of history, and asking for more is refused.
 *
 * <p>This is the rule that gives the modifier consequences beyond a word in a metadata column: a
 * permanent schema takes a retention a transient one cannot, so the same statement succeeds or
 * refuses depending only on how its container was created. Live-verified: 0 and 1 are accepted,
 * 2 refuses — and it refuses in the BRACKETED invalid-value shape shared with every other
 * out-of-range parameter, quoting the number bare rather than the object or the limit.
 */
public final class TransientRetentionLimit {

    /** Transient storage keeps one day; Time Travel past that is what a permanent object is for. */
    private static final int MAX_TRANSIENT_DAYS = 1;

    private TransientRetentionLimit() {
    }

    /**
     * Refuse a retention a transient object cannot hold.
     *
     * @param transientObject whether the target is transient — a permanent object is never capped here
     * @param rawValue the value AS WRITTEN, which is what the sentence echoes
     */
    /**
     * The account-wide retention ceiling: any value past 90 days refuses with the account's own
     * sentence, on a database and a schema alike (live-verified).
     */
    public static void requireWithinAccountLimit(final String rawValue) {
        if (rawValue != null && rawValue.matches("[0-9]+") && Long.parseLong(rawValue) > 90L) {
            throw new RuntimeException(SqlCompilationError.of(
                "Exceeds maximum allowable retention time (90 day(s))."));
        }
    }

    public static void requireWithinTransientLimit(final boolean transientObject, final String rawValue) {
        if (!transientObject || rawValue == null) {
            return;
        }
        final int days;
        try {
            days = Integer.parseInt(rawValue.trim());
        } catch (final NumberFormatException notANumber) {
            return;
        }
        if (days > MAX_TRANSIENT_DAYS) {
            throw new RuntimeException(
                SqlCompilationError.invalidValueForParameter(rawValue, "DATA_RETENTION_TIME_IN_DAYS"));
        }
    }
}
