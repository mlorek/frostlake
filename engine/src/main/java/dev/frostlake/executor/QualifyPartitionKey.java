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

import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;
import java.util.Map;

/**
 * A subquery reading the outer row that stands as the one PARTITION BY key of an aggregate window the QUALIFY reads,
 * with no ORDER BY: the plan groups the window's argument by it and refuses that argument as no group by expression,
 * named as the plan converts it (live-verified):
 *
 * <pre>
 *   SUM(v)       v INT             [CAST(R1.V AS NUMBER(38,0))]                         the argument at its own type
 *   SUM(d)       d NUMBER(10,2)    [CAST(R1.D AS NUMBER(10,2))]
 *   SUM(v + 1)                     [CAST(R1.V + 1 AS NUMBER(38,0))]
 *   AVG(v)       v INT             [CAST(CAST(R1.V AS NUMBER(38,3)) AS NUMBER(38,3))]
 *   COUNT(v)  MAX(s)  MIN(s)       [R1.V]  [R1.S]                                         the argument as it is
 *   COUNT(*)                       Unsupported subquery type cannot be evaluated, at the subquery
 * </pre>
 *
 * <p>A second key, an ORDER BY in the window, a subquery reading nothing of the outer row and the same key in a
 * select list's window are answered (live-verified). A call or an argument of another shape is left to the plan.
 */
final class QualifyPartitionKey {

    /** The decimals AVG divides a whole number into. */
    private static final int AVERAGE_SCALE = 3;
    private static final int MAX_PRECISION = 38;

    private QualifyPartitionKey() {
    }

    /**
     * The refusal of a subquery standing as a QUALIFY window's one partition key, or null when it stands anywhere
     * else or the window's call is of a shape this does not name.
     *
     * @param node      the scalar subquery expression
     * @param relations the outer query's relations, by the names they go by
     * @return the refusal, or null
     */
    static RuntimeException refusal(final ParseTree node, final Map<String, Table> relations) {
        final FrostlakeParser.OverClauseContext over = soleKeyOf(node);
        if (over == null || over.orderByClause() != null || over.windowFrame() != null
                || !(over.getParent() instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) over.getParent();
        if (call.DISTINCT() != null || call.functionArgList() == null
                || call.functionArgList().functionArg().size() != 1) {
            return null;
        }
        final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
        final FrostlakeParser.FunctionArgContext argument = call.functionArgList().functionArg(0);
        if (argument.STAR() != null) {
            if (!"COUNT".equals(name) || argument.starQualifiedName() != null) {
                return null;
            }
            final FrostlakeParser.SelectStatementContext query =
                ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement();
            return new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be"
                + " evaluated at line " + query.getStart().getLine() + ", position "
                + query.getStart().getCharPositionInLine()));
        }
        if (argument.booleanExpr() == null) {
            return null;
        }
        final ParseTree value = valueOf(argument.booleanExpr());
        final String printed = printed(value, relations);
        if (printed == null) {
            return null;
        }
        final String grouped;
        if ("SUM".equals(name)) {
            final DataType type = typeOf(value, relations);
            grouped = type == null ? null : "CAST(" + printed + " AS " + SqlTypeNames.canonical(type) + ")";
        } else if ("AVG".equals(name)) {
            final DataType type = typeOf(value, relations);
            final String average = "NUMBER(" + MAX_PRECISION + "," + AVERAGE_SCALE + ")";
            grouped = isWhole(type) ? "CAST(CAST(" + printed + " AS " + average + ") AS " + average + ")" : null;
        } else if ("COUNT".equals(name) || "MAX".equals(name) || "MIN".equals(name)) {
            grouped = printed;
        } else {
            grouped = null;
        }
        return grouped == null ? null
            : new RuntimeException(SqlCompilationError.of("[" + grouped + "] is not a valid group by expression"));
    }

    /** The OVER clause a subquery is the one PARTITION BY key of, inside a QUALIFY; null anywhere else. */
    private static FrostlakeParser.OverClauseContext soleKeyOf(final ParseTree node) {
        FrostlakeParser.PartitionByClauseContext partition = null;
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext) {
                return null;
            }
            if (partition == null && at instanceof FrostlakeParser.PartitionByClauseContext) {
                partition = (FrostlakeParser.PartitionByClauseContext) at;
                if (partition.booleanExprList() == null || partition.booleanExprList().booleanExpr().size() != 1
                        || valueOf(partition.booleanExprList().booleanExpr(0)) != node) {
                    return null;
                }
            } else if (partition != null && at instanceof FrostlakeParser.QualifyClauseContext) {
                return partition.getParent() instanceof FrostlakeParser.OverClauseContext
                    ? (FrostlakeParser.OverClauseContext) partition.getParent() : null;
            }
        }
        return null;
    }

    /** The one value a condition stands for: the node every single-child step below it leads to. */
    private static ParseTree valueOf(final ParseTree condition) {
        ParseTree at = condition;
        while (at.getChildCount() == 1 && !(at instanceof FrostlakeParser.ScalarSubqueryExprContext)
                && !(at instanceof FrostlakeParser.QualifiedNameExprContext)) {
            at = at.getChild(0);
        }
        return at;
    }

    /**
     * An argument as the plan prints it: a column of the outer relations qualified by the name its relation goes by
     * ({@code R1.V}), and a column plus or minus a whole number ({@code R1.V + 1}); null for any other shape.
     */
    private static String printed(final ParseTree value, final Map<String, Table> relations) {
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            final Map.Entry<String, TableColumn> column =
                column((FrostlakeParser.QualifiedNameExprContext) value, relations);
            return column == null ? null
                : column.getKey().toUpperCase(Locale.ROOT) + "." + column.getValue().getName().toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.AdditiveExprContext shifted = shifted(value);
        if (shifted == null) {
            return null;
        }
        final String base = printed(shifted.expression(0), relations);
        return base == null ? null
            : base + " " + shifted.op.getText() + " " + shifted.expression(1).getText();
    }

    /**
     * The type the plan gives an argument: a column's own, and NUMBER(38,0) for a column of whole numbers plus or
     * minus a whole number.
     */
    private static DataType typeOf(final ParseTree value, final Map<String, Table> relations) {
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            final Map.Entry<String, TableColumn> column =
                column((FrostlakeParser.QualifiedNameExprContext) value, relations);
            return column == null ? null : column.getValue().getDataType();
        }
        final FrostlakeParser.AdditiveExprContext shifted = shifted(value);
        return shifted != null && isWhole(typeOf(shifted.expression(0), relations))
            ? new NumericType("NUMBER", MAX_PRECISION, 0) : null;
    }

    /** A column plus or minus a whole number written as such, or null for any other value. */
    private static FrostlakeParser.AdditiveExprContext shifted(final ParseTree value) {
        if (!(value instanceof FrostlakeParser.AdditiveExprContext)) {
            return null;
        }
        final FrostlakeParser.AdditiveExprContext additive = (FrostlakeParser.AdditiveExprContext) value;
        final boolean shift = additive.op.getType() == FrostlakeParser.PLUS
            || additive.op.getType() == FrostlakeParser.MINUS;
        return shift && additive.expression(0) instanceof FrostlakeParser.QualifiedNameExprContext
            && additive.expression(1) instanceof FrostlakeParser.LiteralExprContext
            && ((FrostlakeParser.LiteralExprContext) additive.expression(1)).literal().INTEGER_LITERAL() != null
            ? additive : null;
    }

    private static boolean isWhole(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) && ((NumericType) type).getScale() == 0;
    }

    /** The column a name reads among the relations, with the name its relation goes by; null for none. */
    private static Map.Entry<String, TableColumn> column(final FrostlakeParser.QualifiedNameExprContext name,
                                                         final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        if (parts.length > 2) {
            return null;
        }
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (parts.length == 1 || relation.getKey().equalsIgnoreCase(parts[0])) {
                final TableColumn held = relation.getValue().getColumn(parts[parts.length - 1]);
                if (held != null) {
                    return Map.entry(relation.getKey(), held);
                }
            }
        }
        return null;
    }
}
