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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Judges the names and values of one ALTER SESSION SET or UNSET as a whole, so that nothing applies when any of
 * them is refused (live-verified). The statement's names are taken in the order of their text — an upper-case
 * letter before a lower-case one — whatever order they were written in, and each step below runs over all of
 * them before the next begins, which is what decides the refusal a statement with two faults reports:
 *
 * <ol>
 *   <li>a name written twice in the same spelling: {@code duplicate property '<name>';};</li>
 *   <li>a name that is no session parameter's, a quoted name always being one:
 *       {@code invalid parameter '<name as written>'};</li>
 *   <li>a value of the wrong kind, the NUMBER parameters first, then the STRING ones, then the BOOLEAN ones:
 *       {@code invalid value [<as written>] for parameter '<NAME>'}. A NUMBER takes a whole number of 32 bits,
 *       a STRING takes text, a BOOLEAN takes TRUE or FALSE, and each takes NULL, which unsets it;</li>
 *   <li>a value of the right kind that the parameter does not take — a number out of its range, a word outside
 *       its vocabulary, a time zone the account does not know — in the parameter's own sentence.</li>
 * </ol>
 *
 * <p>The same parameter under two spellings ({@code LOCK_TIMEOUT = 1, lock_timeout = 2}) is no duplicate: both
 * apply in that order, so the later text wins.
 */
final class SessionParameterSettings {

    private static final BigInteger SMALLEST = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger LARGEST = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final String[] KINDS = {"NUMBER", "STRING", "BOOLEAN"};
    /** The longest QUERY_TAG, in characters. */
    private static final int LONGEST_QUERY_TAG = 2000;
    private static final String CHECKPOINT_PREFIX = "CP$";

    private SessionParameterSettings() {
    }

    /**
     * The assignments of an ALTER SESSION SET in the order they apply, each with the value the session will
     * hold. Refuses the statement when any name or value is refused.
     *
     * @param assignments   the assignments as written
     * @param queryExecutor the executor, for the session's variables
     * @param catalog       the catalog, for the schemas a SEARCH_PATH names
     * @return the settings, judged
     */
    static List<SessionParameterSetting> judged(final List<FrostlakeParser.SessionAssignmentContext> assignments,
                                                final QueryExecutor queryExecutor, final Catalog catalog) {
        final List<SessionParameterSetting> settings = new ArrayList<SessionParameterSetting>();
        for (final FrostlakeParser.SessionAssignmentContext assignment : assignments) {
            settings.add(new SessionParameterSetting(assignment.sessionParameter(), assignment.sessionValue()));
        }
        requireNames(settings);
        for (final String kind : KINDS) {
            for (final SessionParameterSetting setting : settings) {
                if (kind.equals(setting.type())) {
                    setting.read(SessionParameterValue.read(setting.valueContext(), queryExecutor));
                    setting.store(ofKind(setting));
                }
            }
        }
        for (final SessionParameterSetting setting : settings) {
            if (setting.stored() != null) {
                requireTaken(setting, catalog);
            }
        }
        return settings;
    }

    /**
     * The names of an ALTER SESSION UNSET in the order they apply. Refuses the statement when any name is
     * refused.
     *
     * @param names the names as written
     * @return the settings, none carrying a value
     */
    static List<SessionParameterSetting> named(final List<FrostlakeParser.SessionParameterContext> names) {
        final List<SessionParameterSetting> settings = new ArrayList<SessionParameterSetting>();
        for (final FrostlakeParser.SessionParameterContext name : names) {
            settings.add(new SessionParameterSetting(name, null));
        }
        requireNames(settings);
        return settings;
    }

    /** Orders the names, then refuses a repeated one and one that is no session parameter's. */
    private static void requireNames(final List<SessionParameterSetting> settings) {
        Collections.sort(settings, new SessionParameterSettingOrder());
        for (int i = 1; i < settings.size(); i++) {
            if (settings.get(i).written().equals(settings.get(i - 1).written())) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(settings.get(i).written()));
            }
        }
        for (final SessionParameterSetting setting : settings) {
            if (setting.key() == null) {
                throw ParameterRegistry.invalidSessionParameter(setting.written());
            }
        }
    }

    /** The value the session will hold for a value of the parameter's kind; null for NULL. */
    static Object ofKind(final SessionParameterSetting setting) {
        final SessionParameterValue value = setting.value();
        if (value.kind() == SessionValueKind.NULL) {
            return null;
        }
        if (ParameterRegistry.takesNoValue("SESSION", setting.key())) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.written(), setting.key()));
        }
        if ("NUMBER".equals(setting.type())) {
            if (value.kind() == SessionValueKind.INTEGER && value.integer().compareTo(SMALLEST) >= 0
                    && value.integer().compareTo(LARGEST) <= 0) {
                return Long.valueOf(value.integer().longValue());
            }
        } else if ("STRING".equals(setting.type())) {
            if (value.kind() == SessionValueKind.TEXT) {
                return value.text();
            }
        } else if ("FLOAT".equals(setting.type())) {
            // Only parameters of the account are of this kind; a number of either form is one.
            if (value.kind() == SessionValueKind.INTEGER || value.kind() == SessionValueKind.DECIMAL) {
                return value.written();
            }
        } else if (value.kind() == SessionValueKind.BOOLEAN) {
            return Boolean.valueOf(value.flag());
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.written(), setting.key()));
    }

    /** Refuses a value of the right kind that the parameter does not take. */
    static void requireTaken(final SessionParameterSetting setting, final Catalog catalog) {
        final String key = setting.key();
        if ("NUMBER".equals(setting.type())) {
            TaskParameterValues.requireNumber(key, ((Long) setting.stored()).longValue());
            return;
        }
        if (!"STRING".equals(setting.type())) {
            return;
        }
        final String text = (String) setting.stored();
        switch (key) {
            case "SEARCH_PATH":
                // Each schema the path names is resolved now, as the ALTER runs, not when a later listing
                // reads the path.
                SearchPathValue.requireResolvable(catalog, text);
                return;
            case "QUERY_TAG":
                if (text.length() > LONGEST_QUERY_TAG) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
                }
                return;
            case "TIMESTAMP_NTZ_OUTPUT_FORMAT":
            case "TIMESTAMP_OUTPUT_FORMAT":
                // Neither takes AUTO, which the input formats take.
                if ("AUTO".equalsIgnoreCase(text)) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
                }
                return;
            case "VERSIONED_STAGE_ACTIVE_CHECKPOINT_ALIASES":
                requireCheckpointAliases(key, text);
                return;
            default:
                TaskParameterValues.requireText(key, text);
        }
    }

    /** A comma-separated list of aliases, each starting with CP$ in any case; an empty list is one. */
    private static void requireCheckpointAliases(final String key, final String text) {
        int start = 0;
        while (start <= text.length()) {
            final int comma = text.indexOf(',', start);
            final int end = comma < 0 ? text.length() : comma;
            final String alias = text.substring(start, end).trim();
            if (!alias.isEmpty() && !alias.regionMatches(true, 0, CHECKPOINT_PREFIX, 0, CHECKPOINT_PREFIX.length())) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                    "Each checkpoint alias must start with 'CP$', but found: " + alias.toUpperCase(Locale.ROOT),
                    key));
            }
            start = end + 1;
        }
    }
}
