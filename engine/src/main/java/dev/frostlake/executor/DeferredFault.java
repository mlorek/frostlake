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

import dev.frostlake.storage.Row;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A cell whose value faulted while a FROM relation computed it (see {@link RelationBody}). Live computes a
 * relation's select item only where the reading statement reaches that row's value: a row the outer filter
 * drops, a branch that is not taken, a column that is not read, one row that is never compared are not
 * computed, and their faults never raise (live-verified). The relation therefore keeps the fault in the cell,
 * and whatever reads the cell - a column reference, a join or grouping key, a sort key, a write, the result
 * handed out - raises it, the original exception unchanged.
 */
public final class DeferredFault {
    /**
     * The faults cells hold, by identity, so a catch that turns a failure into something else - a NULL key,
     * an invalid identifier - can let a deferred one through unchanged.
     */
    private static final Map<Throwable, Boolean> HELD =
        Collections.synchronizedMap(new WeakHashMap<Throwable, Boolean>());

    private final RuntimeException fault;

    public DeferredFault(final RuntimeException fault) {
        this.fault = fault;
        HELD.put(fault, Boolean.TRUE);
    }

    /**
     * Whether a failure is a fault some cell held, raised because a statement read that cell.
     *
     * @param failure the failure caught
     * @return whether it came out of a deferred cell
     */
    public static boolean isHeld(final Throwable failure) {
        return failure != null && HELD.containsKey(failure);
    }

    /** The fault this cell holds. */
    public RuntimeException getFault() {
        return fault;
    }

    /**
     * The value a cell holds, raising the fault a deferred cell holds instead.
     *
     * @param value the cell
     * @return the value itself
     */
    public static Object read(final Object value) {
        if (value instanceof DeferredFault) {
            throw ((DeferredFault) value).fault;
        }
        return value;
    }

    /**
     * Raises the first deferred fault these rows hold, in row order and then column order - what handing the
     * rows out, or writing them, reads. The column is recorded as a failing projection's is (see
     * {@link ProjectionSlot}), so a write names it: {@code DML operation to table TGT2 failed on column C with
     * error: …} (live-verified).
     *
     * @param rows the rows
     */
    public static void requireNone(final List<Row> rows) {
        if (rows == null) {
            return;
        }
        for (final Row row : rows) {
            if (row == null) {
                continue;
            }
            final List<Object> values = row.getValues();
            for (int column = 0; column < values.size(); column++) {
                if (values.get(column) instanceof DeferredFault) {
                    ProjectionSlot.failedAt(column);
                    throw ((DeferredFault) values.get(column)).fault;
                }
            }
        }
    }

    /**
     * Whether a row fault may wait in its cell: a compilation error never does, because live raises it
     * whatever the rows are.
     *
     * @param failure the failure an item's evaluation raised
     * @return whether it is a row fault
     */
    public static boolean deferrable(final RuntimeException failure) {
        return !(failure instanceof SecurityException) && !SqlCompilationError.isCompilationError(failure.getMessage());
    }

    @Override
    public String toString() {
        return "<deferred fault: " + fault.getMessage() + ">";
    }
}
