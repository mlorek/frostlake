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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The point a change-tracking read starts or ends at — a stream's AT | BEFORE and a CHANGES clause's AT | BEFORE |
 * END. It may be any expression that answers the same each time it is asked: a text, a number, a variable, a
 * subquery, a call over them. A function whose value moves between two calls cannot anchor the changes, and is
 * refused where it is written, the sentence closing with "(inside a view)." when a query's CHANGES clause wrote it
 * (live-verified).
 *
 * <pre>
 *   AT (TIMESTAMP =&gt; DATEADD(minute, -30, CURRENT_TIMESTAMP()))   function 'CURRENT_TIMESTAMP', at the call
 *   AT (TIMESTAMP =&gt; CURRENT_DATE()::TIMESTAMP_LTZ)                function 'CURRENT_DATE'
 *   AT (OFFSET =&gt; -ABS(RANDOM()) % 2)                              function 'RANDOM'
 *   AT (STATEMENT =&gt; LAST_QUERY_ID())                              function 'LAST_QUERY_ID'
 *   AT (TIMESTAMP =&gt; SYSDATE()) | GETDATE() | SYSTIMESTAMP()       function 'CURRENT_TIMESTAMP', which each is
 *                                                                  written through, at that writing's own place
 *   AT (TIMESTAMP =&gt; TO_TIMESTAMP_LTZ('2020-01-01 00:00:00'))      read
 *   AT (OFFSET =&gt; -60 + 60), AT (OFFSET =&gt; (SELECT 0))             read
 * </pre>
 */
final class ChangeTrackingPoints {

    /** The functions whose value moves, each under the name the refusal tells it by. */
    private static final Map<String, String> MOVING = new HashMap<>();

    /** The place the refusal tells for a function written through CURRENT_TIMESTAMP: its own, not the call's. */
    private static final Map<String, Integer> REWRITTEN_PLACE = new HashMap<>();

    static {
        for (final String name : new String[] {"CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "LOCALTIMESTAMP", "LOCALTIME", "RANDOM", "UUID_STRING", "LAST_QUERY_ID"}) {
            MOVING.put(name, name);
        }
        MOVING.put("SYSDATE", "CURRENT_TIMESTAMP");
        MOVING.put("GETDATE", "CURRENT_TIMESTAMP");
        MOVING.put("SYSTIMESTAMP", "CURRENT_TIMESTAMP");
        REWRITTEN_PLACE.put("SYSDATE", Integer.valueOf(35));
        REWRITTEN_PLACE.put("GETDATE", Integer.valueOf(1));
        REWRITTEN_PLACE.put("SYSTIMESTAMP", Integer.valueOf(1));
    }

    private ChangeTrackingPoints() {
    }

    /**
     * Refuses a point that reads a function whose value moves between two calls.
     *
     * @param point   the point
     * @param inQuery whether a query's CHANGES clause wrote it, rather than a stream's definition
     */
    static void requireRepeatable(final FrostlakeParser.TimeTravelPointContext point, final boolean inQuery) {
        if (point == null || point.STREAM() != null || point.expression() == null) {
            return;
        }
        final Token moving = firstMoving(point.expression());
        if (moving == null) {
            return;
        }
        final String written = moving.getText().toUpperCase(Locale.ROOT);
        final String detail = "Query contains time travel to a version containing function '" + MOVING.get(written)
            + "', but change tracking is not supported on queries with non-deterministic functions in versions."
            + (inQuery ? " (inside a view)." : "");
        final Integer place = REWRITTEN_PLACE.get(written);
        if (place != null) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " line 1 at position " + place + ":\n" + detail);
        }
        throw new RuntimeException(SqlCompilationError.atLine(moving.getLine(), moving.getCharPositionInLine(),
            detail));
    }

    /** The first word under a tree that names a moving function, or null. */
    private static Token firstMoving(final ParseTree tree) {
        if (tree instanceof TerminalNode) {
            final Token token = ((TerminalNode) tree).getSymbol();
            return MOVING.containsKey(token.getText().toUpperCase(Locale.ROOT)) ? token : null;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            final Token found = firstMoving(tree.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
