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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Attaching a DATA METRIC FUNCTION to a table. The attachment is recorded and the metric is never
 * evaluated — nothing computes a value and no scheduled run happens (see docs/scope.md) — so what is
 * held to live here is which statements are accepted and how the rest are refused.
 *
 * <p>The measured shape: only the SNOWFLAKE.CORE metrics resolve (a bare name does not), a repeated
 * ADD is silent while a DROP that finds nothing is refused, and SUSPEND / RESUME are per attachment.
 */
public class DataMetricFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE dm_t (id NUMBER, txt VARCHAR, d DATE)");
    }

    @Test
    public void aColumnMetricAttachesToItsColumn() {
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (txt)");
    }

    /** The table-level metrics take no column at all. */
    @Test
    public void aTableMetricTakesNoColumns() {
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.ROW_COUNT ON ()");
    }

    @Test
    public void attachingTheSameMetricTwiceIsSilent() {
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
    }

    @Test
    public void aMetricThatIsNotASystemOneIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION"
                    + " SNOWFLAKE.CORE.NO_SUCH_M ON (id)");
            }
        });
        assertEquals("SQL compilation error:\nFunction 'SNOWFLAKE.CORE.NO_SUCH_M'"
            + " does not exist or not authorized.", ex.getMessage());
    }

    /** The name must be qualified: the bare metric resolves to nothing. */
    @Test
    public void aBareMetricNameDoesNotResolve() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION NULL_COUNT ON (id)");
            }
        });
        assertEquals("SQL compilation error:\nFunction 'NULL_COUNT' does not exist or not authorized.",
            ex.getMessage());
    }

    @Test
    public void anUnknownColumnIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION"
                    + " SNOWFLAKE.CORE.NULL_COUNT ON (no_such_c)");
            }
        });
        assertEquals("SQL compilation error:\ncolumn 'NO_SUCH_C' does not exist", ex.getMessage());
    }

    @Test
    public void anAttachmentMayBeSuspendedAndResumed() {
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
        engine.execute("ALTER TABLE dm_t MODIFY DATA METRIC FUNCTION"
            + " SNOWFLAKE.CORE.NULL_COUNT ON (id) SUSPEND");
        engine.execute("ALTER TABLE dm_t MODIFY DATA METRIC FUNCTION"
            + " SNOWFLAKE.CORE.NULL_COUNT ON (id) RESUME");
    }

    @Test
    public void droppingAnAttachmentThatIsNotThereIsRefused() {
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
        engine.execute("ALTER TABLE dm_t DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE dm_t DROP DATA METRIC FUNCTION"
                    + " SNOWFLAKE.CORE.NULL_COUNT ON (id)");
            }
        });
        assertEquals("SQL compilation error: Data metric function NULL_COUNT"
            + " is not attached to Table DM_T.", ex.getMessage());
    }

    /** The schedule half was already there; it takes the attachments it schedules. */
    @Test
    public void theScheduleAndTheMetricsLiveTogether() {
        engine.execute("ALTER TABLE dm_t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.ROW_COUNT ON ()");
        engine.execute("ALTER TABLE dm_t SET DATA_METRIC_SCHEDULE = '60 MINUTE'");
        engine.execute("ALTER TABLE dm_t UNSET DATA_METRIC_SCHEDULE");
    }

    /** Neither METRIC nor DATA is reserved. */
    @Test
    public void itsWordsRemainUsableAsNames() {
        engine.execute("CREATE TABLE metric (metric NUMBER, data NUMBER)");
        engine.execute("INSERT INTO metric VALUES (1, 2)");
        assertEquals("1", engine.executeQuery("SELECT metric FROM metric")
            .getRows().get(0).getValue(0).toString());
    }
}
