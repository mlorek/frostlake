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
 * WHICH select item was being evaluated when a row-time failure was raised. A DML statement's write
 * failure names the TARGET COLUMN — {@code DML operation to table VEMPTY failed on column D with
 * error: …} — but a failure raised while the SOURCE QUERY runs has no column attached to it: the query
 * fails as a whole, several layers below the statement that knows what the columns are for.
 *
 * <p>So the projection records the item it was on as the failure leaves it, and the DML layer reads
 * that back to name the column. The record is written ONLY on the failing path and is cleared by the
 * reader, which is what keeps it out of the way of every query that succeeds.
 *
 * <p>Live names the FIRST failing projection when more than one would fail
 * ({@code INSERT INTO twobad SELECT COALESCE(va, d), COALESCE(va, d)} names D1), which is what
 * recording the item the evaluation stopped on gives for free.
 */
public final class ProjectionSlot {

    private static final ThreadLocal<Integer> FAILED_AT = new ThreadLocal<>();

    private ProjectionSlot() {
    }

    /** Forget any earlier record, before running a query whose failure is about to be attributed. */
    public static void reset() {
        FAILED_AT.remove();
    }

    /**
     * Record the item a failure is leaving.
     *
     * @param index the select item's position
     */
    public static void failedAt(final int index) {
        FAILED_AT.set(Integer.valueOf(index));
    }

    /**
     * The recorded item, and forget it.
     *
     * @return the position, or -1 when nothing was recorded
     */
    public static int takeFailedSlot() {
        final Integer index = FAILED_AT.get();
        FAILED_AT.remove();
        return index == null ? -1 : index.intValue();
    }
}
