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

import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Collections;

/**
 * A statement written in parentheses as the value of an assignment, {@code x := (<statement>)}. Only a RESULTSET takes
 * one; any other target is refused and nothing of the statement runs (all live-verified):
 *
 * <ul>
 *   <li>a cursor, whatever it is assigned: " Assignment to variable 'C' is not permitted." at the {@code :=}, and an
 *       exception: "invalid identifier 'E'" there, both while the block compiles;</li>
 *   <li>a scalar, while the block compiles, when the statement is a command the account reads as such — a SHOW, a
 *       DESCRIBE, an EXPLAIN, a LIST, GET, PUT or REMOVE, a transaction statement, a DROP, UNDROP, USE, COMMENT or
 *       TRUNCATE, a CREATE of a schema, database, sequence, function, task or file format, an ALTER of the session, a
 *       view, a schema or a sequence, a table's SWAP WITH, an EXECUTE IMMEDIATE and an anonymous block: "Invalid
 *       expression value (…) for assignment.", the account naming the statement in its own rendering;</li>
 *   <li>a scalar, when the assignment runs, for any other statement — INSERT, UPDATE, DELETE, MERGE, COPY, a CREATE of a
 *       table, view, stage or stream, an ALTER TABLE, SET and UNSET: the account evaluates the parentheses as a query,
 *       {@code SELECT * FROM (<statement>)}, and fails with that text's syntax error, an uncaught EXPRESSION_ERROR at
 *       the statement's first word (SQLCODE 1003, SQLSTATE 42000).</li>
 * </ul>
 */
public final class AssignedStatement {

    /** What the account calls an EXECUTE IMMEDIATE it will not assign to a scalar. */
    public static final String EXECUTE_IMMEDIATE = "?SqlExecuteImmediateDynamic?";

    /** What the account calls an anonymous block it will not assign to a scalar. */
    public static final String BLOCK = "?SqlPsmBlock?";

    /** The query the account evaluates a parenthesized statement as, around the statement. */
    private static final String QUERY_LEAD = "SELECT * FROM (";

    private AssignedStatement() {
    }

    /**
     * Whether a statement assigned to a scalar is refused while the block compiles, rather than when the assignment
     * runs.
     *
     * @param statement the statement in the parentheses
     * @return true for a command the account refuses as an invalid expression value
     */
    public static boolean refusedWhileCompiling(final FrostlakeParser.ResultSetStatementContext statement) {
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl != null) {
            if (ddl.createStatement() != null) {
                return createsCommandObject(ddl.createStatement());
            }
            if (ddl.alterStatement() != null) {
                return altersAsCommand(ddl.alterStatement());
            }
            return ddl.dropStatement() != null || ddl.dropClassStatement() != null || ddl.undropStatement() != null
                || ddl.useStatement() != null || ddl.commentStatement() != null || ddl.truncateStatement() != null;
        }
        return statement.explainStatement() != null || statement.transactionStatement() != null
            || statement.listStatement() != null || statement.getStatement() != null
            || statement.putStatement() != null || statement.removeStatement() != null
            || statement.securityObjectListing() != null || statement.showStatement() != null
            || statement.showClassStatement() != null || statement.describeStatement() != null;
    }

    /**
     * The refusal of a statement assigned to a scalar while the block compiles.
     *
     * @param named the statement as the sentence names it
     * @return the refusal to throw
     */
    public static RuntimeException invalidValue(final String named) {
        return new RuntimeException(SqlCompilationError.of("Invalid expression value (" + named + ") for assignment."));
    }

    /**
     * The refusal of a statement assigned to a scalar when the assignment runs: the syntax error of the query the
     * account evaluates it as, its places counted in that query's text.
     *
     * @param statementText the statement as written
     * @return the refusal to throw
     */
    public static RuntimeException scalarRefusal(final String statementText) {
        final String query = QUERY_LEAD + statementText + ")";
        final SourcePosition displaced = LeadingCommentOffset.begin(null);
        try {
            QueryExecutor.requireParses(query);
        } catch (final RuntimeException syntax) {
            return syntax;
        } finally {
            LeadingCommentOffset.end(displaced);
        }
        // A statement this parser reads as a relation — UNSET v, a table name and an alias — is refused at the word
        // after its first, which the account does not read in a parenthesized relation (live-verified).
        final CommonTokenStream tokens = new CommonTokenStream(new FrostlakeLexer(CharStreams.fromString(query)));
        tokens.fill();
        Token second = null;
        int words = 0;
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getStartIndex() >= QUERY_LEAD.length()
                    && token.getType() != Token.EOF && ++words == 2) {
                second = token;
                break;
            }
        }
        final Token at = second != null ? second : tokens.get(tokens.size() - 1);
        final String line = "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
            + " unexpected '" + at.getText() + "'.";
        return new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line), query);
    }

    /** Whether a CREATE makes a schema, database, sequence, function, task or file format. */
    private static boolean createsCommandObject(final FrostlakeParser.CreateStatementContext create) {
        for (int i = 1; i < create.getChildCount(); i++) {
            final ParseTree child = create.getChild(i);
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            switch (((TerminalNode) child).getSymbol().getType()) {
                case FrostlakeLexer.SCHEMA:
                case FrostlakeLexer.DATABASE:
                case FrostlakeLexer.SEQUENCE:
                case FrostlakeLexer.FUNCTION:
                case FrostlakeLexer.TASK:
                case FrostlakeLexer.FILE:
                    return true;
                case FrostlakeLexer.TABLE:
                case FrostlakeLexer.VIEW:
                case FrostlakeLexer.STAGE:
                case FrostlakeLexer.STREAM:
                    return false;
                default:
                    break;
            }
        }
        return false;
    }

    /** Whether an ALTER is of the session, a view, a schema or a sequence, or swaps a table. */
    private static boolean altersAsCommand(final FrostlakeParser.AlterStatementContext alter) {
        if (alter.tableAction() != null) {
            return alter.ICEBERG() == null && alter.tableAction().SWAP() != null;
        }
        return alter.SESSION() != null || alter.viewAction() != null || alter.schemaAction() != null
            || alter.sequenceAction() != null;
    }
}
