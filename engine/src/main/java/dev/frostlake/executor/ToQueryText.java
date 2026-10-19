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

import org.antlr.v4.runtime.Token;

import java.util.List;

/**
 * What TO_QUERY compiles. Its text is one query — a SELECT, a WITH, a set operation, a query in parentheses,
 * a closing semicolon allowed — or one CALL, and nothing else (all live-verified):
 *
 * <pre>
 *   a DML, a DDL, SHOW, DESCRIBE, SET, USE, a block      [1] is not a valid FROM item
 *   a second statement after a semicolon                 syntax error line 1 at position 10 unexpected 'DELETE'.
 *   a flow of statements (a ->> b)                       Unsupported feature 'a'.
 *   EXPLAIN of a query                                   the query itself, its rows
 * </pre>
 *
 * <p>A text that does not parse is refused for its syntax, and an empty one as an empty statement, both as
 * running it would refuse it.
 */
final class ToQueryText {

    private static final String NO_FROM_ITEM = "[1] is not a valid FROM item";

    private ToQueryText() {
    }

    /**
     * The query a TO_QUERY text runs.
     *
     * @param sql the text
     * @return the query to run — the text itself, or the query an EXPLAIN in it names — or null when the text
     *     is a CALL, which runs as it is written
     * @throws RuntimeException the refusal of a text that is neither
     */
    static String requireQuery(final String sql) {
        if (StatementCount.countTokens(sql) == 0) {
            return sql;     // refused as the empty statement it is, when it runs
        }
        final List<FrostlakeParser.FlowChainContext> chains = QueryExecutor.parsedScript(sql).flowChain();
        if (chains.isEmpty()) {
            return sql;
        }
        if (chains.size() > 1) {
            final Token second = chains.get(1).getStart();
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + second.getLine()
                + " at position " + second.getCharPositionInLine() + " unexpected '" + second.getText() + "'."));
        }
        final List<FrostlakeParser.StatementContext> stages = chains.get(0).statement();
        if (stages.size() > 1) {
            throw new RuntimeException("Unsupported feature '" + ParseTreeText.getOriginalText(stages.get(0)) + "'.");
        }
        final FrostlakeParser.StatementContext statement = stages.get(0);
        if (statement.queryStatement() != null) {
            return sql;
        }
        if (statement.proceduralStatement() != null && statement.proceduralStatement().callStatement() != null) {
            return null;
        }
        if (statement.explainStatement() != null && statement.explainStatement().selectStatement() != null) {
            return ParseTreeText.getOriginalText(statement.explainStatement().selectStatement());
        }
        throw new RuntimeException(SqlCompilationError.of(NO_FROM_ITEM));
    }
}
