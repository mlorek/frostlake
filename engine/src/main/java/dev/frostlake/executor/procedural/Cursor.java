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

import dev.frostlake.executor.BodyAbort;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

/**
 * Represents a cursor for iterating through query results in stored procedures
 */
public class Cursor {

    private final String name;
    private final String selectQuery;
    private final String resultSetVariableName;
    private ResultSet resultSet;
    private int currentPosition;
    private boolean isOpen;
    /** Whether OPEN ever ran (a FOR loop opens implicitly). */
    private boolean everOpened;
    /** Whether the FOR loop that iterated the cursor closed it: a CLOSE written after such a loop runs clean. */
    private boolean closedByLoop;

    public Cursor(final String name, final String selectQuery) {
        this(name, selectQuery, null);
    }

    public Cursor(final String name, final String selectQuery, final String resultSetVariableName) {
        this.name = name;
        this.selectQuery = selectQuery;
        this.resultSetVariableName = resultSetVariableName;
        this.currentPosition = -1;
        this.isOpen = false;
    }

    public String getName() {
        return name;
    }

    public String getSelectQuery() {
        return selectQuery;
    }

    /** The RESULTSET variable this cursor iterates, or null when it is declared over a SELECT query. */
    public String getResultSetVariableName() {
        return resultSetVariableName;
    }

    public void open(final ResultSet resultSet) {
        if (isOpen) {
            // An OPEN of a cursor that is open is a statement error (live-verified).
            throw new RuntimeException("Stored procedure execution error: CURSOR '" + name + "' is already open.");
        }
        this.resultSet = resultSet;
        this.currentPosition = -1;
        this.isOpen = true;
        this.everOpened = true;
        this.closedByLoop = false;
    }

    /** True when a FOR loop ran this cursor to its end and nothing has opened or closed it since. */
    public boolean wasEndedByLoop() {
        return isOpen && closedByLoop;
    }

    /**
     * Leaves the cursor as the FOR loop that iterated it does when it ends: open, past its last row, so that a FETCH
     * finds no row and a CLOSE closes it (live-verified). A FOR that names it again opens it afresh.
     */
    public void endLoop() {
        if (resultSet != null) {
            this.currentPosition = resultSet.getRowCount();
        }
        this.closedByLoop = true;
    }

    /** True once this cursor has been opened at least once, however it was later closed. */
    public boolean wasEverOpened() {
        return everOpened;
    }

    public Row fetch() {
        if (!isOpen) {
            // A FETCH from a cursor that is not open is no exception of the block: no handler catches it, and
            // the statement fails with this sentence alone (live-verified).
            throw new BodyAbort("CURSOR " + name + " is not open");
        }
        if (resultSet == null) {
            return null;
        }

        currentPosition++;

        if (currentPosition >= resultSet.getRowCount()) {
            return null; // End of result set
        }

        return resultSet.getRows().get(currentPosition);
    }

    public void close() {
        if (!isOpen) {
            throw new RuntimeException("CURSOR " + name + " is not open");
        }
        this.isOpen = false;
        this.closedByLoop = false;
        this.resultSet = null;
        this.currentPosition = -1;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public boolean hasNext() {
        if (!isOpen || resultSet == null) {
            return false;
        }
        return currentPosition < resultSet.getRowCount() - 1;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }
}
