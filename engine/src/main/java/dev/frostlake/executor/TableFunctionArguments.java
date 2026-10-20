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
import java.util.Locale;
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
    private static final String STRTOK_SPLIT_TO_TABLE = "STRTOK_SPLIT_TO_TABLE";
    private static final String TO_QUERY = "TO_QUERY";
    private static final String QUERY_HISTORY = "QUERY_HISTORY";
    private static final String TASK_HISTORY = "TASK_HISTORY";
    private static final String TAG_REFERENCES = "TAG_REFERENCES";
    private static final String TAG_REFERENCES_ALL_COLUMNS = "TAG_REFERENCES_ALL_COLUMNS";
    private static final String DATA_METRIC_FUNCTION_REFERENCES = "DATA_METRIC_FUNCTION_REFERENCES";
    private static final String ALERT_HISTORY = "ALERT_HISTORY";
    private static final String COPY_HISTORY = "COPY_HISTORY";
    private static final String GENERATOR = "GENERATOR";

    /** The schema the INFORMATION_SCHEMA-only functions must be qualified with. */
    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    /**
     * The functions that exist ONLY under INFORMATION_SCHEMA. Their bare names resolve to nothing —
     * {@code TABLE(QUERY_HISTORY(…))} is {@code Invalid identifier QUERY_HISTORY} — while both
     * {@code INFORMATION_SCHEMA.QUERY_HISTORY} and {@code <db>.INFORMATION_SCHEMA.QUERY_HISTORY} run.
     */
    private static final Set<String> INFORMATION_SCHEMA_ONLY =
        new HashSet<>(Arrays.asList(QUERY_HISTORY, "QUERY_HISTORY_BY_SESSION", "QUERY_HISTORY_BY_USER",
            "QUERY_HISTORY_BY_WAREHOUSE", TASK_HISTORY, TAG_REFERENCES, "TAG_REFERENCES_ALL_COLUMNS",
            ALERT_HISTORY, COPY_HISTORY, "TASK_DEPENDENTS",
            "CURRENT_TASK_GRAPHS", "COMPLETE_TASK_GRAPHS", "SERVERLESS_TASK_HISTORY"));

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
        ACCEPTED_NAMES.put(STRTOK_SPLIT_TO_TABLE, new ArrayList<String>());
        ACCEPTED_NAMES.put(InferSchemaArguments.FUNCTION, InferSchemaArguments.PARAMETERS);
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

        // The tokenizing sibling takes the same two texts, the second one a SET of delimiter characters.
        final Map<String, TableFunctionParameterKind> strtokSplitToTable = new HashMap<>();
        strtokSplitToTable.put("1", TableFunctionParameterKind.STRING);
        strtokSplitToTable.put("2", TableFunctionParameterKind.STRING);
        PARAMETER_KINDS.put(STRTOK_SPLIT_TO_TABLE, strtokSplitToTable);

        POSITIONAL_PARAMETER_NAMES.put(QUERY_HISTORY, Arrays.asList("END_TIME_RANGE_START"));
        POSITIONAL_PARAMETER_NAMES.put(TASK_HISTORY, Arrays.asList("SCHEDULED_TIME_RANGE_START"));

        final Map<String, TableFunctionParameterKind> generator = new HashMap<>();
        generator.put("ROWCOUNT", TableFunctionParameterKind.NUMBER);
        generator.put("TIMELIMIT", TableFunctionParameterKind.NUMBER);
        PARAMETER_KINDS.put(GENERATOR, generator);

        // Both arguments of the tag-reference functions are texts, named in a refusal by the parameter.
        final Map<String, TableFunctionParameterKind> tagReferences = new HashMap<>();
        tagReferences.put("OBJECT_NAME", TableFunctionParameterKind.STRING);
        tagReferences.put("DOMAIN", TableFunctionParameterKind.STRING);
        for (final String function : Arrays.asList(TAG_REFERENCES, TAG_REFERENCES_ALL_COLUMNS)) {
            POSITIONAL_PARAMETER_NAMES.put(function, Arrays.asList("OBJECT_NAME", "DOMAIN"));
            PARAMETER_KINDS.put(function, tagReferences);
        }

        // The metric a DATA_METRIC_FUNCTION_REFERENCES call names is a text, and so are its entity and its domain.
        ACCEPTED_NAMES.put(DATA_METRIC_FUNCTION_REFERENCES,
            Arrays.asList("METRIC_NAME", "REF_ENTITY_NAME", "REF_ENTITY_DOMAIN"));
        final Map<String, TableFunctionParameterKind> metricReferences = new HashMap<>();
        metricReferences.put("METRIC_NAME", TableFunctionParameterKind.STRING);
        metricReferences.put("REF_ENTITY_NAME", TableFunctionParameterKind.STRING);
        metricReferences.put("REF_ENTITY_DOMAIN", TableFunctionParameterKind.STRING);
        PARAMETER_KINDS.put(DATA_METRIC_FUNCTION_REFERENCES, metricReferences);
        // One argument written without a name is the metric's, and is told by that name.
        POSITIONAL_PARAMETER_NAMES.put(DATA_METRIC_FUNCTION_REFERENCES, Arrays.asList("METRIC_NAME"));

        final Map<String, TableFunctionParameterKind> queryHistory = new HashMap<>();
        queryHistory.put("END_TIME_RANGE_START", TableFunctionParameterKind.TIMESTAMP);
        PARAMETER_KINDS.put(QUERY_HISTORY, queryHistory);

        final Map<String, TableFunctionParameterKind> taskHistory = new HashMap<>();
        taskHistory.put("SCHEDULED_TIME_RANGE_START", TableFunctionParameterKind.TIMESTAMP);
        PARAMETER_KINDS.put(TASK_HISTORY, taskHistory);

        // COPY_HISTORY's table is a text and its range two timestamps, a positional call filling them in that order.
        final Map<String, TableFunctionParameterKind> copyHistory = new HashMap<>();
        copyHistory.put("TABLE_NAME", TableFunctionParameterKind.STRING);
        copyHistory.put("START_TIME", TableFunctionParameterKind.TIMESTAMP);
        copyHistory.put("END_TIME", TableFunctionParameterKind.TIMESTAMP);
        PARAMETER_KINDS.put(COPY_HISTORY, copyHistory);
        POSITIONAL_PARAMETER_NAMES.put(COPY_HISTORY, Arrays.asList("TABLE_NAME", "START_TIME", "END_TIME"));
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
                // A quoted name matches no parameter, whatever it spells: FLATTEN("INPUT" => …) is refused.
                if (written != null && !accepted.contains(written)) {
                    // DATA_METRIC_FUNCTION_REFERENCES is named by the first part of the call's name, the schema
                    // the function is read from.
                    final String shown = DATA_METRIC_FUNCTION_REFERENCES.equals(functionName)
                        ? nameToken.getText().toUpperCase(Locale.ROOT) : echoed(functionName, nameToken);
                    throw new RuntimeException(at(nameToken, "invalid argument for function ["
                        + shown + "] unexpected argument [" + written + "] at position "
                        + (i + 1) + ","));
                }
            }
        }
        final String required = REQUIRED_PARAMETER.get(functionName);
        if (required != null && !namesInOrder.isEmpty() && !supplies(namesInOrder, required)) {
            throw new RuntimeException(at(nameToken, "missing required argument [" + required + "] for function ["
                + echoed(functionName, nameToken) + "]"));
        }
    }

    /**
     * COPY_HISTORY takes its table and its start time, by name or position: a call that gives fewer arguments is
     * refused for the count, and a table given as NULL as missing, each naming the first part of the call's name;
     * TO_QUERY's text given as {@code SQL => NULL} is missing too (live-verified).
     *
     * @param functionName  the function, unqualified and upper-cased
     * @param nameToken     the first token of the call's name, whose position the refusal reports
     * @param namesInOrder  each argument's name as written and upper-cased, null for a positional one
     * @param argumentTexts each argument's text as written
     */
    public static void validateRequiredArguments(final String functionName, final Token nameToken,
                                                 final List<String> namesInOrder, final List<String> argumentTexts) {
        if (TO_QUERY.equals(functionName)) {
            // TO_QUERY's text given as NULL by its name is missing; a NULL given positionally is no string.
            for (int i = 0; i < namesInOrder.size(); i++) {
                if ("SQL".equals(namesInOrder.get(i)) && "NULL".equalsIgnoreCase(argumentTexts.get(i).trim())) {
                    throw new RuntimeException(at(nameToken, "missing required argument [SQL] for function ["
                        + nameToken.getText().toUpperCase(Locale.ROOT) + "]"));
                }
            }
            return;
        }
        if (!COPY_HISTORY.equals(functionName)) {
            return;
        }
        final String shown = nameToken.getText().toUpperCase(Locale.ROOT);
        if (namesInOrder.size() < 2) {
            throw new RuntimeException(at(nameToken, "not enough arguments for function [" + shown + "], expected 2,"
                + " got " + namesInOrder.size()));
        }
        for (int i = 0; i < namesInOrder.size(); i++) {
            final String name = namesInOrder.get(i) != null ? unquoted(namesInOrder.get(i))
                : nameOfPositional(functionName, i);
            if ("TABLE_NAME".equals(name) && "NULL".equalsIgnoreCase(argumentTexts.get(i).trim())) {
                throw new RuntimeException(at(nameToken, "missing required argument [TABLE_NAME] for function ["
                    + shown + "]"));
            }
        }
    }

    /**
     * How a refusal names FLATTEN and SPLIT_TO_TABLE: by the first part of the call's name as it is written,
     * whatever its case and quotes - {@code [flatten]}, {@code [FQ_S]}, {@code ["fq_s"]}, {@code [test_db]}. A
     * qualifier before a built-in table function's name is not looked up - the built-in runs whatever schema the
     * name is written under, one that holds a function of that name included - and is all the refusal echoes
     * (live-verified). Every other function is named as its own rule measured it.
     */
    private static String echoed(final String functionName, final Token nameToken) {
        return FLATTEN.equals(functionName) || SPLIT_TO_TABLE.equals(functionName) ? nameToken.getText()
            : functionName;
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
        if (TO_QUERY.equals(functionName) && argumentTexts.size() == 1
                && "NULL".equalsIgnoreCase(argumentTexts.get(0).trim())) {
            // The bare word NULL written for the text is no text at all: the argument is missing (live-verified).
            throw new RuntimeException(at(nameToken,
                "missing required argument [" + REQUIRED_PARAMETER.get(TO_QUERY) + "] for function [" + functionName + "]"));
        }
        if ((TAG_REFERENCES.equals(functionName) || TAG_REFERENCES_ALL_COLUMNS.equals(functionName))
                && argumentTexts.size() > 2) {
            // The refusal names the first part of the call's name, the schema the function is read from.
            throw new RuntimeException(at(nameToken, "too many arguments for function ["
                + nameToken.getText().toUpperCase(Locale.ROOT) + "] expected 2, got " + argumentTexts.size()));
        }
        final int given = argumentTexts.size();
        if (FLATTEN.equals(functionName)) {
            // FLATTEN takes its input and up to four more by position (live-verified).
            if (given == 0) {
                throw new RuntimeException(at(nameToken, "not enough arguments for function ["
                    + nameToken.getText() + "], expected 1, got 0"));
            }
            if (given > FLATTEN_PARAMETERS.size()) {
                throw new RuntimeException(at(nameToken, "too many arguments for function ["
                    + nameToken.getText() + "] expected " + FLATTEN_PARAMETERS.size() + ", got " + given));
            }
            return;
        }
        if (!SPLIT_TO_TABLE.equals(functionName)) {
            return;
        }
        if (given == 0) {
            throw new RuntimeException(at(nameToken, "not enough arguments for function ["
                + nameToken.getText() + "], expected 1, got 0"));
        }
        if (given == 1) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "not enough arguments for function"
                + " [SPLIT(" + argumentTexts.get(0) + " AS \"1\")], expected 2, got 1"));
        }
        if (given > 2) {
            throw new RuntimeException(at(nameToken, "too many arguments for function ["
                + nameToken.getText() + "] expected 2, got " + given));
        }
    }

    /**
     * The order a call's arguments are judged in: as written, or — for DATA_METRIC_FUNCTION_REFERENCES, whose entity
     * is judged before its domain wherever each is written — in the order the function declares them
     * (live-verified).
     *
     * @param functionName the function
     * @param namesInOrder the arguments' names as written, upper-cased, null for a positional one
     * @return the arguments' indexes, in the order to judge them
     */
    public static List<Integer> judgingOrder(final String functionName, final List<String> namesInOrder) {
        final List<Integer> order = new ArrayList<>();
        final List<String> declared = DATA_METRIC_FUNCTION_REFERENCES.equals(functionName)
            ? ACCEPTED_NAMES.get(functionName) : null;
        if (declared != null) {
            for (final String name : declared) {
                for (int i = 0; i < namesInOrder.size(); i++) {
                    if (namesInOrder.get(i) != null && name.equals(unquoted(namesInOrder.get(i)))) {
                        order.add(Integer.valueOf(i));
                    }
                }
            }
        }
        for (int i = 0; i < namesInOrder.size(); i++) {
            if (!order.contains(Integer.valueOf(i))) {
                order.add(Integer.valueOf(i));
            }
        }
        return order;
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
     * The parameter of FLATTEN a positional argument fills at its place among every argument the call writes: INPUT,
     * PATH, OUTER, RECURSIVE and MODE in turn.
     *
     * @param index the argument's place, from 0
     * @return the parameter, or null past the last one
     */
    public static String flattenParameterAt(final int index) {
        return index < FLATTEN_PARAMETERS.size() ? FLATTEN_PARAMETERS.get(index) : null;
    }

    /** @return how many parameters FLATTEN takes */
    public static int flattenParameterCount() {
        return FLATTEN_PARAMETERS.size();
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
     * A quoted argument name without its quotes. The refusal of one keeps them ({@code ["STRING"]}), and live
     * upper-cases either spelling.
     */
    private static String unquoted(final String written) {
        return written.length() > 1 && written.startsWith("\"") && written.endsWith("\"")
            ? written.substring(1, written.length() - 1) : written;
    }

    private static String at(final Token nameToken, final String detail) {
        return SqlCompilationError.at(nameToken.getLine(), nameToken.getCharPositionInLine(), detail);
    }
}
