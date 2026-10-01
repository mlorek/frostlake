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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a JOIN POLICY demands of a query's FROM clause: the protected table must be INNER joined to a
 * DIFFERENT table on an equality between their columns.
 *
 * <p>Live answers three distinct sentences, which is why this returns one rather than a boolean —
 * no join or an OUTER join is the generic violation, a join that cannot satisfy the constraint (a
 * cross join, a comma join, a constant ON) names the unsatisfied constraint, and a self join is
 * called a potential cross-join. All measured.
 */
public final class JoinPolicyRules {

    /** The generic refusal: nothing was joined, or the join was an outer one. */
    public static final String NO_JOIN =
        "Join Policy violation, please contact the policy admin for details";

    /** A join is there, but cannot satisfy the policy. */
    public static final String UNSATISFIED =
        "Join Policy violation, invalid join condition with reason: Unsatisfied constraint(s).";

    /** The protected table joined to itself. */
    public static final String SELF_JOIN =
        "Join Policy violation, invalid join condition with reason: Potential cross-join.";

    private JoinPolicyRules() {
    }

    /**
     * The refusal a FROM clause earns for a table under a join policy, or null when it satisfies one.
     *
     * @param from       the FROM clause of the select that reads the table
     * @param tableName  the protected table's name
     */
    public static String violation(final FrostlakeParser.TableExpressionContext from,
                                   final String tableName) {
        if (from == null) {
            return NO_JOIN;
        }
        final List<FrostlakeParser.TableReferenceContext> refs = from.tableReference();
        final List<FrostlakeParser.JoinClauseContext> joins = from.joinClause();
        if (joins.isEmpty() && refs.size() <= 1) {
            return NO_JOIN;
        }
        if (joins.isEmpty()) {
            // FROM a, b — a comma join carries no condition to satisfy.
            return UNSATISFIED;
        }
        if (namesTableTwice(from, tableName)) {
            return SELF_JOIN;
        }
        for (final FrostlakeParser.JoinClauseContext join : joins) {
            final FrostlakeParser.JoinTypeContext type = join.joinType();
            if (type != null && (type.LEFT() != null || type.RIGHT() != null || type.FULL() != null)) {
                return NO_JOIN;
            }
            if (join.CROSS() != null) {
                return UNSATISFIED;
            }
            if (join.USING() != null) {
                continue;
            }
            if (join.booleanExpr() == null || !equatesTwoColumns(join.booleanExpr())) {
                return UNSATISFIED;
            }
        }
        return null;
    }

    /** Whether the protected table is read more than once in this FROM clause. */
    private static boolean namesTableTwice(final FrostlakeParser.TableExpressionContext from,
                                           final String tableName) {
        final List<String> names = new ArrayList<>();
        collectTableNames(from, names);
        int seen = 0;
        for (final String name : names) {
            if (name.equalsIgnoreCase(tableName)) {
                seen++;
            }
        }
        return seen > 1;
    }

    private static void collectTableNames(final ParseTree tree, final List<String> into) {
        if (tree instanceof FrostlakeParser.TableQualifiedNameContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                (FrostlakeParser.TableQualifiedNameContext) tree);
            into.add(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectTableNames(tree.getChild(i), into);
        }
    }

    /** Whether an ON condition equates two COLUMN references — {@code ON 1 = 1} does not. */
    private static boolean equatesTwoColumns(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.ComparisonExprContext) {
            final FrostlakeParser.ComparisonExprContext comparison =
                (FrostlakeParser.ComparisonExprContext) tree;
            if (comparison.op != null && "=".equals(comparison.op.getText())
                    && isColumnReference(comparison.expression(0))
                    && isColumnReference(comparison.expression(1))) {
                return true;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (equatesTwoColumns(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isColumnReference(final ParseTree tree) {
        return tree instanceof FrostlakeParser.QualifiedNameExprContext;
    }
}
