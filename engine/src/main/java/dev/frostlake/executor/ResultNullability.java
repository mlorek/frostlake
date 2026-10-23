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

import dev.frostlake.executor.expressions.ArrayAccessExpression;
import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LikeAnyAllExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ObjectAccessExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.executor.expressions.WindowFunctionExpression;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether a select item can answer NULL, worked out from the item itself as the account works it out for a
 * JavaScript procedure's {@code isColumnNullable} (live-verified):
 *
 * <pre>
 *   a column                                   its own NOT NULL, or nullable on a side an outer join extends;
 *                                              a derived relation's as the projection that made it judged
 *   NULL, TRUE, FALSE                          nullable; a number or a string is not
 *   +, -, *, /, %, ||, a sign, a cast          nullable when an operand is; TRY_CAST always
 *   a comparison, AND, OR, NOT, IS [NOT] NULL,
 *   BETWEEN, LIKE, IN, EQUAL_NULL               nullable
 *   CASE, IFF, NVL2, DECODE                     nullable when a result is, or with no ELSE or default
 *   COALESCE, NVL, IFNULL                       nullable only when every argument is
 *   an aggregate or a window function           nullable, but for COUNT, LISTAGG, ARRAY_AGG, OBJECT_AGG,
 *                                              HASH_AGG, APPROX_COUNT_DISTINCT and the ranking functions
 *   PARSE_JSON, the constructors, GET, a path,
 *   TRY_*, NULLIF, the session's names, …       nullable
 *   HASH, RANDOM, UUID_STRING, SEQ4, the
 *   current date and time, ZEROIFNULL           not nullable
 *   any other function                         nullable when an argument is
 * </pre>
 *
 * A name no column answers to is a word of the call it is written in, such as a date part, and no value.
 */
final class ResultNullability {

    /**
     * The functions that can answer NULL whatever their arguments are, a lookup that finds nothing among them:
     * ARRAY_POSITION, ARRAY_MIN, ARRAY_MAX, ARRAY_SIZE, JSON_EXTRACT_PATH_TEXT, CHECK_XML and GETVARIABLE are
     * nullable over columns that are not (live-verified).
     */
    private static final Set<String> ALWAYS = new HashSet<String>(Arrays.asList(
        "ARRAY_POSITION", "ARRAY_MIN", "ARRAY_MAX", "ARRAY_SIZE", "JSON_EXTRACT_PATH_TEXT", "CHECK_XML", "GETVARIABLE",
        "PARSE_JSON", "TRY_PARSE_JSON", "CHECK_JSON", "PARSE_XML", "XMLGET", "ARRAY_CONSTRUCT",
        "ARRAY_CONSTRUCT_COMPACT", "OBJECT_CONSTRUCT", "OBJECT_CONSTRUCT_KEEP_NULL", "GET", "GET_PATH",
        "GET_IGNORE_CASE", "TO_GEOGRAPHY", "TO_GEOMETRY", "CURRENT_USER", "CURRENT_ROLE", "CURRENT_DATABASE",
        "CURRENT_SCHEMA", "CURRENT_SESSION", "CURRENT_WAREHOUSE", "NULLIF", "NULLIFZERO", "REGEXP_SUBSTR", "STRTOK",
        "EQUAL_NULL", "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE", "SUM", "MIN", "MAX", "AVG",
        "ANY_VALUE", "MEDIAN", "MODE", "COUNT_IF", "BOOLAND_AGG", "BOOLOR_AGG", "BOOLXOR_AGG", "BITAND_AGG",
        "BITOR_AGG", "BITXOR_AGG", "MIN_BY", "MAX_BY", "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VARIANCE",
        "VARIANCE_POP", "VARIANCE_SAMP", "VAR_POP", "VAR_SAMP", "KURTOSIS", "SKEW", "CORR", "COVAR_POP",
        "COVAR_SAMP", "PERCENTILE_CONT", "PERCENTILE_DISC", "APPROX_PERCENTILE"));

    /** The functions that never answer NULL. */
    private static final Set<String> NEVER = new HashSet<String>(Arrays.asList(
        "COUNT", "HASH", "HASH_AGG", "APPROX_COUNT_DISTINCT", "HLL", "LISTAGG", "ARRAY_AGG", "OBJECT_AGG",
        "ROW_NUMBER", "RANK", "DENSE_RANK", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "RANDOM", "UUID_STRING", "CURRENT_DATE",
        "CURRENT_TIME", "CURRENT_TIMESTAMP", "LOCALTIME", "LOCALTIMESTAMP", "SYSDATE", "GETDATE", "SYSTIMESTAMP",
        "ZEROIFNULL", "PI"));

    /** The functions that answer NULL only when every argument is NULL. */
    private static final Set<String> FIRST_NON_NULL = new HashSet<String>(Arrays.asList("COALESCE", "NVL",
        "IFNULL"));

    private final Table table;
    private final Map<String, Table> aliasToTable;

    /**
     * @param table        the relation the select list reads
     * @param aliasToTable the relations a qualified name reaches, by name, or null
     */
    ResultNullability(final Table table, final Map<String, Table> aliasToTable) {
        this.table = table;
        this.aliasToTable = aliasToTable;
    }

    /**
     * Whether the expression can answer NULL.
     *
     * @param expression the select item
     * @return whether it can
     */
    boolean of(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) expression).getType();
            return type == LiteralType.NULL || type == LiteralType.BOOLEAN;
        }
        if (expression instanceof ColumnReferenceExpression) {
            return column((ColumnReferenceExpression) expression);
        }
        if (expression instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expression;
            return cast.isTryMode() || of(cast.getExpression());
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            final BinaryOperator operator = binary.getOperator();
            final boolean arithmetic = operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
                || operator == BinaryOperator.MODULO || operator == BinaryOperator.CONCAT;
            return !arithmetic || of(binary.getLeft()) || of(binary.getRight());
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() == UnaryOperator.NOT || unary.getOperator() == UnaryOperator.EXISTS
                || of(unary.getOperand());
        }
        if (expression instanceof IsNullExpression || expression instanceof BetweenExpression
                || expression instanceof InExpression || expression instanceof LikeAnyAllExpression) {
            return true;
        }
        if (expression instanceof CaseExpression) {
            final CaseExpression caseExpression = (CaseExpression) expression;
            if (caseExpression.getElseExpression() == null || of(caseExpression.getElseExpression())) {
                return true;
            }
            for (final WhenClause when : caseExpression.getWhenClauses()) {
                if (of(when.getResult())) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ObjectAccessExpression || expression instanceof ArrayAccessExpression) {
            return true;
        }
        if (expression instanceof WindowFunctionExpression) {
            final String name = ((WindowFunctionExpression) expression).getFunctionName();
            return name == null || !NEVER.contains(name.toUpperCase(Locale.ROOT));
        }
        if (expression instanceof FunctionCallExpression) {
            return call((FunctionCallExpression) expression);
        }
        return true;
    }

    /** A function's answer: by its own rule, else nullable when an argument is. */
    private boolean call(final FunctionCallExpression call) {
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> arguments = call.getArguments();
        if (ALWAYS.contains(name) || name.startsWith("TRY_") || name.startsWith("AS_")) {
            return true;
        }
        if (NEVER.contains(name)) {
            return false;
        }
        if (FIRST_NON_NULL.contains(name)) {
            for (final Expression argument : arguments) {
                if (!of(argument)) {
                    return false;
                }
            }
            return true;
        }
        if ("IFF".equals(name) || "NVL2".equals(name)) {
            return arguments.size() < 3 || of(arguments.get(1)) || of(arguments.get(2));
        }
        if ("DECODE".equals(name)) {
            // DECODE(expr, search, result, …, [default]): its results, and NULL when no default is written.
            if (arguments.size() % 2 == 0) {
                for (int i = 2; i < arguments.size(); i += 2) {
                    if (of(arguments.get(i))) {
                        return true;
                    }
                }
                return of(arguments.get(arguments.size() - 1));
            }
            return true;
        }
        for (final Expression argument : arguments) {
            if (of(argument)) {
                return true;
            }
        }
        return false;
    }

    /** A column's own NOT NULL, and the NULLs an outer join extends its relation with. */
    private boolean column(final ColumnReferenceExpression reference) {
        final String name = reference.getColumnName();
        if (reference.isQualified() && aliasToTable != null) {
            final Table relation = relationNamed(reference.getTableName());
            if (relation != null && relation.hasColumn(name)) {
                final JoinedRelations joined = table == null ? null : table.getJoinedRelations();
                return nullable(relation.getColumns().get(relation.getColumnIndex(name)))
                    || joined != null && joined.includes(relation) && joined.isNullExtended(relation);
            }
        }
        // A name no column answers to is a word of the call it stands in — DATEADD(day, …) — and no value.
        return table != null && table.hasColumn(name)
            && nullable(table.getColumns().get(table.getColumnIndex(name)));
    }

    /**
     * A column's own NOT NULL, or a derived relation's column's judgement of its item: a column of
     * (SELECT 1 AS c1) cannot answer NULL (live-verified).
     */
    private static boolean nullable(final TableColumn column) {
        return column.getValuesNullable() != null ? column.getValuesNullable().booleanValue() : column.isNullable();
    }

    /** The relation a qualifier names, exactly or else by case alone. */
    private Table relationNamed(final String qualifier) {
        final Table exact = aliasToTable.get(qualifier);
        if (exact != null || qualifier == null) {
            return exact;
        }
        for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(qualifier)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
