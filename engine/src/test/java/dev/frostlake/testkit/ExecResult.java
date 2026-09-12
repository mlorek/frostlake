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

package dev.frostlake.testkit;

import java.util.List;
import java.util.Locale;

/**
 * The outcome of one statement, whatever transport ran it: the first result grid as text (a null cell
 * is SQL NULL, a null grid means no result set), the out-of-band DML count when the transport reports
 * one, or the refusal.
 */
public final class ExecResult {

    private List<String> columns;
    private List<List<String>> rows;
    private long updateCount = -1;
    private String errorMessage;
    private String errorCode;
    private String sqlState;

    /** @return the result's column names, or null when the transport has none */
    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(final List<String> names) {
        this.columns = names;
    }

    /** @return the first result grid, or null when the statement produced none */
    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(final List<List<String>> grid) {
        this.rows = grid;
    }

    /** @return the DML count, or -1 when none was reported */
    public long getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(final long count) {
        this.updateCount = count;
    }

    /** @return the refusal's message, or null when the statement succeeded */
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String message) {
        this.errorMessage = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final String code) {
        this.errorCode = code;
    }

    public String getSqlState() {
        return sqlState;
    }

    public void setSqlState(final String state) {
        this.sqlState = state;
    }

    /** @return whether the statement was refused */
    public boolean failed() {
        return errorMessage != null;
    }

    /**
     * Frostlake, like Snowflake, reports a DML count as a result grid ("number of rows inserted" …).
     * When no out-of-band count came through the transport, derive it from that grid the way
     * Snowflake's own JDBC driver does: the first cell of the single count row.
     */
    public void deriveUpdateCountFromGrid() {
        if (updateCount >= 0 || columns == null || columns.isEmpty() || rows == null || rows.size() != 1) {
            return;
        }
        for (final String column : columns) {
            if (column == null || !column.toLowerCase(Locale.ROOT).startsWith("number of")) {
                return;
            }
        }
        try {
            updateCount = Long.parseLong(rows.get(0).get(0).trim());
        } catch (final RuntimeException notACount) {
            // A grid named like a count but holding something else is simply not one.
        }
    }
}
