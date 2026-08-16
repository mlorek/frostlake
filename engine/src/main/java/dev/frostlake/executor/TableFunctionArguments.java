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

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which arguments each table function actually takes, and how Snowflake refuses the rest.
 *
 * <p><b>Whether a table function accepts {@code NAME =&gt; value} at all is a property of the FUNCTION,
 * not of table functions in general.</b> FLATTEN takes five named arguments; SPLIT_TO_TABLE takes none
 * and answers {@code invalid argument for function [SPLIT_TO_TABLE] unexpected argument [STRING] at
 * position 1,} for the very spelling its parameters are documented under; GENERATOR sits at the other
 * extreme and silently IGNORES anything it does not recognise, positional arguments included. So each
 * function's surface is listed here from measurement, and a function with no entry is left alone.
 *
 * <p>All of these are COMPILE-time refusals on live, raised before a row is read, and all but one carry
 * the position of the function name.
 */
public final class TableFunctionArguments {

    private static final String FLATTEN = "FLATTEN";
    private static final String SPLIT_TO_TABLE = "SPLIT_TO_TABLE";
    private static final String TO_QUERY = "TO_QUERY";
    private static final String QUERY_HISTORY = "QUERY_HISTORY";
    private static final String TASK_HISTORY = "TASK_HISTORY";
    private static final String TAG_REFERENCES = "TAG_REFERENCES";

    /** The schema the INFORMATION_SCHEMA-only functions must be qualified with. */
    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    /**
     * The functions that exist ONLY under INFORMATION_SCHEMA. Their bare names resolve to nothing —
     * {@code TABLE(QUERY_HISTORY(…))} is {@code Invalid identifier QUERY_HISTORY} — while both
     * {@code INFORMATION_SCHEMA.QUERY_HISTORY} and {@code <db>.INFORMATION_SCHEMA.QUERY_HISTORY} run.
     */
    private static final Set<String> INFORMATION_SCHEMA_ONLY =
        new HashSet<>(Arrays.asList(QUERY_HISTORY, TASK_HISTORY, TAG_REFERENCES));

    /**
     * How live NAMES a positional parameter, by function. Most report the 1-based position
     * ({@code for parameter '1'}); the history functions report the parameter's own name instead, so
     * {@code QUERY_HISTORY(2)} blames {@code 'END_TIME_RANGE_START'}. Only the leading parameter of
     * each is listed — it is the one measured, and an unlisted position is left unchecked.
     */
    private static final Map<String, List<String>> POSITIONAL_PARAMETER_NAMES = new HashMap<>();

    /** FLATTEN's parameters, in the order its positional form fills them. */
    private static final List<String> FLATTEN_PARAMETERS =
        Arrays.asList("INPUT", "PATH", "OUTER", "RECURSIVE", "MODE");

    /** The named arguments each measured function accepts. An empty list means "none at all". */
    private static final Map<String, List<String>> ACCEPTED_NAMES = new HashMap<>();

    /** The type each measured parameter takes, by function then parameter name. */
    private static final Map<String, Map<String, TableFunctionParameterKind>> PARAMETER_KINDS =
        new HashMap<>();

    /**
     * The parameter each function's FIRST positional argument fills. It is not always INPUT:
     * TO_QUERY's is SQL, and {@code TO_QUERY(INPUT => …)} is refused for naming a parameter that does
     * not exist.
     */
    private static final Map<String, String> FIRST_POSITIONAL_PARAMETER = new HashMap<>();

    /** The parameter a call must supply, by name or positionally. */
    private static final Map<String, String> REQUIRED_PARAMETER = new HashMap<>();

    static {
        ACCEPTED_NAMES.put(FLATTEN, FLATTEN_PARAMETERS);
        ACCEPTED_NAMES.put(SPLIT_TO_TABLE, new ArrayList<String>());
        // TO_QUERY is deliberately absent: beyond SQL it takes ARBITRARY names, which bind to the
        // :name placeholders inside the text, so there is no list to check against.

        FIRST_POSITIONAL_PARAMETER.put(FLATTEN, "INPUT");
        FIRST_POSITIONAL_PARAMETER.put(TO_QUERY, "SQL");

        REQUIRED_PARAMETER.put(FLATTEN, "INPUT");
        REQUIRED_PARAMETER.put(TO_QUERY, "SQL");

        final Map<String, TableFunctionParameterKind> flatten = new HashMap<>();
        flatten.put("INPUT", TableFunctionParameterKind.SEMI_STRUCTURED);
        flatten.put("PATH", TableFunctionParameterKind.STRING);
        flatten.put("OUTER", TableFunctionParameterKind.BOOLEAN);
        flatten.put("RECURSIVE", TableFunctionParameterKind.BOOLEAN);
        flatten.put("MODE", TableFunctionParameterKind.STRING);
        PARAMETER_KINDS.put(FLATTEN, flatten);

        final Map<String, TableFunctionParameterKind> splitToTable = new HashMap<>();
        splitToTable.put("1", TableFunctionParameterKind.STRING);
        splitToTable.put("2", TableFunctionParameterKind.STRING);
        PARAMETER_KINDS.put(SPLIT_TO_TABLE, splitToTable);

        POSITIONAL_PARAMETER_NAMES.put(QUERY_HISTORY, Arrays.asList("END_TIME_RANGE_START"));
        POSITIONAL_PARAMETER_NAMES.put(TASK_HISTORY, Arrays.asList("SCHEDULED_TIME_RANGE_START"));

        final Map<String, TableFunctionParameterKind> queryHistory = new HashMap<>();
        queryHistory.put("END_TIME_RANGE_START", TableFunctionParameterKind.TIMESTAMP);
        PARAMETER_KINDS.put(QUERY_HISTORY, queryHistory);

        final Map<String, TableFunctionParameterKind> taskHistory = new HashMap<>();
        taskHistory.put("SCHEDULED_TIME_RANGE_START", TableFunctionParameterKind.TIMESTAMP);
        PARAMETER_KINDS.put(TASK_HISTORY, taskHistory);
    }

    /**
     * Refuse a function that only exists under INFORMATION_SCHEMA when the call did not say so. The
     * qualifier is checked rather than discarded: Frostlake resolved these by their last name part, so
     * a bare {@code QUERY_HISTORY(…)} ran where live answers an invalid identifier.
     *
     * @param schemaQualifier the CANONICAL schema part written immediately before the function's own
     *                        name (quoted spellings already unwrapped by the parse-tree reader), or
     *                        empty when the call is unqualified
     */
    public static void validateQualification(final String functionName, final String schemaQualifier) {
        if (!INFORMATION_SCHEMA_ONLY.contains(functionName)) {
            return;
        }
        if (!INFORMATION_SCHEMA.equalsIgnoreCase(schemaQualifier)) {
            throw new RuntimeException(SqlCompilationError.of("Invalid identifier " + functionName));
        }
    }

    private TableFunctionArguments() {
    }

    /**
     * Refuse a named argument the function does not take, and a required one the call never supplied.
     *
     * @param functionName the function as written, unqualified and upper-cased
     * @param nameToken    the function-name token, whose position the refusal reports
     * @param namesInOrder one entry per argument in call order — the name as written and upper-cased,
     *                     or null where the argument was passed positionally
     */
    public static void validateNames(final String functionName, final Token nameToken,
                                     final List<String> namesInOrder) {
        final List<String> accepted = ACCEPTED_NAMES.get(functionName);
        if (accepted != null) {
            for (int i = 0; i < namesInOrder.size(); i++) {
                final String written = namesInOrder.get(i);
                if (written != null && !accepted.contains(unquoted(written))) {
                    throw new RuntimeException(at(nameToken, "invalid argument for function ["
                        + functionName + "] unexpected argument [" + written + "] at position "
                        + (i + 1) + ","));
                }
            }
        }
        final String required = REQUIRED_PARAMETER.get(functionName);
        if (required != null && !namesInOrder.isEmpty() && !supplies(namesInOrder, required)) {
            throw new RuntimeException(at(nameToken,
                "missing required argument [" + required + "] for function [" + functionName + "]"));
        }
    }

    /**
     * Refuse a call whose positional argument count the function does not accept.
     *
     * <p>SPLIT_TO_TABLE takes exactly two, and live spells the three refusals three different ways —
     * including, for the one-argument call, a message that names the internal SPLIT rewrite the
     * arguments were folded into ({@code [SPLIT('a,b' AS "1")]}) and carries no position at all. The
     * rewritten call renders a literal argument exactly as written, which is the shape this reproduces.
     *
     * @param argumentTexts the source text of each positional argument, in order
     */
    public static void validateArity(final String functionName, final Token nameToken,
                                     final List<String> argumentTexts) {
        if (TO_QUERY.equals(functionName) && argumentTexts.isEmpty()) {
            throw new RuntimeException(at(nameToken, "not enough arguments for function ["
                + functionName + "], expected 1, got 0"));
        }
        if (!SPLIT_TO_TABLE.equals(functionName)) {
            return;
        }
        final int given = argumentTexts.size();
        if (given == 0) {
            throw new RuntimeException(at(nameToken, "not enough arguments for function ["
                + functionName + "], expected 1, got 0"));
        }
        if (given == 1) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "not enough arguments for function"
                + " [SPLIT(" + argumentTexts.get(0) + " AS \"1\")], expected 2, got 1"));
        }
        if (given > 2) {
            throw new RuntimeException(at(nameToken, "too many arguments for function ["
                + functionName + "] expected 2, got " + given));
        }
    }

    /** The type a named parameter takes, or null when the function's surface has not been measured. */
    public static TableFunctionParameterKind kindOfNamed(final String functionName, final String parameterName) {
        final Map<String, TableFunctionParameterKind> kinds = PARAMETER_KINDS.get(functionName);
        return kinds == null ? null : kinds.get(unquoted(parameterName));
    }

    /**
     * The type the {@code index}-th positional argument takes (0-based). Live names such a parameter by
     * its POSITION rather than its name — {@code FLATTEN('[1,2,3]')} is
     * {@code invalid type [VARCHAR(7)] for parameter '1'} — which is what {@link #nameOfPositional}
     * spells, while the type rule itself is the named parameter's.
     */
    public static TableFunctionParameterKind kindOfPositional(final String functionName, final int index) {
        if (FLATTEN.equals(functionName)) {
            return index < FLATTEN_PARAMETERS.size()
                ? kindOfNamed(functionName, FLATTEN_PARAMETERS.get(index)) : null;
        }
        return kindOfNamed(functionName, nameOfPositional(functionName, index));
    }

    /**
     * How live names a positional parameter in a type refusal — its 1-based position for most
     * functions, but the parameter's own name for the ones that spell it out.
     */
    public static String nameOfPositional(final String functionName, final int index) {
        final List<String> names = POSITIONAL_PARAMETER_NAMES.get(functionName);
        return names != null && index < names.size() ? names.get(index) : String.valueOf(index + 1);
    }

    /**
     * The parameter a leading positional argument fills. INPUT for most, but not all — TO_QUERY's
     * first parameter is SQL, so a call naming it INPUT is missing its required argument.
     */
    public static String firstPositionalParameter(final String functionName) {
        final String named = FIRST_POSITIONAL_PARAMETER.get(functionName);
        return named == null ? "INPUT" : named;
    }

    /** A required parameter may arrive by name or as the leading positional argument. */
    private static boolean supplies(final List<String> namesInOrder, final String required) {
        for (final String written : namesInOrder) {
            if (written == null || required.equals(unquoted(written))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A quoted argument name keeps its quotes in the refusal ({@code ["STRING"]}) but is matched
     * against the parameter list without them — and live upper-cases either spelling.
     */
    private static String unquoted(final String written) {
        return written.length() > 1 && written.startsWith("\"") && written.endsWith("\"")
            ? written.substring(1, written.length() - 1) : written;
    }

    private static String at(final Token nameToken, final String detail) {
        return SqlCompilationError.at(nameToken.getLine(), nameToken.getCharPositionInLine(), detail);
    }
}
