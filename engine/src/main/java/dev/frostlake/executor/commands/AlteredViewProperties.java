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
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an ALTER VIEW or an ALTER MATERIALIZED VIEW sets or unsets, judged before the view is looked for, IF EXISTS
 * or not (live-verified). A SET reads a list of {@code name = value} items, blank- or comma-separated, and an UNSET a
 * comma list of names, any name; a statement with several faults is answered for the first class in this order:
 *
 * <pre>
 *   SET COMMENT                  syntax error at the list's first name: an item without its value
 *   SET SECURE COMMENT = 'x'     syntax error at what follows SECURE, which stands alone
 *   UNSET SECURE, COMMENT        syntax error at the comma: UNSET SECURE is the statement that makes a view plain
 *   SET COMMENT = 'a' COMMENT = 'b'   duplicate property 'COMMENT'; of a view, while a materialized view takes the
 *                                last one written; an UNSET's repeated name is refused for both
 *   SET FOO = 1                  invalid property 'FOO' for 'VIEW' (for 'MATERIALIZED_VIEW'), SECURE = TRUE and a
 *                                materialized view's CHANGE_TRACKING among them; of several the first of a table
 *                                hashed by name
 *   SET COMMENT = 5              invalid value [5] for parameter 'COMMENT'
 *   SET CHANGE_TRACKING = NULL   invalid value 'null' for property 'CHANGE_TRACKING'
 *   SET TIMEZONE = 'UTC'         Insufficient privileges to operate on view 'V0'.  any parameter, set or unset
 * </pre>
 *
 * A value that is a reserved word ({@code SET COMMENT = ON}) is a syntax error at the list's first name, as an item
 * without its value is. A view's COMMENT, CHANGE_TRACKING and DATA_METRIC_SCHEDULE are its properties, a materialized
 * view's COMMENT alone; {@code COMMENT = NULL} takes the comment away.
 */
final class AlteredViewProperties {

    private static final String COMMENT = "COMMENT";
    private static final String CHANGE_TRACKING = "CHANGE_TRACKING";
    private static final String DATA_METRIC_SCHEDULE = "DATA_METRIC_SCHEDULE";

    private AlteredViewProperties() {
    }

    /**
     * Judges a SET's items.
     *
     * @param items        the items, in the order written
     * @param materialized whether the statement alters a materialized view
     * @param name         the view's name, as the access refusal spells it
     * @param sql          the statement, for a syntax error
     */
    static void judgeSet(final List<FrostlakeParser.ViewSetItemContext> items, final boolean materialized,
                         final String name, final String sql) {
        for (final FrostlakeParser.ViewSetItemContext item : items) {
            if (item.copyOptionValue() == null && item.NULL() == null) {
                final boolean secureFirst = !hasValue(items.get(0)) && items.size() > 1
                    && "SECURE".equalsIgnoreCase(items.get(0).optionKey().getText());
                throw syntaxError(items.get(secureFirst ? 1 : 0).getStart(), sql);
            }
        }
        final List<String> names = new ArrayList<String>();
        for (final FrostlakeParser.ViewSetItemContext item : items) {
            names.add(item.optionKey().getText());
        }
        if (!materialized) {
            rejectDuplicates(names);
        }
        final List<Integer> order = CreatedObjectProperties.judgedOrder(names);
        rejectUnknown(names, order, materialized);
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            final FrostlakeParser.CopyOptionValueContext value = items.get(i).copyOptionValue();
            if (value == null) {
                // NULL: a view's change tracking takes no NULL, in a sentence of its own (live-verified).
                if (CHANGE_TRACKING.equals(key)) {
                    throw new RuntimeException(SqlCompilationError.of("invalid value 'null' for property '"
                        + CHANGE_TRACKING + "'"));
                }
                continue;
            }
            final boolean number = value.INTEGER_LITERAL() != null;
            final boolean fits = CHANGE_TRACKING.equals(key) ? value.booleanValue() != null
                : !COMMENT.equals(key) || !number && value.booleanValue() == null;
            if (!fits) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), key));
            }
        }
        rejectParameters(names, order, materialized, name);
    }

    /**
     * Whether a judged SET sets the comment.
     *
     * @param items the items, judged
     * @return whether one of them is COMMENT
     */
    static boolean setsComment(final List<FrostlakeParser.ViewSetItemContext> items) {
        return lastItem(items, COMMENT) != null;
    }

    /**
     * The comment a judged SET gives, the last one written: null for {@code COMMENT = NULL}, which takes it away.
     *
     * @param items the items, judged
     * @return the comment, or null
     */
    static String commentOf(final List<FrostlakeParser.ViewSetItemContext> items) {
        final FrostlakeParser.ViewSetItemContext item = lastItem(items, COMMENT);
        if (item == null || item.copyOptionValue() == null) {
            return null;
        }
        final FrostlakeParser.CopyOptionValueContext value = item.copyOptionValue();
        return value.STRING_LITERAL() != null ? SqlStringLiterals.decode(value.STRING_LITERAL().getText())
            : value.getText();
    }

    /**
     * The change tracking a judged SET of a view gives, the last one written, or null when it gives none.
     *
     * @param items the items, judged
     * @return TRUE, FALSE or null
     */
    static Boolean changeTrackingOf(final List<FrostlakeParser.ViewSetItemContext> items) {
        final FrostlakeParser.ViewSetItemContext item = lastItem(items, CHANGE_TRACKING);
        if (item == null || item.copyOptionValue() == null || item.copyOptionValue().booleanValue() == null) {
            return null;
        }
        return Boolean.valueOf(item.copyOptionValue().booleanValue().TRUE() != null);
    }

    /**
     * Whether a judged UNSET takes a view's change tracking away.
     *
     * @param keys the names, judged
     * @return whether one of them is CHANGE_TRACKING
     */
    static boolean unsetsChangeTracking(final List<FrostlakeParser.OptionKeyContext> keys) {
        for (final FrostlakeParser.OptionKeyContext key : keys) {
            if (CHANGE_TRACKING.equals(ParameterRegistry.canonical(key.getText()))) {
                return true;
            }
        }
        return false;
    }

    private static FrostlakeParser.ViewSetItemContext lastItem(final List<FrostlakeParser.ViewSetItemContext> items,
                                                               final String key) {
        FrostlakeParser.ViewSetItemContext last = null;
        for (final FrostlakeParser.ViewSetItemContext item : items) {
            if (key.equals(ParameterRegistry.canonical(item.optionKey().getText()))) {
                last = item;
            }
        }
        return last;
    }

    /** Whether an item carries a value, NULL included. */
    private static boolean hasValue(final FrostlakeParser.ViewSetItemContext item) {
        return item.copyOptionValue() != null || item.NULL() != null;
    }

    /**
     * Judges an UNSET's names and answers whether it unsets the comment.
     *
     * @param keys         the names, in the order written
     * @param firstComma   the comma after the first name, or null for a single name
     * @param materialized whether the statement alters a materialized view
     * @param name         the view's name, as the access refusal spells it
     * @param sql          the statement, for a syntax error
     * @return whether the comment is unset
     */
    static boolean judgeUnset(final List<FrostlakeParser.OptionKeyContext> keys, final Token firstComma,
                              final boolean materialized, final String name, final String sql) {
        final List<String> names = new ArrayList<String>();
        for (final FrostlakeParser.OptionKeyContext key : keys) {
            names.add(key.getText());
        }
        if (firstComma != null && "SECURE".equalsIgnoreCase(names.get(0)) && !names.get(0).startsWith("\"")) {
            throw syntaxError(firstComma, sql);
        }
        rejectDuplicates(names);
        final List<Integer> order = CreatedObjectProperties.judgedOrder(names);
        rejectUnknown(names, order, materialized);
        rejectParameters(names, order, materialized, name);
        for (final String written : names) {
            if (COMMENT.equals(ParameterRegistry.canonical(written))) {
                return true;
            }
        }
        return false;
    }

    private static void rejectDuplicates(final List<String> names) {
        final Set<String> seen = new HashSet<String>();
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
            if (!seen.add(key)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(
                    isProperty(key, false) || CreatedObjectProperties.isParameter(key) ? key : name));
            }
        }
    }

    /** A name that is neither a property of the view nor a parameter, refused as it is written. */
    private static void rejectUnknown(final List<String> names, final List<Integer> order, final boolean materialized) {
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (!isProperty(key, materialized) && !CreatedObjectProperties.isParameter(key)) {
                throw ParameterRegistry.invalidProperty(ParameterRegistry.spell(names.get(i)),
                    materialized ? "MATERIALIZED_VIEW" : "VIEW");
            }
        }
    }

    /** A parameter is no view's to set: the statement is refused as one the role may not run on it. */
    private static void rejectParameters(final List<String> names, final List<Integer> order,
                                         final boolean materialized, final String name) {
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (!isProperty(key, materialized)) {
                throw new RuntimeException(SqlAccessControlError.insufficientPrivileges(
                    materialized ? "materialized_view" : "view", name));
            }
        }
    }

    private static boolean isProperty(final String key, final boolean materialized) {
        return COMMENT.equals(key)
            || !materialized && (CHANGE_TRACKING.equals(key) || DATA_METRIC_SCHEDULE.equals(key));
    }

    /**
     * The schedule a judged SET gives the view's data metric functions, as written.
     *
     * @param items the items, judged
     * @return the last DATA_METRIC_SCHEDULE's text, or null when none is set
     */
    static String dataMetricScheduleOf(final List<FrostlakeParser.ViewSetItemContext> items) {
        final FrostlakeParser.ViewSetItemContext item = lastItem(items, DATA_METRIC_SCHEDULE);
        return item == null || item.copyOptionValue() == null ? null
            : SqlStringLiterals.decode(item.copyOptionValue().getText());
    }

    /**
     * Whether a judged UNSET takes the view's data metric schedule away.
     *
     * @param keys the names, judged
     * @return whether one of them is DATA_METRIC_SCHEDULE
     */
    static boolean unsetsDataMetricSchedule(final List<FrostlakeParser.OptionKeyContext> keys) {
        for (final FrostlakeParser.OptionKeyContext key : keys) {
            if (DATA_METRIC_SCHEDULE.equals(ParameterRegistry.canonical(key.getText()))) {
                return true;
            }
        }
        return false;
    }

    /** The syntax error at a token, the one line the account gives. */
    private static SqlSyntaxException syntaxError(final Token at, final String sql) {
        final String line = "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
            + " unexpected '" + at.getText() + "'.";
        return new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line), sql);
    }
}
