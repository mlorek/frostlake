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

public class DeclareCursorStatement extends Statement {
    private final String cursorName;
    private final String selectQuery;
    private final String resultSetVariableName;

    public DeclareCursorStatement(final String cursorName, final String selectQuery) {
        this(cursorName, selectQuery, null);
    }

    public DeclareCursorStatement(final String cursorName, final String selectQuery,
                                  final String resultSetVariableName) {
        super(StatementType.DECLARE_CURSOR);
        this.cursorName = cursorName;
        this.selectQuery = selectQuery;
        this.resultSetVariableName = resultSetVariableName;
    }

    public String getCursorName() {
        return cursorName;
    }

    public String getSelectQuery() {
        return selectQuery;
    }

    /** The RESULTSET variable this cursor iterates, or null when it is declared over a SELECT query. */
    public String getResultSetVariableName() {
        return resultSetVariableName;
    }
}
