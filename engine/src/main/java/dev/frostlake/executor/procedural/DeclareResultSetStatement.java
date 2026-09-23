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

public class DeclareResultSetStatement extends Statement {
    private final String resultSetName;
    private final String selectQuery;
    private int queryLine = -1;
    private int queryColumn = -1;

    public DeclareResultSetStatement(final String resultSetName, final String selectQuery) {
        super(StatementType.DECLARE_RESULTSET);
        this.resultSetName = resultSetName;
        this.selectQuery = selectQuery;
    }

    public String getResultSetName() {
        return resultSetName;
    }

    public String getSelectQuery() {
        return selectQuery;
    }

    /**
     * Note where the initialiser's query begins in the block, which a failure running it is reported at.
     *
     * @param line   the query's line
     * @param column the query's column
     */
    public void setQueryAt(final int line, final int column) {
        this.queryLine = line;
        this.queryColumn = column;
    }

    public int getQueryLine() {
        return queryLine;
    }

    public int getQueryColumn() {
        return queryColumn;
    }
}
