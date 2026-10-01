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
package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A tag's {@code PROPAGATE}, {@code ON_CONFLICT} and {@code COMMENT} properties, as CREATE TAG and ALTER TAG … SET
 * write them: in any order after the allowed values, each at most once. The mode is one of the three the tag
 * documentation names; the conflict rule is a string or {@code ALLOWED_VALUES_SEQUENCE}. Frostlake records both and
 * reports them in SHOW TAGS; it does not propagate tags.
 *
 * <p>A conflict rule is held to the tag it would leave, before anything changes: it needs a propagation mode, the
 * allowed-values sequence needs allowed values, and a string rule on a tag with allowed values must be one of them.
 * Each is refused with the account's {@code Invalid on_conflict strategy} sentence.
 */
final class TagPropagation {

    /** The propagation modes a tag takes. */
    private static final List<String> MODES =
        Arrays.asList("ON_DEPENDENCY_AND_DATA_MOVEMENT", "ON_DEPENDENCY", "ON_DATA_MOVEMENT");

    /** The conflict rule that orders by the allowed values. */
    static final String SEQUENCE = "ALLOWED_VALUES_SEQUENCE";

    private static final String STRATEGY = "Invalid on_conflict strategy: ";

    private TagPropagation() {
    }

    /**
     * Refuses what the account refuses at compile time, before the tag is looked up: a property written twice
     * ({@code duplicate property 'ON_CONFLICT';}), then a propagation mode that is none of the three.
     */
    static void requireCompilable(final List<FrostlakeParser.TagSetPropertyContext> properties) {
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.TagSetPropertyContext property : properties) {
            keys.add(property.getStart().getText());
        }
        PropertyDuplicates.reject(keys);
        for (final FrostlakeParser.TagSetPropertyContext property : properties) {
            if (property.tagPropagation() != null) {
                mode(property.tagPropagation());
            }
        }
    }

    /** The clause's mode, refused unless it is one of the three. */
    static String mode(final FrostlakeParser.TagPropagationContext clause) {
        final String mode = SqlIdentifiers.canonical(clause.identifier()).toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new RuntimeException(SqlCompilationError.of("invalid value '" + mode
                + "' for property 'PROPAGATE'"));
        }
        return mode;
    }

    /** The conflict rule the clause names. */
    static String conflict(final FrostlakeParser.TagConflictContext clause) {
        if (clause.ALLOWED_VALUES_SEQUENCE() != null) {
            return SEQUENCE;
        }
        return SqlStringLiterals.decode(clause.STRING_LITERAL().getText());
    }

    /**
     * Refuses a conflict rule the tag would not take.
     *
     * @param allowedValues the tag's allowed values as the statement would leave them
     * @param propagate its propagation mode as the statement would leave it, or null for none
     * @param onConflict its conflict rule as the statement would leave it, or null for none
     */
    static void requireConsistent(final List<String> allowedValues, final String propagate, final String onConflict) {
        if (onConflict == null) {
            return;
        }
        if (propagate == null) {
            throw new RuntimeException(STRATEGY + "On Conflict can only be set when the PROPAGATE property is set");
        }
        final boolean hasAllowed = allowedValues != null && !allowedValues.isEmpty();
        if (SEQUENCE.equals(onConflict)) {
            if (!hasAllowed) {
                throw new RuntimeException(STRATEGY
                    + "On conflict as allowed_values_sequence requires allowed values to be added to the Tag");
            }
        } else if (hasAllowed && !allowedValues.contains(onConflict)) {
            throw new RuntimeException(STRATEGY + "On Conflict value must be part of Allowed Values if set");
        }
    }

    /**
     * Refuses an {@code ALTER TAG … DROP ALLOWED_VALUES} the tag's conflict rule would not survive: the
     * allowed-values sequence keeps at least one value, and a string rule stays one of the values that remain,
     * unless none remain.
     *
     * @param remaining the allowed values the statement would leave
     * @param propagate the tag's propagation mode, or null for none
     * @param onConflict the tag's conflict rule, or null for none
     */
    static void requireConsistentAfterDrop(final List<String> remaining, final String propagate,
                                           final String onConflict) {
        if (SEQUENCE.equals(onConflict) && remaining.isEmpty()) {
            throw new RuntimeException(STRATEGY + "Cannot drop allowed values with on_conflict strategy = "
                + "allowed_values_sequence as it requires allowed values to added for the tag");
        }
        requireConsistent(remaining, propagate, onConflict);
    }
}
