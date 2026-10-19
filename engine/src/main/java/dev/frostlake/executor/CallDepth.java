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

/**
 * How deep bodies may run inside one another, as the account bounds it (live-verified). A body is a procedure's
 * call in any language, a block the session sends and a dynamic text that is a block; a block written inside
 * another and a dynamic text that is one statement are none. Sixteen bodies run inside one another in full. A
 * seventeenth still runs — an IF, an assignment of an expression and a RETURN answer — but the first statement it
 * issues, a query read by one of its expressions included, ends it: the body's own handlers never see the refusal
 * and the body adds no sentence of its own to it. The statement that started the body fails with
 * {@link #REFUSAL}, 90235 / 42601 to a handler, which the sixteenth body may handle as any statement's failure.
 * Nothing the bodies wrote before is undone, as for any failed CALL.
 */
public final class CallDepth {

    /** How many bodies run inside one another in full. */
    public static final int LIMIT = 16;

    /** What the statement that started a body past the limit reports once that body issues a statement. */
    public static final String REFUSAL = "Stored procedure execution error: Stored procedure exceeds the maximum "
        + "allowed number of recursive calls.";

    /** The level of the body that was ended on this thread and has not been reported yet, or zero. */
    private static final ThreadLocal<int[]> ENDED = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    private CallDepth() {
    }

    /**
     * Stands where a body issues a statement: past the limit the body is ended.
     *
     * @param level how many bodies are running, the issuing one included
     */
    public static void requireMayIssue(final int level) {
        if (level > LIMIT) {
            ENDED.get()[0] = level;
            throw new CallDepthExceeded(level);
        }
    }

    /**
     * Whether the body at a level was ended for issuing a statement past the limit: asked, once, by the statement
     * that started the body, whatever the body handed back or let out — a handler language may have caught the
     * refusal, and it is the statement's to report all the same.
     *
     * @param level the level of the body the statement started
     * @return true when that body was ended; the fact is forgotten with the answer
     */
    public static boolean endedAt(final int level) {
        final int[] ended = ENDED.get();
        if (level > 0 && ended[0] == level) {
            ended[0] = 0;
            return true;
        }
        return false;
    }
}
