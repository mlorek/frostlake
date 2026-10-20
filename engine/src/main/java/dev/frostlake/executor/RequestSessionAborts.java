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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SessionDirectory;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The SYSTEM$ABORT_SESSION calls of a request's queries that name a session by its number written out, carried out as
 * the request is read, before it waits for the engine: the session aborted ends what it runs now at that statement's
 * next checkpoint (see {@link StatementCancellation}), where waiting for the engine would let it run on to its end and
 * commit before the abort could run. The calls are carried out again in their turn, and answer as they would have —
 * an abort answers the same whether or not the session was still there (see {@link SessionAbort}). A call naming the
 * request's own session is left to its turn: a session that aborts itself gets its answer first.
 */
public final class RequestSessionAborts {

    private static final String NAME = "SYSTEM$ABORT_SESSION";

    private RequestSessionAborts() {
    }

    /**
     * Carry out the aborts a request names of sessions other than its own.
     *
     * @param sql        the request's text
     * @param ownSession the number of the session the request runs in
     * @param directory  the engine's abortable sessions
     */
    public static void carryOut(final String sql, final long ownSession, final SessionDirectory directory) {
        final FrostlakeParser.SqlScriptContext script;
        try {
            script = QueryExecutor.parsedScriptQuietly(sql);
        } catch (final RuntimeException doesNotParse) {
            // A text that does not parse is refused for its syntax in its turn, and aborts nothing.
            return;
        }
        final List<Long> named = new ArrayList<Long>();
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                if (statement.queryStatement() != null) {
                    collect(statement.queryStatement(), named);
                }
            }
        }
        for (final Long number : named) {
            if (number.longValue() != ownSession) {
                directory.abort(number.longValue());
            }
        }
    }

    /** The session numbers the calls under {@code node} name by a whole number written out, in the order written. */
    private static void collect(final ParseTree node, final List<Long> into) {
        if (node instanceof FrostlakeParser.SystemFuncExprContext) {
            final FrostlakeParser.SystemFuncExprContext call = (FrostlakeParser.SystemFuncExprContext) node;
            if (NAME.equalsIgnoreCase(call.SYSTEM_FUNC().getText()) && call.booleanExprList() != null
                    && call.booleanExprList().booleanExpr().size() == 1) {
                final Token start = call.booleanExprList().booleanExpr(0).getStart();
                if (start == call.booleanExprList().booleanExpr(0).getStop()
                        && start.getType() == FrostlakeLexer.INTEGER_LITERAL) {
                    // A number wider than 64 bits wraps into them, as the call reads it.
                    into.add(Long.valueOf(new BigInteger(start.getText()).longValue()));
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }
}
