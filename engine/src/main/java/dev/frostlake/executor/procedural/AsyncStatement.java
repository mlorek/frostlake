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

/** {@code ASYNC (<statement>)}: a job of the statement, started where the ASYNC stands (see {@link AsyncJob}). */
public class AsyncStatement extends Statement {

    private final String jobText;
    private final int jobLine;
    private final int jobColumn;

    /**
     * @param jobText   the statement inside the parentheses
     * @param jobLine   the line that statement begins on
     * @param jobColumn that statement's first column
     */
    public AsyncStatement(final String jobText, final int jobLine, final int jobColumn) {
        super(StatementType.ASYNC);
        this.jobText = jobText;
        this.jobLine = jobLine;
        this.jobColumn = jobColumn;
    }

    public String getJobText() {
        return jobText;
    }

    public int getJobLine() {
        return jobLine;
    }

    public int getJobColumn() {
        return jobColumn;
    }
}
