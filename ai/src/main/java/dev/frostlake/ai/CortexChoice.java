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

package dev.frostlake.ai;

import java.util.List;

/**
 * Picking one of a caller-supplied list. Shared by the two classifying functions and by the
 * sentiment-category one, all of which must answer with a value the caller named — a model that
 * replies with something else, or explains its choice, would otherwise leak a label that was never
 * offered.
 */
final class CortexChoice {

    private CortexChoice() {
    }

    /** The category the model picks for a plain classification, matched against the offered list. */
    static String one(final String subject, final List<String> categories) {
        return one("Classify the text below into exactly one of these categories: %s.", subject, categories);
    }

    /**
     * The same, under a caller-written instruction. The instruction carries a single {@code %s} where
     * the offered categories belong, so a function that asks about one aspect of a text still gets an
     * answer constrained to the list.
     */
    static String one(final String instruction, final String subject, final List<String> categories) {
        final StringBuilder offered = new StringBuilder();
        for (final String category : categories) {
            if (offered.length() > 0) {
                offered.append(", ");
            }
            offered.append(category);
        }
        final String reply = CortexText.terse(
            instruction.replace("%s", offered.toString()), subject);
        for (final String category : categories) {
            if (category.equalsIgnoreCase(reply)) {
                return category;
            }
        }
        for (final String category : categories) {
            if (reply.toLowerCase().contains(category.toLowerCase())) {
                return category;
            }
        }
        return categories.get(0);
    }
}
