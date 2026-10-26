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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SessionParameterCatalog;
import dev.frostlake.executor.SessionParameterRow;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.ObjectParameters;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Judges the parameters an ALTER ACCOUNT or an ALTER USER names, as the account does (live-verified). Both levels
 * take every parameter the account lists and the ones a user alone carries, beside a session's own, and a list is
 * judged whole, in steps, each over all of its names in the order of their text:
 *
 * <ol>
 *   <li>a name written twice in the same spelling: {@code duplicate property '<name>';};</li>
 *   <li>a name no parameter has, a quoted name always being one: {@code invalid property '<name as written>' for
 *       'ACCOUNT'}, or {@code for 'USER'};</li>
 *   <li>a value of the wrong kind, the NUMBER parameters first, then the STRING ones, then the BOOLEAN ones:
 *       {@code invalid value [<as written>] for parameter '<NAME>'};</li>
 *   <li>a value of the right kind that a session parameter does not take, in the parameter's own sentence. A
 *       user's list is judged for it only once the user is found.</li>
 * </ol>
 *
 * <p>Nothing of a list applies when any of it is refused. A value is kept as the text SHOW PARAMETERS reports, and
 * NULL puts a parameter back on what its level inherits.
 */
final class LeveledParameterSettings {

    private static final String NUMBER = "NUMBER";
    private static final String FLOAT = "FLOAT";
    private static final String FALLBACK_WAREHOUSE = "FALLBACK_WAREHOUSE";

    private LeveledParameterSettings() {
    }

    /**
     * The kind of value a parameter of any level takes.
     *
     * @param name the parameter's name, upper case
     * @return BOOLEAN, NUMBER, FLOAT or STRING; null when no level has a parameter of that name
     */
    static String typeOf(final String name) {
        final String listed = CreatedObjectProperties.accountParameterType(name);
        if (listed != null) {
            return listed;
        }
        for (final SessionParameterRow row : SessionParameterCatalog.userOnlyRows()) {
            if (row.getName().equals(name)) {
                return row.getType();
            }
        }
        return null;
    }

    /**
     * The kind of value a parameter of any level takes, a session's own and a warehouse's own included: what an
     * ALTER of a schema or a database judges a value against before the level, so a session's BOOLEAN
     * TELEMETRY_ENABLE_INTERACTIVE_MODE = 'x' and a warehouse's FALLBACK_WAREHOUSE = 1 are invalid values there, not
     * access refusals (live-verified).
     *
     * @param name the parameter's name, upper case
     * @return BOOLEAN, NUMBER, FLOAT or STRING; null when no level has a parameter of that name
     */
    static String valueTypeOf(final String name) {
        final String leveled = typeOf(name);
        if (leveled != null) {
            return leveled;
        }
        final SessionParameterRow row = SessionParameterCatalog.find(name);
        if (row != null) {
            return row.getType();
        }
        // The warehouse parameter no session carries, typed as SHOW PARAMETERS IN WAREHOUSE lists it.
        return FALLBACK_WAREHOUSE.equals(name) ? "STRING" : null;
    }

    /**
     * The assignments of a SET, as written.
     *
     * @param assignments the assignments
     * @return a setting for each, not yet judged
     */
    static List<SessionParameterSetting> assignments(final List<FrostlakeParser.SessionAssignmentContext> assignments) {
        final List<SessionParameterSetting> settings = new ArrayList<SessionParameterSetting>();
        for (final FrostlakeParser.SessionAssignmentContext assignment : assignments) {
            settings.add(new SessionParameterSetting(SessionParameterSetting.writtenOf(assignment.sessionParameter()),
                assignment.sessionValue(), true));
        }
        return settings;
    }

    /**
     * The names of an UNSET, as written.
     *
     * @param names the names
     * @return a setting for each, none carrying a value
     */
    static List<SessionParameterSetting> names(final List<String> names) {
        final List<SessionParameterSetting> settings = new ArrayList<SessionParameterSetting>();
        for (final String name : names) {
            settings.add(new SessionParameterSetting(name, null, true));
        }
        return settings;
    }

    /**
     * Orders the names, then refuses a repeated one and one that is no parameter's — of several such, the first
     * written (live-verified: UNSET NOSUCH_A, NOSUCH_2 names NOSUCH_A).
     *
     * @param settings the settings, put in the order they are judged and applied in
     * @param level    ACCOUNT or USER, as the refusal names it
     */
    static void requireNames(final List<SessionParameterSetting> settings, final String level) {
        final List<SessionParameterSetting> written = new ArrayList<>(settings);
        Collections.sort(settings, new SessionParameterSettingOrder());
        for (int i = 1; i < settings.size(); i++) {
            if (settings.get(i).written().equals(settings.get(i - 1).written())) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(settings.get(i).written()));
            }
        }
        for (final SessionParameterSetting setting : written) {
            if (setting.key() == null) {
                throw ParameterRegistry.invalidProperty(setting.written(), level);
            }
        }
    }

    /**
     * Reads the values of the settings of one kind and refuses one that is of another.
     *
     * @param settings      the settings, in order
     * @param kind          NUMBER, STRING or BOOLEAN; NUMBER judges the FLOAT parameters with it
     * @param queryExecutor the executor, for the session's variables
     */
    static void requireKind(final List<SessionParameterSetting> settings, final String kind,
                            final QueryExecutor queryExecutor) {
        for (final SessionParameterSetting setting : settings) {
            if (isOfKind(setting, kind)) {
                judge(setting, queryExecutor);
            }
        }
    }

    /** Whether a setting is judged with the parameters of a kind. */
    static boolean isOfKind(final SessionParameterSetting setting, final String kind) {
        return kind.equals(setting.type()) || NUMBER.equals(kind) && FLOAT.equals(setting.type());
    }

    /** Reads one setting's value and refuses it when it is not of the parameter's kind. */
    static void judge(final SessionParameterSetting setting, final QueryExecutor queryExecutor) {
        setting.read(SessionParameterValue.read(setting.valueContext(), queryExecutor));
        setting.store(SessionParameterSettings.ofKind(setting));
    }

    /**
     * Refuses a value of the right kind that its session parameter does not take.
     *
     * @param settings the settings, their kinds judged
     * @param catalog  the catalog, for the schemas a SEARCH_PATH names
     */
    static void requireTaken(final List<SessionParameterSetting> settings, final Catalog catalog) {
        for (final SessionParameterSetting setting : settings) {
            if (setting.stored() != null && setting.isSessionParameter()) {
                SessionParameterSettings.requireTaken(setting, catalog);
            }
        }
    }

    /**
     * Writes judged settings to the level that keeps them.
     *
     * @param target   the account's parameters, or a user's
     * @param settings the settings, judged
     */
    static void set(final ObjectParameters target, final List<SessionParameterSetting> settings) {
        for (final SessionParameterSetting setting : settings) {
            if (setting.stored() == null) {
                target.unset(setting.key());
            } else {
                target.set(setting.key(), String.valueOf(setting.stored()));
            }
        }
    }

    /**
     * Removes the level's own value of each named parameter.
     *
     * @param target   the account's parameters, or a user's
     * @param settings the names, judged
     */
    static void unset(final ObjectParameters target, final List<SessionParameterSetting> settings) {
        for (final SessionParameterSetting setting : settings) {
            target.unset(setting.key());
        }
    }
}
