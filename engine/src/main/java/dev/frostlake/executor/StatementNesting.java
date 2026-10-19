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
 * How deep dynamic texts run inside one another, and what a statement reports past that depth or past what the
 * engine's stack holds. A text EXECUTE IMMEDIATE runs may itself be an EXECUTE IMMEDIATE, and so on: an account
 * runs a thousand and one such texts inside one another and reports no bound (live-verified; a block among them
 * counts as a call, and calls nest sixteen deep). Here the texts nest to {@link #LIMIT}, well inside what the
 * engine's stack holds, and the statement past it fails with the engine's own sentence, in the words of the
 * failure an account reports for work it had to abandon. It fails as any statement does: a handler may catch it,
 * the history holds its row, and the session goes on.
 */
public final class StatementNesting {

    /** How many dynamic texts run inside one another. */
    public static final int LIMIT = 300;

    /** The failure's sentence. */
    public static final String REFUSAL = "SQL execution internal error:\nProcessing aborted: the statement nests "
        + "deeper than the engine can follow.";

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    private StatementNesting() {
    }

    /** A dynamic text starts running: refused past {@link #LIMIT}, and then {@link #exit()} is not owed. */
    public static void enter() {
        final int[] depth = DEPTH.get();
        if (depth[0] >= LIMIT) {
            throw new RuntimeException(REFUSAL);
        }
        depth[0]++;
    }

    /** The dynamic text {@link #enter()} admitted has run. */
    public static void exit() {
        final int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }
}
