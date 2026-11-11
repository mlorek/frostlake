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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SyntaxErrorListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The correlated subqueries a DELETE's or an UPDATE's WHERE holds, judged as the account judges them when it compiles
 * the statement, before any row is read: as the WHERE of a query over the target alone (see
 * {@link CorrelationPlanJudgement}). So a target of no row, or of one, spares only what it spares that query, and an
 * EXPLAIN of the statement is refused at the same place: {@code DELETE FROM t WHERE id IN (SELECT MAX(v) + t.id FROM g
 * WHERE FALSE)} over an empty {@code t} is refused at the subquery's SELECT, and so is
 * {@code EXPLAIN UPDATE t SET y = 1 WHERE id = (SELECT v FROM g WHERE g.id = t.id)} over two rows (live-verified).
 *
 * <p>The WHERE is read in place: the statement's text is read again with everything before the WHERE replaced by a
 * query over the target, which stands where those words stood, so every token of the WHERE keeps its line and its
 * position. A statement reading other sources through FROM or USING keeps the judgement its rows make as they reach
 * the subquery.
 */
final class DmlCorrelationJudgement {

    private DmlCorrelationJudgement() {
    }

    /**
     * Refuses the statement for the first subquery of its WHERE the account cannot evaluate, as a query's plan does.
     *
     * @param executor the executor
     * @param table    the statement's target
     * @param target   the target's name as written
     * @param alias    the target's alias as written, or null
     * @param where    the statement's WHERE, or null
     */
    static void require(final QueryExecutor executor, final Table table, final FrostlakeParser.ObjectNameContext target,
                        final FrostlakeParser.IdentifierContext alias, final FrostlakeParser.WhereClauseContext where) {
        if (where == null || !holdsSubquery(where)) {
            return;
        }
        final String text = queryInPlace(target, alias, where);
        final FrostlakeParser.SqlScriptContext parsed = text == null ? null : SyntaxErrorListener.cleanParse(text);
        final FrostlakeParser.SelectStatementContext query = parsed == null ? null : firstQuery(parsed);
        if (query == null || query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null) {
            return;
        }
        final Map<String, Table> relations = new HashMap<>();
        relations.put((alias != null ? ParseTreeText.getIdentifier(alias) : table.getName()).toUpperCase(Locale.ROOT),
            table);
        final CorrelationPlanJudgement judgement = CorrelationPlanJudgement.of(executor, query,
            query.selectOperand(0).selectClause(), table, relations, null);
        final RuntimeException refused = judgement == null ? null : judgement.refusal();
        if (refused != null) {
            throw refused;
        }
    }

    /** Whether the WHERE holds a query at all, the only thing the judgement reads. */
    private static boolean holdsSubquery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsSubquery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The statement's text up to its WHERE's end, everything before the WHERE blanked but its line breaks and a query
     * over the target written into the first stretch that holds it; null where no stretch does.
     */
    private static String queryInPlace(final FrostlakeParser.ObjectNameContext target,
                                       final FrostlakeParser.IdentifierContext alias,
                                       final FrostlakeParser.WhereClauseContext where) {
        final CharStream input = where.getStart().getInputStream();
        final int whereStart = where.getStart().getStartIndex();
        final int whereStop = where.getStop().getStopIndex();
        if (input == null || whereStart < 0 || whereStop < whereStart) {
            return null;
        }
        final String standIn = "SELECT*FROM " + written(target) + (alias != null ? " " + written(alias) : "");
        final char[] text = input.getText(Interval.of(0, whereStop)).toCharArray();
        int run = 0;
        int placedAt = -1;
        for (int i = 0; i < whereStart; i++) {
            if (text[i] == '\n' || text[i] == '\r') {
                run = 0;
                continue;
            }
            text[i] = ' ';
            run++;
            if (placedAt < 0 && run == standIn.length() + 1) {
                placedAt = i - standIn.length();
            }
        }
        if (placedAt < 0) {
            return null;
        }
        standIn.getChars(0, standIn.length(), text, placedAt);
        return new String(text);
    }

    private static String written(final ParserRuleContext node) {
        return node.getStart().getInputStream().getText(
            Interval.of(node.getStart().getStartIndex(), node.getStop().getStopIndex()));
    }

    private static FrostlakeParser.SelectStatementContext firstQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return (FrostlakeParser.SelectStatementContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.SelectStatementContext found = firstQuery(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
