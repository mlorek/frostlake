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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement that NAMES a warehouse is refused when that warehouse is not there — live-verified, and
 * phrased two different ways for the same missing warehouse:
 *
 * <pre>
 * CREATE TASK … WAREHOUSE = nosuch_wh           -&gt; Nonexistent warehouse NOSUCH_WH was specified.
 * CREATE DYNAMIC TABLE … WAREHOUSE = nosuch_wh  -&gt; Warehouse 'NOSUCH_WH' does not exist.
 * CREATE CORTEX SEARCH SERVICE … WAREHOUSE = …  -&gt; Warehouse 'NOSUCH_WH' does not exist.
 * </pre>
 *
 * <p>And one that is NOT checked: {@code CREATE USER … DEFAULT_WAREHOUSE = nosuch_wh} is accepted, so
 * the rule follows the statements measured rather than every warehouse-shaped property. Frostlake used
 * to accept all of them, which is how a test invented a warehouse called {@code wh} and only found out
 * when the suite was replayed against a real account.
 */
public class WarehouseReferenceTest extends BaseDatabaseTest {

    @BeforeEach
    public void createSource() {
        engine.execute("CREATE TABLE src(id INT, body VARCHAR)");
        engine.execute("CREATE WAREHOUSE present_wh");
    }

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    // ── refused, with the phrasing each statement uses ────────────────────────────

    @Test
    public void aTaskNamingAMissingWarehouseIsRefused() {
        assertEquals("Nonexistent warehouse NOSUCH_WH was specified.",
            refusalOf("CREATE TASK tk WAREHOUSE = nosuch_wh SCHEDULE = '1 minute' AS SELECT 1"));
    }

    @Test
    public void aDynamicTableNamingAMissingWarehouseIsRefused() {
        assertEquals("Warehouse 'NOSUCH_WH' does not exist.",
            refusalOf("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 hour' WAREHOUSE = nosuch_wh"
                + " AS SELECT id FROM src"));
    }

    @Test
    public void aCortexSearchServiceNamingAMissingWarehouseIsRefused() {
        assertEquals("Warehouse 'NOSUCH_WH' does not exist.",
            refusalOf("CREATE CORTEX SEARCH SERVICE svc ON body WAREHOUSE = nosuch_wh"
                + " TARGET_LAG = '1 hour' AS (SELECT id, body FROM src)"));
    }

    /** The refusal names the warehouse upper-cased, however the statement spelled it. */
    @Test
    public void theRefusalUpperCasesTheName() {
        assertTrue(refusalOf("CREATE DYNAMIC TABLE dt2 TARGET_LAG = '1 hour' WAREHOUSE = MiXeD_Wh"
            + " AS SELECT id FROM src").contains("'MIXED_WH'"));
    }

    // ── accepted, because the warehouse is there ──────────────────────────────────

    @Test
    public void anExistingWarehouseIsAccepted() {
        engine.execute("CREATE TASK tk WAREHOUSE = present_wh SCHEDULE = '1 minute' AS SELECT 1");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 hour' WAREHOUSE = present_wh"
            + " AS SELECT id FROM src");
        engine.execute("CREATE CORTEX SEARCH SERVICE svc ON body WAREHOUSE = present_wh"
            + " TARGET_LAG = '1 hour' AS (SELECT id, body FROM src)");
    }

    /** A statement that names no warehouse at all is not a reference, so nothing is checked. */
    @Test
    public void aStatementWithNoWarehouseClauseIsUnaffected() {
        engine.execute("CREATE TASK tk2 SCHEDULE = '1 minute' AS SELECT 1");
    }

    // ── the one live does NOT check ───────────────────────────────────────────────

    /**
     * {@code DEFAULT_WAREHOUSE} is a stored preference rather than a reference the statement uses, and
     * live accepts one that does not exist. Checking it would make Frostlake stricter than Snowflake.
     */
    @Test
    public void aUsersDefaultWarehouseIsNotValidated() {
        engine.execute("CREATE USER u1 DEFAULT_WAREHOUSE = nosuch_wh");
    }
}
