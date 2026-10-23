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

import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;

/**
 * The names a statement sets or unsets on a database, a schema or a table, judged before any is applied (all
 * live-verified). A name that is neither a property of the object nor any parameter the account knows is an invalid
 * property of its kind, the first of a table hashed by name; then a parameter of another level is refused — by ALTER
 * as an operation the role may not run on the object, whatever the value ({@code SET TIMEZONE = 'UTC'} on a schema is
 * "Insufficient privileges to operate on schema 'TEST_SCHEMA'."), by CREATE as {@code cannot set parameter
 * 'TIMEZONE' at 'SCHEMA'}.
 */
final class ParameterLevels {

    /** The property naming a container's classification profile, which no parameter list holds. */
    static final String CLASSIFICATION_PROFILE = "CLASSIFICATION_PROFILE";

    private ParameterLevels() {
    }

    /**
     * Refuses the classification profile a database or a schema is given: the profile must exist, and none does
     * here, so the value is refused in the account's words, {@code invalid value 'P' for property 'bundle instance
     * name'}, at CREATE and at ALTER … SET alike (live-verified). Unsetting one is taken.
     *
     * @param key   the property's name, canonical
     * @param value its value as written
     */
    static void refuseClassificationProfile(final String key, final FrostlakeParser.CopyOptionValueContext value) {
        if (CLASSIFICATION_PROFILE.equals(key) && value != null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForProperty(NamespaceProperties.render(value),
                "bundle instance name"));
        }
    }

    /**
     * Judges an ALTER's names.
     *
     * @param names          the names as written
     * @param taken          per name, whether the object takes it: its own property or a parameter of its level
     * @param kind           the object's kind as an invalid property names it: SCHEMA, DATABASE, TABLE
     * @param privilegeKind  the kind as the access refusal names it: schema, database, table
     * @param objectName     the object's name as the access refusal spells it
     */
    static void judgeAlter(final List<String> names, final List<Boolean> taken, final String kind,
                           final String privilegeKind, final String objectName) {
        judgeAlter(names, taken, null, kind, privilegeKind, objectName);
    }

    /**
     * Judges an ALTER's names and the values it sets them to: after an unknown name, a value of another kind than its
     * parameter's type is refused as written - "invalid value ['abc'] for parameter
     * 'INITIAL_REPLICATION_SIZE_LIMIT_IN_TB'" - before the object's level or the role's access is judged, a parameter
     * the object takes included (live-verified).
     *
     * @param names          the names as written
     * @param taken          per name, whether the object takes it: its own property or a parameter of its level
     * @param values         per name, the value written or null; null when no value is judged
     * @param kind           the object's kind as an invalid property names it: SCHEMA, DATABASE, TABLE, ALERT
     * @param privilegeKind  the kind as the access refusal names it: schema, database, table, alert
     * @param objectName     the object's name as the access refusal spells it
     */
    static void judgeAlter(final List<String> names, final List<Boolean> taken,
                           final List<WrittenParameterValue> values, final String kind, final String privilegeKind,
                           final String objectName) {
        final List<Integer> order = rejectUnknown(names, taken, kind);
        if (values != null) {
            for (final Integer i : order) {
                final WrittenParameterValue value = values.get(i);
                final String canonical = ParameterRegistry.canonical(names.get(i));
                if (value != null && !value.fits(LeveledParameterSettings.typeOf(canonical))) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.written(),
                        canonical));
                }
            }
        }
        for (final Integer i : order) {
            final String canonical = ParameterRegistry.canonical(names.get(i));
            if (ParameterRegistry.isUnsettable(kind, canonical)) {
                // A table or an alert refuses a few parameters at its own level, in CREATE's words (live-verified).
                throw new RuntimeException(SqlCompilationError.of("cannot set parameter '" + canonical
                    + "' at '" + kind + "'"));
            }
            if (!taken.get(i).booleanValue()) {
                throw new RuntimeException(SqlAccessControlError.insufficientPrivileges(privilegeKind, objectName));
            }
        }
    }

    /**
     * Refuses the first name, in the hashed order, that is neither taken nor any parameter, before the object is
     * looked for: ALTER TABLE nosuch SET foo = 1 names the property, not the table (live-verified).
     *
     * @param names the names as written
     * @param taken per name, whether the object's kind takes it
     * @param kind  the object's kind as an invalid property names it
     */
    static void rejectUnknownNames(final List<String> names, final List<Boolean> taken, final String kind) {
        rejectUnknown(names, taken, kind);
    }

    /**
     * Judges a CREATE's names.
     *
     * @param names the names as written
     * @param taken per name, whether the object takes it
     * @param kind  the object's kind: SCHEMA, DATABASE
     */
    static void judgeCreate(final List<String> names, final List<Boolean> taken, final String kind) {
        final List<Integer> order = rejectUnknown(names, taken, kind);
        for (final Integer i : order) {
            if (!taken.get(i).booleanValue()) {
                throw new RuntimeException(SqlCompilationError.of("cannot set parameter '"
                    + ParameterRegistry.canonical(names.get(i)) + "' at '" + kind + "'"));
            }
        }
    }

    /** Refuses the first name, in the hashed order, that is neither taken nor any parameter; answers that order. */
    private static List<Integer> rejectUnknown(final List<String> names, final List<Boolean> taken, final String kind) {
        final List<Integer> order = CreatedObjectProperties.judgedOrder(names);
        for (final Integer i : order) {
            if (!taken.get(i).booleanValue()
                    && !CreatedObjectProperties.isParameter(ParameterRegistry.canonical(names.get(i)))) {
                throw ParameterRegistry.invalidProperty(ParameterRegistry.spell(names.get(i)), kind);
            }
        }
        return order;
    }
}
