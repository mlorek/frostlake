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

import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.storage.Row;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The names a FROM-less SELECT's own item list publishes, and the rules its WHERE is held to.
 *
 * <p>A query with no relation still has a scope: its select items. Their names resolve in the WHERE
 * exactly as a relation's columns would — {@code SELECT 1 AS a WHERE a = 1} answers 1 — and they
 * resolve by the ordinary identifier rules, so an unquoted reference folds to upper case while a
 * quoted one matches verbatim. An item written without an alias publishes its own source text, which
 * a quoted reference can name ({@code SELECT 1 + 1 WHERE "1 + 1" = 2}).
 *
 * <p>Three refusals belong to the scope rather than to the evaluation, and each one outranks the
 * clause rules that follow it — a name the scope cannot answer is reported wherever it stands, even
 * when an aggregate written earlier in the same predicate is also wrong:
 *
 * <pre>
 *   SELECT 1 AS a, 2 AS a WHERE a = 1        ambiguous column name 'A'
 *   SELECT COUNT(*) AS c WHERE c = 1         aggregate function alias 'C' cannot be used in the
 *                                            WHERE clause
 *   SELECT 1 AS a WHERE MAX(a) = 1 AND b = 1 invalid identifier 'B'
 * </pre>
 *
 * <p>A name is answered by this scope only where the scope IS in force: a query nested inside the
 * predicate has a scope of its own and cannot see this one. Live-verified.
 */
final class FromlessSelectScope {

    /** Every published name in item order, duplicates kept — two of one name make it ambiguous. */
    private final List<String> names = new ArrayList<>();

    /** The published names whose item is an aggregate call. */
    private final Set<String> aggregates = new HashSet<>();

    /**
     * Publishes one select item's name.
     *
     * @param name        the item's name, canonical: an unquoted spelling upper-cased, a quoted one verbatim
     * @param isAggregate whether the item is an aggregate call
     */
    void publish(final String name, final boolean isAggregate) {
        names.add(name);
        if (isAggregate) {
            aggregates.add(name);
        }
    }

    /**
     * Refuses every bare name the predicate reads that this scope cannot answer, in source order.
     *
     * @param clause      the predicate's parse node
     * @param outsideScope an evaluator that does NOT see this scope, which decides whether a name the
     *                     scope does not publish resolves some other way — a zero-argument function, a
     *                     date-time unit keyword and the rest of the resolver's own vocabulary
     * @param row          the row that evaluator reads
     */
    void rejectUnresolvableNames(final ParseTree clause, final ExpressionEvaluator outsideScope,
                                 final Row row) {
        if (clause instanceof FrostlakeParser.SelectClauseContext) {
            // A query nested in the predicate carries its own scope.
            return;
        }
        if (clause instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) clause).qualifiedName();
            if (name.namePart().isEmpty()) {
                judge(name, outsideScope, row);
            }
        }
        for (int i = 0; i < clause.getChildCount(); i++) {
            rejectUnresolvableNames(clause.getChild(i), outsideScope, row);
        }
    }

    /** Answers one bare name: published once, published twice, an aggregate's alias, or unknown. */
    private void judge(final FrostlakeParser.QualifiedNameContext name,
                       final ExpressionEvaluator outsideScope, final Row row) {
        final String canonical = ParseTreeText.namePartText(name.nameStartPart());
        final Token at = name.getStart();
        int published = 0;
        for (final String each : names) {
            if (each.equals(canonical)) {
                published++;
            }
        }
        if (published > 1) {
            throw new AmbiguousColumnException(canonical);
        }
        if (published == 1) {
            if (aggregates.contains(canonical)) {
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "aggregate function alias '" + canonical + "' cannot be used in the WHERE clause"));
            }
            return;
        }
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(at.getLine(), at.getCharPositionInLine()));
        try {
            outsideScope.evaluate(name.getText(), row);
        } catch (final SqlSyntaxException notAnExpressionAlone) {
            // A word that only reads as a name where a name is the only thing possible — CASE and its
            // kind — cannot be re-parsed on its own, and it is no more resolvable for that.
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                at.getLine(), at.getCharPositionInLine(), canonical));
        } finally {
            ExpressionSource.end(displaced);
        }
    }
}
