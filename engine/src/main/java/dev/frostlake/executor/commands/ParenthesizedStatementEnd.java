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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A statement a RESULTSET is filled from, or one assigned in parentheses, ends at its closing parenthesis: a
 * semicolon before it is a syntax error at the semicolon, whatever the statement — {@code (SHOW TABLES;)},
 * {@code (INSERT INTO t VALUES (1);)}, {@code (BEGIN;)}, {@code (CALL SYSTEM$TYPEOF(1);)},
 * {@code (EXECUTE IMMEDIATE 'SELECT 1';)} (live-verified). The engine's statement rules each take a closing
 * semicolon of their own, so the parse reads past it; the block's compilation refuses it.
 */
final class ParenthesizedStatementEnd {

    private ParenthesizedStatementEnd() {
    }

    /**
     * The semicolon that closes {@code node}'s parenthesized statement, when {@code node} is a RESULTSET's source
     * or a statement assigned in parentheses and it ends with one; null otherwise.
     *
     * @param node a node of a block's parse tree
     * @return the semicolon, or null
     */
    static Token of(final ParseTree node) {
        final ParserRuleContext statement;
        if (node instanceof FrostlakeParser.ResultSetSourceContext) {
            statement = (ParserRuleContext) node;
        } else if (node instanceof FrostlakeParser.AssignmentStatementContext) {
            statement = ((FrostlakeParser.AssignmentStatementContext) node).resultSetStatement();
        } else {
            statement = null;
        }
        final Token last = statement == null ? null : statement.getStop();
        return last != null && last.getType() == FrostlakeLexer.SEMI ? last : null;
    }

    /**
     * The syntax error {@code semicolon} is.
     *
     * @param semicolon the semicolon {@link #of} found
     * @return the refusal
     */
    static RuntimeException refusal(final Token semicolon) {
        return new RuntimeException(SqlCompilationError.of("syntax error line " + semicolon.getLine()
            + " at position " + semicolon.getCharPositionInLine() + " unexpected ';'."));
    }
}
