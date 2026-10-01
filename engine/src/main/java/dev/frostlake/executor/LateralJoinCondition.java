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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ON condition of a LATERAL join, applied the way live applies it — which for a LEFT join is not the
 * textbook outer join.
 *
 * <p>Live pairs a left row with the lateral rows its condition accepts, and a LEFT join null-extends a left
 * row that ends up with none. But it does so only for the part of the condition that reads the LATERAL
 * side alone (or no column at all). A conjunct that reads a column of the relations to the LEFT is applied
 * afterwards, as a filter over the paired rows, null-extended ones included — so it can drop a left row
 * outright instead of null-extending it. Live-verified over {@code f (a)} holding 1..4 and a lateral
 * {@code (SELECT v FROM g WHERE g.k = f.a)} that yields 10 and 11 for a = 1 and nothing for a = 4:
 *
 * <pre>
 *   LEFT JOIN LATERAL (…) l ON l.v &gt; 10        a = 1 → (1, 11); a = 4 → (4, NULL)
 *   LEFT JOIN LATERAL (…) l ON FALSE            every left row, null-extended
 *   LEFT JOIN LATERAL (…) l ON f.a &gt; 1         a = 1 is DROPPED; a = 4 → (4, NULL)
 *   LEFT JOIN LATERAL (…) l ON l.v = f.a * 10   a = 1 → (1, 10); a = 4 is DROPPED
 * </pre>
 *
 * <p>Every other lateral join — INNER, RIGHT, FULL, and a LEFT one written without ON — keeps only the
 * pairs the whole condition accepts and null-extends nothing: live answers {@code FULL JOIN LATERAL … ON
 * FALSE} with no rows at all.
 */
final class LateralJoinCondition {

    private final ExpressionEvaluator evaluator;
    /** The conjuncts that decide which lateral rows pair with a left row. */
    private final List<Expression> pairing = new ArrayList<>();
    /** The conjuncts judged after pairing, over null-extended rows too. */
    private final List<Expression> filtering = new ArrayList<>();
    private final boolean nullExtends;
    private final boolean judgesNothing;
    private final SourcePosition origin;

    /**
     * Compile a lateral join's condition.
     *
     * @param executor      the executor running the query block
     * @param condition     the ON condition as written
     * @param nullExtends   whether an unpaired left row is kept null-extended (a LEFT join with ON)
     * @param judgesNothing whether the condition is refused by a clause rule (an aggregate or window call),
     *                      so that no pair may be judged against it
     * @param leftTable     the relation joined so far
     * @param scope         the relations to the left, by the names the query gives them
     * @param scopeTables   the relations to the left, in combined-row order
     * @param lateralTable  the lateral side's shape
     * @param lateralAlias  the lateral side's alias, or null
     */
    LateralJoinCondition(final QueryExecutor executor, final FrostlakeParser.BooleanExprContext condition,
                         final boolean nullExtends, final boolean judgesNothing, final Table leftTable,
                         final Map<String, Table> scope, final List<Table> scopeTables,
                         final Table lateralTable, final String lateralAlias) {
        this.nullExtends = nullExtends;
        this.judgesNothing = judgesNothing;
        this.origin = new SourcePosition(condition.getStart().getLine(),
            condition.getStart().getCharPositionInLine());
        final Map<String, Table> combinedScope = withRelation(scope, lateralAlias, lateralTable);
        final List<Table> combinedTables = new ArrayList<>(scopeTables);
        combinedTables.add(lateralTable);
        this.evaluator = new ExpressionEvaluator(leftTable, executor.getFunctionRegistry(), executor.getCatalog(),
            executor);
        evaluator.setMultiTableContext(combinedScope, combinedTables);
        if (!nullExtends) {
            pairing.add(compiled(executor, condition));
            return;
        }
        // Which conjuncts read the lateral side alone: the ones every name of which resolves in a scope that
        // holds nothing but the lateral relation.
        final ExpressionEvaluator lateralOnly = new ExpressionEvaluator(lateralTable,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        final List<Table> lateralTables = new ArrayList<>();
        lateralTables.add(lateralTable);
        lateralOnly.setMultiTableContext(withRelation(new LinkedHashMap<String, Table>(), lateralAlias,
            lateralTable), lateralTables);
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(condition, conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final Expression compiled = compiled(executor, conjunct);
            if (readsLateralSideOnly(lateralOnly, compiled)) {
                pairing.add(compiled);
            } else {
                filtering.add(compiled);
            }
        }
    }

    /**
     * The rows one left row contributes.
     *
     * @param leftRow      the left row
     * @param lateralRows  what the lateral side produced for it
     * @param lateralWidth the lateral side's column count, for null-extension
     * @param context      the names the lateral side was evaluated with (select aliases the condition may read)
     * @return the combined rows to keep
     */
    List<Row> rowsFor(final Row leftRow, final List<Row> lateralRows, final int lateralWidth,
                      final Map<String, Object> context) {
        final List<Row> kept = new ArrayList<>();
        if (judgesNothing) {
            return kept;
        }
        final SourcePosition displaced = ExpressionSource.beginNested(origin);
        try {
            evaluator.setOuterLateralContext(context);
            for (final Row lateralRow : lateralRows) {
                final List<Object> values = new ArrayList<>(leftRow.getValues());
                values.addAll(lateralRow.getValues());
                final Row combined = Row.of(values);
                if (allTrue(pairing, combined)) {
                    kept.add(combined);
                }
            }
            if (kept.isEmpty() && nullExtends) {
                final List<Object> values = new ArrayList<>(leftRow.getValues());
                for (int i = 0; i < lateralWidth; i++) {
                    values.add(null);
                }
                kept.add(Row.of(values));
            }
            if (filtering.isEmpty()) {
                return kept;
            }
            final List<Row> filtered = new ArrayList<>();
            for (final Row row : kept) {
                if (allTrue(filtering, row)) {
                    filtered.add(row);
                }
            }
            return filtered;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    private boolean allTrue(final List<Expression> conjuncts, final Row row) {
        for (final Expression conjunct : conjuncts) {
            if (!SqlTruth.isTrue(evaluator.evaluate(conjunct, row))) {
                return false;
            }
        }
        return true;
    }

    private Expression compiled(final QueryExecutor executor, final FrostlakeParser.BooleanExprContext condition) {
        final Expression parsed = evaluator.withNarrowingCastEqualitiesAnswered(
            ExpressionEvaluator.parse(executor.getOriginalText(condition)));
        evaluator.validateCollations(parsed);
        return parsed;
    }

    private static boolean readsLateralSideOnly(final ExpressionEvaluator lateralOnly, final Expression conjunct) {
        try {
            lateralOnly.validateColumnScope(conjunct);
            return true;
        } catch (final RuntimeException readsElsewhere) {
            return false;
        }
    }

    /** The top-level AND operands of a condition; a parenthesized conjunction stays one conjunct. */
    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext condition,
                                         final List<FrostlakeParser.BooleanExprContext> conjuncts) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            collectConjuncts(((FrostlakeParser.AndExprContext) condition).booleanExpr(0), conjuncts);
            collectConjuncts(((FrostlakeParser.AndExprContext) condition).booleanExpr(1), conjuncts);
            return;
        }
        conjuncts.add(condition);
    }

    private static Map<String, Table> withRelation(final Map<String, Table> scope, final String alias,
                                                   final Table relation) {
        if (scope instanceof FromClauseRelations) {
            final FromClauseRelations widened = new FromClauseRelations((FromClauseRelations) scope);
            widened.putRelation(alias, relation);
            return widened;
        }
        final Map<String, Table> widened = new LinkedHashMap<>(scope);
        widened.put(alias != null ? alias : relation.getName(), relation);
        return widened;
    }
}
