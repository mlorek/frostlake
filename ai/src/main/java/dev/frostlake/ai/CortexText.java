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
 * Shared helpers for the Cortex functions: reading a text argument, and asking the model a question
 * whose answer is meant to be one bare value rather than a paragraph.
 *
 * <p>Every Cortex function here follows Snowflake in returning NULL for a NULL input, so a column of
 * mixed data does not need guarding.
 */
final class CortexText {

    private static final String THINKING_OPEN = "<think>";
    private static final String THINKING_CLOSE = "</think>";

    private CortexText() {
    }

    /** An argument as text, or null when the argument is SQL NULL. */
    static String text(final Object argument) {
        return argument == null ? null : String.valueOf(argument);
    }

    /**
     * Ask for a single value and give back exactly that: a local model tends to explain itself, so
     * the instruction says not to, and the reply is trimmed of the punctuation and quoting it adds
     * anyway.
     *
     * <p>No token cap. A cap that looks generous for the answer still truncates a model that opens
     * with whitespace or a preamble, and a truncated reply comes back as the empty string rather than
     * as an error — a silently wrong answer instead of a slow one.
     */
    static String terse(final String instruction, final String subject) {
        final String reply = OllamaClient.generate(OllamaConfig.model(),
            instruction + "\n\nAnswer with the value only, no explanation and no punctuation.\n\n"
            + subject, Double.valueOf(0.0), null);
        return strip(reply);
    }

    /**
     * Trim whitespace, wrapping quotes and a trailing full stop from a model's one-value reply — and
     * a leading {@code <think>…</think>} block, which a reasoning model emits ahead of its answer.
     */
    static String strip(final String reply) {
        String value = reply == null ? "" : reply.trim();
        final int endOfThinking = value.indexOf(THINKING_CLOSE);
        if (value.startsWith(THINKING_OPEN) && endOfThinking >= 0) {
            value = value.substring(endOfThinking + THINKING_CLOSE.length()).trim();
        }
        while (value.length() > 1
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    /** The first double the reply contains, or {@code fallback} when it contains none. */
    static double firstNumber(final String reply, final double fallback) {
        final String cleaned = strip(reply);
        final StringBuilder number = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            final char ch = cleaned.charAt(i);
            if (Character.isDigit(ch) || ch == '-' || ch == '.') {
                number.append(ch);
            } else if (number.length() > 0) {
                break;
            }
        }
        try {
            return Double.parseDouble(number.toString());
        } catch (final NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** The category list argument, whatever shape the engine handed it over in. */
    static List<String> categories(final Object argument) {
        return CortexCategories.of(argument);
    }
}
