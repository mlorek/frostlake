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
import dev.frostlake.parser.FrostlakeParser;
import java.util.List;

/**
 * The properties written after a PRIMARY KEY, UNIQUE or FOREIGN KEY constraint, and what they come to. The
 * account reads them in any order and repeated, and then (live-verified, alike for the three kinds, written in a
 * column definition or at table level, in a CREATE TABLE or an ALTER TABLE … ADD):
 *
 * <pre>
 *   RELY NORELY, ENABLE DISABLE, DEFERRABLE NOT DEFERRABLE,
 *   INITIALLY DEFERRED INITIALLY IMMEDIATE      Conflicting constraint properties      either order
 *   NOT DEFERRABLE INITIALLY DEFERRED           Constraint is not deferrable           either order
 *   ENFORCED NOT ENFORCED                       taken: the constraint is not enforced whatever is written
 * </pre>
 *
 * A constraint the account cannot keep as written is left out without a word, and the statement succeeds: one
 * that is DEFERRABLE, INITIALLY IMMEDIATE, ENABLE or VALIDATE. A foreign key is left out as well for a MATCH
 * other than FULL and for an ON UPDATE or ON DELETE rule other than NO ACTION. The other properties — NOT
 * ENFORCED, ENFORCED, NOT DEFERRABLE, INITIALLY DEFERRED, DISABLE, NOVALIDATE — change nothing, and RELY or
 * NORELY is the one that is kept.
 */
final class KeyConstraintProperties {

    private static final String DEFERRABLE = "DEFERRABLE";

    private Boolean rely;
    private boolean leftOut;

    private KeyConstraintProperties() {
    }

    /**
     * Judges the properties of one constraint.
     *
     * @param written the properties, in the order written
     * @return what they come to
     */
    static KeyConstraintProperties read(final List<FrostlakeParser.KeyConstraintPropertyContext> written) {
        final KeyConstraintProperties properties = new KeyConstraintProperties();
        Boolean enabled = null;
        Boolean deferrable = null;
        Boolean immediate = null;
        boolean validated = false;
        for (final FrostlakeParser.KeyConstraintPropertyContext property : written) {
            if (property.ENFORCED() != null) {
                continue;
            }
            if (property.relyOption() != null) {
                properties.rely = settled(properties.rely, property.relyOption().RELY() != null);
            } else if (property.ENABLE() != null || property.DISABLE() != null) {
                enabled = settled(enabled, property.ENABLE() != null);
                validated = validated
                    || property.keyValidation() != null && property.keyValidation().NOVALIDATE() == null;
            } else if (property.keyInitially() != null) {
                immediate = settled(immediate, property.keyInitially().IMMEDIATE() != null);
            } else if (property.NOT() == null && !DEFERRABLE.equalsIgnoreCase(property.identifier().getText())) {
                // INITIALLY with neither DEFERRED nor IMMEDIATE after it.
                throw SyntaxAfter.unexpected(property);
            } else {
                deferrable = settled(deferrable, property.NOT() == null);
            }
        }
        if (Boolean.FALSE.equals(deferrable) && Boolean.FALSE.equals(immediate)) {
            throw new RuntimeException(SqlCompilationError.of("Constraint is not deferrable"));
        }
        properties.leftOut = Boolean.TRUE.equals(deferrable) || Boolean.TRUE.equals(immediate)
            || Boolean.TRUE.equals(enabled) || validated;
        return properties;
    }

    /** A property's value, which a second writing may repeat and may not contradict. */
    private static Boolean settled(final Boolean before, final boolean now) {
        if (before != null && before.booleanValue() != now) {
            throw new RuntimeException(SqlCompilationError.of("Conflicting constraint properties"));
        }
        return Boolean.valueOf(now);
    }

    /** RELY or NORELY as written, or null when neither was. */
    Boolean rely() {
        return rely;
    }

    /** Whether the constraint is left out of the table although its statement succeeds. */
    boolean leavesOut() {
        return leftOut;
    }

    /**
     * Whether a foreign key's MATCH leaves the key out: any but FULL does.
     *
     * @param match the MATCH clause, or null when the key has none
     * @return whether the key is left out
     */
    static boolean matchLeavesOut(final FrostlakeParser.ForeignKeyMatchContext match) {
        if (match == null) {
            return false;
        }
        if (match.foreignKeyMatchKind() == null) {
            throw SyntaxAfter.unexpected(match);
        }
        return match.foreignKeyMatchKind().FULL() == null;
    }
}
