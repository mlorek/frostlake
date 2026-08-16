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

import dev.frostlake.metastore.NoCurrentDatabaseRefusal;

/**
 * The envelope live wraps a DML write failure in — {@code DML operation to table T failed on column C
 * with error: …} — around whatever sentence the value itself earned.
 *
 * <p>★ THE ENVELOPE IS THE DML LAYER'S, NOT THE VALUE'S. The same
 * {@code SELECT COALESCE(va, d) FROM vf} that a CTAS wraps is answered bare when written on its own,
 * so it belongs to the statement writing the rows rather than to the conversion that failed.
 *
 * <p>★ HOW THE TABLE IS NAMED DEPENDS ON THE STATEMENT. A CTAS always spells it FULLY QUALIFIED, even
 * when the CREATE named it bare; every other DML echoes the name the statement wrote, upper-cased.
 * Both live-measured in one run. Only the CTAS half is applied here so far — see the task list for the
 * written-name half, which needs the spelling threaded down from each handler.
 */
public final class DmlWriteTarget {

    private DmlWriteTarget() {
    }

    /**
     * Whether a failure belongs INSIDE the envelope. A COMPILATION refusal does not: live answers
     * {@code CREATE TABLE t AS SELECT NOSUCHFN(x) …} with the bare "SQL compilation error" sentence,
     * because the statement never got far enough to write anything. Only a failure raised while VALUES
     * were being produced is a DML failure, and those carry no prefix at all — which is what makes the
     * prefix the discriminator rather than a list of sentences.
     *
     * @param failure the failure
     * @return true when the envelope applies
     */
    public static boolean isRowTimeFailure(final RuntimeException failure) {
        if (failure instanceof NoCurrentDatabaseRefusal) {
            // Refused while compiling, like any prefixed sentence; live's wording simply has no prefix.
            return false;
        }
        final String message = failure.getMessage();
        return message != null && !message.startsWith("SQL compilation error");
    }

    /**
     * @param table the table as it should be named
     * @param column the column the failure is attributed to
     * @param failure the inner failure, whose sentence is kept verbatim
     * @return the wrapped refusal
     */
    public static RuntimeException failedOnColumn(final String table, final String column,
                                                  final RuntimeException failure) {
        return new RuntimeException("DML operation to table " + table + " failed on column "
            + column + " with error: " + failure.getMessage());
    }
}
