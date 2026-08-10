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

package dev.frostlake;

import org.junit.jupiter.api.Assumptions;

/**
 * Distinguishes "this live account may not use the feature" from "Frostlake and Snowflake disagree".
 *
 * <p>A trial account carries no Cortex AI functions, so every test that builds a CORTEX SEARCH SERVICE
 * fails there with {@code AI function EMBED_TEXT_768 is not available for trial accounts.} That is the
 * account's tier talking, not a fidelity result — reported as an ERROR it is nineteen lines of noise in
 * every live round, and it hides the rounds where something real breaks. Reported as a SKIP with the
 * account's own sentence, it says exactly what it is.
 *
 * <p>The gate is deliberately narrow: only Snowflake's own "not available / not enabled for your
 * account" refusals abort the test. Anything else is rethrown untouched, so a genuine divergence can
 * never be silently swallowed. It is also only ever consulted on a live run — nothing here can mask an
 * embedded failure.
 */
public final class LiveFeatureGate {

    private LiveFeatureGate() {
    }

    /**
     * Abort the current test as SKIPPED when {@code failure} is the account refusing a feature its tier
     * does not include; otherwise rethrow it unchanged. Never returns normally.
     */
    public static void skipIfAccountTierBlocks(final RuntimeException failure) {
        final String message = String.valueOf(failure.getMessage());
        if (message.contains("not available for trial accounts")
                || message.contains("not enabled for your account")
                || message.contains("not supported in your account")) {
            Assumptions.abort("live account tier blocks this feature: " + firstSentence(message));
        }
        throw failure;
    }

    /** The refusal's own first sentence, for a skip reason that quotes the account rather than us. */
    private static String firstSentence(final String message) {
        final String flat = message.replace('\n', ' ').trim();
        final int stop = flat.indexOf('.');
        return stop > 0 ? flat.substring(0, stop + 1) : flat;
    }
}
