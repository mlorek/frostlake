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

package dev.frostlake.executor.procedural;

/**
 * A job an {@code ASYNC (<statement>)} of a block started: the statement, with the block's variables already
 * bound into it as they stood then, the place it is written at, and the RESULTSET it fills when it has one.
 *
 * <p>The job runs when it is awaited — by {@code AWAIT <resultset>} or by {@code AWAIT ALL}, in the order the
 * jobs were started — and one that is never awaited never takes effect (live-verified: the account ends the jobs
 * a body leaves behind).
 */
public final class AsyncJob {

    private final String statement;
    private final int line;
    private final int column;
    private final ResultSetVariable target;
    private final String targetName;
    private AsyncJobState state = AsyncJobState.PENDING;

    /**
     * @param statement  the statement, its binds written out
     * @param line       the line the statement begins on
     * @param column     the statement's first column
     * @param target     the RESULTSET the job fills, or null for a job no name holds
     * @param targetName that RESULTSET's name, upper-cased, or null
     */
    public AsyncJob(final String statement, final int line, final int column, final ResultSetVariable target,
                    final String targetName) {
        this.statement = statement;
        this.line = line;
        this.column = column;
        this.target = target;
        this.targetName = targetName;
    }

    /** The name of the RESULTSET the job fills, or null. */
    public String getTargetName() {
        return targetName;
    }

    public String getStatement() {
        return statement;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /** The RESULTSET the job fills, or null. */
    public ResultSetVariable getTarget() {
        return target;
    }

    public AsyncJobState getState() {
        return state;
    }

    public void setState(final AsyncJobState state) {
        this.state = state;
    }
}
