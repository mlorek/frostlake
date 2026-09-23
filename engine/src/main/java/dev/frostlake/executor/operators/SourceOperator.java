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

package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;

import java.util.List;

/**
 * A stage that answers a relation's rows, whatever rows reach it: the pipeline's first stage over a scanned
 * or derived relation — whose rows it produces when it runs — a relation already in hand, or an empty
 * relation where the plan has settled that no row can qualify. Where the plan needed the rows before this
 * stage ran, the description says so with a trailing {@code *}.
 */
public final class SourceOperator implements Operator {

    private final MemoizedRows rows;
    private final String description;
    private boolean readWhilePlanning;
    private boolean ran;

    /**
     * A relation already in hand.
     *
     * @param rows        the relation's rows
     * @param description how the relation reads in a plan
     */
    public SourceOperator(final List<Row> rows, final String description) {
        this(new MemoizedRows(RowsProvider.of(rows)), description, false);
    }

    /**
     * A relation produced when the stage runs, or before it where the plan needed the rows.
     *
     * @param rows              the relation's rows, produced once
     * @param description       how the relation reads in a plan
     * @param readWhilePlanning whether the rows are known to have been read while planning
     */
    public SourceOperator(final MemoizedRows rows, final String description, final boolean readWhilePlanning) {
        this.rows = rows;
        this.description = description;
        this.readWhilePlanning = readWhilePlanning;
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (!ran) {
            readWhilePlanning |= rows.isProduced();
            ran = true;
        }
        return rows.rows();
    }

    @Override
    public String getDescription() {
        return description + (readWhilePlanning || !ran && rows.isProduced() ? "*" : "");
    }
}
