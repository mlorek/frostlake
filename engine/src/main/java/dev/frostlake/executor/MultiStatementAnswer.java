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

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What an EXECUTE IMMEDIATE of several statements answers, as a multi-statement request does (live-verified): its
 * own one row — "multiple statement execution": "Multiple statements executed successfully." — which a RESULTSET
 * filled from it holds and RESULT_SCAN reads back, and, for the client that sent it, each statement's own answer in
 * order: a query's rows first, a DML statement's count, a DDL statement's status.
 */
public final class MultiStatementAnswer extends ResultSet {

    /** The column of the statement's own answer. */
    public static final String COLUMN = "multiple statement execution";

    /** The one value of the statement's own answer. */
    public static final String ANSWER = "Multiple statements executed successfully.";

    private final List<ResultSet> statementAnswers;

    /**
     * @param statementAnswers each statement's answer, in the order they ran
     */
    public MultiStatementAnswer(final List<ResultSet> statementAnswers) {
        super(columns(), rows());
        this.statementAnswers = new ArrayList<ResultSet>(statementAnswers);
    }

    /** Each statement's answer, in the order they ran. */
    public List<ResultSet> getStatementAnswers() {
        return Collections.unmodifiableList(statementAnswers);
    }

    private static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(COLUMN, AnonymousBlockResult.TEXT));
        return columns;
    }

    private static List<Row> rows() {
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(ANSWER));
        return rows;
    }
}
