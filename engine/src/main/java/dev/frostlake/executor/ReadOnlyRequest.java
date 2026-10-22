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

import dev.frostlake.functions.SystemFunctionNames;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a request only reads. One judgement serves two callers: the server, which runs a request that
 * only reads under its shared lock, and a client that asks for a request to be refused unless it only reads.
 *
 * <p>A request only reads when every statement of it is a query, an EXPLAIN, a SHOW or a DESCRIBE, and none
 * of them reaches a write another way: a sequence's NEXTVAL, however it is spelled, moves the sequence;
 * TO_QUERY may compile a CALL, and a procedure called so writes as it is written; and a few system functions
 * act on the server — they end a session or a transaction, cancel a query, set a task's return value or
 * resume its dependents. A procedure called in a FROM clause is no such way: its statements are child jobs
 * of the query and may not write (see {@link FromClauseProcedureJobs}).
 *
 * <p>The judgement is of the request's own text. What a statement reaches while it runs — the text a block
 * hands to EXECUTE IMMEDIATE, the body of a procedure — is held to the same rule as it runs, under
 * {@link #enter}: a statement that is none of the four is refused there. The bodies the text's names stand for
 * are followed by {@link ReadOnlyBodies}, which needs the catalog.
 */
public final class ReadOnlyRequest {

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /** The system functions that act on the server rather than read it. */
    private static final Set<String> ACTING = Set.of("SYSTEM$ABORT_SESSION", "SYSTEM$ABORT_TRANSACTION",
        "SYSTEM$CANCEL_ALL_QUERIES", "SYSTEM$CANCEL_QUERY", "SYSTEM$SET_RETURN_VALUE",
        "SYSTEM$TASK_DEPENDENTS_ENABLE", "SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS");

    private static final String PREFIX = "Read-only request: ";

    private static final String ONLY = " Only a query, SHOW, DESCRIBE and EXPLAIN run.";

    private ReadOnlyRequest() {
    }

    /**
     * Whether a request may run beside others that read: every statement of it only reads. A text that does
     * not parse is no such request.
     *
     * @param sql the request's text
     * @return true when nothing in the text can write
     */
    public static boolean reads(final String sql) {
        try {
            return judge(sql) == null;
        } catch (final RuntimeException doesNotParse) {
            return false;
        }
    }

    /**
     * Why a request that asked to be read-only may not run.
     *
     * @param sql the request's text
     * @return the refusal, or null when the text only reads — and for a text that does not parse, which is
     *     refused for its syntax when it runs
     */
    public static String refusal(final String sql) {
        try {
            return judge(sql);
        } catch (final RuntimeException doesNotParse) {
            return null;
        }
    }

    /** A read-only request begins to run on this thread: what its statements reach is held to the rule. */
    public static void enter() {
        DEPTH.get()[0]++;
    }

    /** The read-only request has run. */
    public static void exit() {
        DEPTH.get()[0]--;
    }

    /**
     * Refuses a statement a read-only request reaches while it runs, when it is no reading statement;
     * returns quietly outside such a request.
     *
     * @param statement the statement about to run
     */
    static void require(final FrostlakeParser.StatementContext statement) {
        if (DEPTH.get()[0] > 0 && !runsReadOnly(statement)) {
            throw new RuntimeException(writes(statement));
        }
    }

    private static String judge(final String sql) {
        final FrostlakeParser.SqlScriptContext script = QueryExecutor.parsedScriptQuietly(sql);
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                if (!readsOnly(statement)) {
                    return writes(statement);
                }
            }
        }
        return tokenRefusal(sql);
    }

    /**
     * Why a text reaches a write by what it calls, whatever statement holds it: a NEXTVAL, a TO_QUERY, a system
     * function that acts on the server.
     *
     * @param sql the text
     * @return the refusal, or null when the text calls none of them
     */
    static String tokenRefusal(final String sql) {
        for (final Token token : tokens(sql)) {
            final int type = token.getType();
            final String text = token.getText();
            if (type == FrostlakeLexer.NEXTVAL || type == FrostlakeLexer.QUOTED_IDENTIFIER
                    && "\"NEXTVAL\"".equalsIgnoreCase(text)) {
                return PREFIX + "NEXTVAL moves a sequence.";
            }
            if ("TO_QUERY".equalsIgnoreCase(text) || type == FrostlakeLexer.QUOTED_IDENTIFIER
                    && "\"TO_QUERY\"".equalsIgnoreCase(text)) {
                return PREFIX + "TO_QUERY may run a procedure.";
            }
            if (type == FrostlakeLexer.SYSTEM_FUNC) {
                final String name = text.toUpperCase(Locale.ROOT);
                if (ACTING.contains(name) || !SystemFunctionNames.contains(name)) {
                    return PREFIX + name + " may act on the server.";
                }
            }
        }
        return null;
    }

    /**
     * The names a text holds, each as the catalog keeps it: a bare word folded to upper case, a quoted one as
     * written between its quotes, and a text that reads as an identifier reference, the way IDENTIFIER() reads
     * it, by its last part. Every word counts, keywords too, since an object may carry such a name.
     *
     * @param sql the text
     * @return the names, in the order the text holds them
     */
    static List<String> names(final String sql) {
        final List<String> names = new ArrayList<>();
        for (final Token token : tokens(sql)) {
            final String text = token.getText();
            if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
                final String[] parts = SqlIdentifiers.canonicalTextParts(text);
                names.add(parts[parts.length - 1]);
            } else if (token.getType() == FrostlakeLexer.STRING_LITERAL && text.length() >= 2) {
                final String content = text.substring(1, text.length() - 1).replace("''", "'");
                if (SqlIdentifiers.isIdentifierReference(content)) {
                    final String[] parts = SqlIdentifiers.canonicalTextParts(content.trim());
                    names.add(parts[parts.length - 1]);
                }
            } else if (!text.isEmpty() && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_')) {
                names.add(text.toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static List<Token> tokens(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        return stream.getTokens();
    }

    /** A query (SELECT / WITH, however wrapped), EXPLAIN, SHOW or DESCRIBE — nothing else reads only. */
    private static boolean readsOnly(final FrostlakeParser.StatementContext statement) {
        if (statement.queryStatement() != null || statement.explainStatement() != null) {
            return true;
        }
        final int first = statement.getStart().getType();
        return first == FrostlakeParser.SHOW || first == FrostlakeParser.DESCRIBE || first == FrostlakeParser.DESC;
    }

    /**
     * What a read-only request may reach while it runs: a reading statement, and the scripting that carries
     * one — a CALL, a block, an EXECUTE IMMEDIATE — whose own statements are judged as they run.
     */
    private static boolean runsReadOnly(final FrostlakeParser.StatementContext statement) {
        return readsOnly(statement) || statement.proceduralStatement() != null;
    }

    private static String writes(final FrostlakeParser.StatementContext statement) {
        return PREFIX + "a statement that begins with " + statement.getStart().getText().toUpperCase(Locale.ROOT)
            + " may write." + ONLY;
    }
}
