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

package dev.frostlake.persistence;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;

import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Which statements belong in the write-ahead log, decided from the PARSE TREE.
 *
 * <p>A statement earns its way out of the log by being one of the five read-only or
 * transaction-control families the grammar already distinguishes — a query, an EXPLAIN, a SHOW, a
 * DESCRIBE, or BEGIN/COMMIT/ROLLBACK. Everything else is logged: DDL, DML, session and stage
 * statements, security statements, task statements, and procedural blocks.
 *
 * <p>The rule is a DENYLIST on purpose. A new statement family added to the grammar is logged
 * without anyone remembering to come here, which is the safe direction — a redundant log record
 * costs space, while a missing one silently loses committed data on the next restart.
 *
 * <p>It reads the tree rather than the SQL text because the leading keyword does not determine the
 * family. {@code BEGIN} opens a transaction AND a procedural block, so a text rule that skips
 * "BEGIN" drops the DML inside an anonymous {@code BEGIN … END} block; {@code WITH} introduces a
 * query today and could introduce {@code WITH … INSERT} tomorrow. Both are invisible from the text
 * and unambiguous in the tree.
 */
public final class WalStatementKinds {

    private WalStatementKinds() {
    }

    /**
     * Whether this script has anything worth logging: true when ANY of its top-level statements
     * mutates. A script is logged or skipped whole, since that is the unit the log replays.
     */
    public static boolean isDurable(final FrostlakeParser.SqlScriptContext script) {
        if (script == null) {
            // No tree to read — log it. An unparseable statement never executed, and a statement we
            // failed to classify is safer in the log than missing from it.
            return true;
        }
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                if (isDurable(statement)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a statement of a family the log leaves out writes what it must carry all the same: a sequence's
     * NEXTVAL, however it is spelled, moves the sequence; a TO_QUERY may run a CALL; and SYSTEM$TASK_DEPENDENTS_ENABLE
     * resumes a task graph. Read off the statement's tokens, as {@code ReadOnlyRequest} judges a read-only request.
     *
     * @param sql the statement's text
     * @return true when it writes durable state
     */
    public static boolean writesDurably(final String sql) {
        for (final Token token : tokens(sql)) {
            final String text = token.getText();
            if (token.getType() == FrostlakeLexer.NEXTVAL || "\"NEXTVAL\"".equalsIgnoreCase(text)
                    || "TO_QUERY".equalsIgnoreCase(text) || "\"TO_QUERY\"".equalsIgnoreCase(text)
                    || token.getType() == FrostlakeLexer.SYSTEM_FUNC
                        && "SYSTEM$TASK_DEPENDENTS_ENABLE".equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    private static List<Token> tokens(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        return stream.getTokens();
    }

    /** Whether one statement mutates anything the log would have to replay. */
    public static boolean isDurable(final FrostlakeParser.StatementContext statement) {
        if (statement == null) {
            return true;
        }
        return statement.queryStatement() == null
            && statement.explainStatement() == null
            && statement.showStatement() == null
            && statement.describeStatement() == null
            && statement.transactionStatement() == null;
    }

    /**
     * Whether a script runs statements of its own — a CALL, a block or another compound statement, an EXECUTE
     * IMMEDIATE — whose work stands when the script fails: each statement it runs commits as it ends under autocommit,
     * or stays in the caller's transaction, and a procedure's failure undoes none of them. No record of the script
     * carries that work, since the script is logged only when it succeeds.
     *
     * @param script the script's parse
     * @return whether one of its statements runs statements of its own
     */
    public static boolean runsStatementsOfItsOwn(final FrostlakeParser.SqlScriptContext script) {
        if (script == null) {
            return false;
        }
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                final FrostlakeParser.ProceduralStatementContext procedural = statement.proceduralStatement();
                if (procedural != null && (procedural.beginEndBlock() != null || procedural.ifStatement() != null
                        || procedural.caseStatement() != null || procedural.loopStatement() != null
                        || procedural.whileStatement() != null || procedural.repeatStatement() != null
                        || procedural.forStatement() != null || procedural.callIntoStatement() != null
                        || procedural.callStatement() != null || procedural.systemCallStatement() != null
                        || procedural.asyncStatement() != null || procedural.awaitStatement() != null
                        || procedural.executeImmediateStatement() != null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a statement commits work in a session of its own, whatever transaction runs it: EXECUTE TASK, EXECUTE
     * ALERT and ALTER PIPE … REFRESH. A block or a procedure that runs one has it logged in a record of its own.
     *
     * @param statement the statement
     * @return whether it runs work in a session of its own
     */
    public static boolean runsInOwnSession(final FrostlakeParser.StatementContext statement) {
        if (statement == null) {
            return false;
        }
        if (statement.taskStatement() != null) {
            return true;
        }
        final FrostlakeParser.AlterStatementContext alter =
            statement.ddlStatement() == null ? null : statement.ddlStatement().alterStatement();
        return alter != null && alter.PIPE() != null && alter.pipeAction() != null
            && alter.pipeAction().REFRESH() != null;
    }

    /**
     * {@link #runsInOwnSession(FrostlakeParser.StatementContext)} for a text: whether it is one such statement.
     *
     * @param script the text's parse
     * @return whether it is a single statement that runs work in a session of its own
     */
    public static boolean runsInOwnSession(final FrostlakeParser.SqlScriptContext script) {
        if (script == null || script.flowChain().size() != 1 || script.flowChain().get(0).statement().size() != 1) {
            return false;
        }
        return runsInOwnSession(script.flowChain().get(0).statement().get(0));
    }
}
