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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A materialized view and a dynamic table are FILLED at CREATE, where a plain view is not. Their bodies run
 * then, so a value that faults refuses the CREATE:
 * <ul>
 *   <li>a materialized view inside the write envelope a CTAS earns, naming the view (qualified one level
 *       up) and the column whose projection stopped, except that an aggregate's fault is refused bare;</li>
 *   <li>a dynamic table in its initial refresh's sentence, unless it is to INITIALIZE ON_SCHEDULE.</li>
 * </ul>
 * An existing object that is not being replaced is never rebuilt, and a failed OR REPLACE leaves the old one
 * standing. Every cell is live-verified.
 */
public class MaterializedCreateFaultTest extends BaseDatabaseTest {

    private static final String TOO_LONG = "String 'abcdefgh' is too long and would be truncated";
    private static final String ACCEPTED = "<accepted>";
    private static final String LAG = " TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH";
    private static final String CAST = " AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nc (s VARCHAR, n INT)");
        engine.execute("INSERT INTO nc VALUES ('abcdefgh', 1)");
    }

    /** The refusal with newlines shown as '|' and a refresh's timestamp masked, or "<accepted>". */
    private String outcome(final String sql) {
        try {
            engine.executeQuery(sql);
            return ACCEPTED;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|')
                .replaceAll("data_timestamp [0-9]+ ", "data_timestamp <ms> ");
        }
    }

    private String firstValue(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private static String dml(final String table, final String column, final String error) {
        return "DML operation to table " + table + " failed on column " + column + " with error: " + error;
    }

    private static String refresh(final String error) {
        return "SQL compilation error: Failed to refresh dynamic table with refresh_trigger INITIAL at data_timestamp"
            + " <ms> because of the error: SQL compilation error: Target table failed to refresh: " + error;
    }

    @Test
    public void aMaterializedViewRunsItsBodyAtCreate() {
        final String at = "TEST_DB.TEST_SCHEMA.";
        assertEquals(dml(at + "MV_CAST", "C", TOO_LONG), outcome("CREATE MATERIALIZED VIEW mv_cast" + CAST));
        assertEquals(dml(at + "MV_DIV", "Z", "Division by zero"),
            outcome("CREATE MATERIALIZED VIEW mv_div AS SELECT 1 / (n - 1) AS z FROM nc"));
        assertEquals(dml(at + "MV_SECOND", "C", TOO_LONG),
            outcome("CREATE MATERIALIZED VIEW mv_second AS SELECT n, CAST(s AS VARCHAR(5)) AS c FROM nc"));
        assertEquals(dml(at + "MV_SEC", "C", TOO_LONG), outcome("CREATE SECURE MATERIALIZED VIEW mv_sec" + CAST));
        assertEquals(dml(at + "MV_COLS", "X", TOO_LONG),
            outcome("CREATE MATERIALIZED VIEW mv_cols (x) AS SELECT CAST(s AS VARCHAR(5)) FROM nc"));
        assertEquals(dml(at + "MV_CONV", "V", "Numeric value 'abc' is not recognized"),
            outcome("CREATE MATERIALIZED VIEW mv_conv AS SELECT TO_NUMBER('abc') AS v FROM nc"));
        assertEquals(dml(at + "MV_DATE", "D", "Date 'nodate' is not recognized"),
            outcome("CREATE MATERIALIZED VIEW mv_date AS SELECT TO_DATE('nodate') AS d FROM nc"));
        assertEquals(dml(at + "MV_WHERE", "C", TOO_LONG),
            outcome("CREATE MATERIALIZED VIEW mv_where AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc WHERE n = 1"));
        assertEquals(dml(at + "MV_SQ", "C", TOO_LONG), outcome("CREATE MATERIALIZED VIEW test_schema.mv_sq" + CAST));
        assertEquals(dml(at + "MV_INE", "C", TOO_LONG),
            outcome("CREATE MATERIALIZED VIEW IF NOT EXISTS mv_ine" + CAST), "absent, so IF NOT EXISTS forgives nothing");
        assertEquals(TOO_LONG, outcome("CREATE MATERIALIZED VIEW mv_grp AS SELECT n, MAX(CAST(s AS VARCHAR(5))) AS m"
            + " FROM nc GROUP BY n"), "an aggregate's fault names no column");
    }

    @Test
    public void aMaterializedViewRefusesBeforeItRuns() {
        assertEquals("SQL compilation error:|Missing column specification",
            outcome("CREATE MATERIALIZED VIEW mv_noalias AS SELECT CAST(s AS VARCHAR(5)) FROM nc"));
        assertEquals("SQL compilation error: error line 1 at position 42|invalid identifier 'NOSUCHCOL'",
            outcome("CREATE MATERIALIZED VIEW mv_bad AS SELECT nosuchcol FROM nc"));
        assertEquals(ACCEPTED,
            outcome("CREATE MATERIALIZED VIEW mv_empty AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc WHERE n = 2"));
        assertEquals("0", firstValue("SELECT COUNT(*) FROM mv_empty"));
    }

    @Test
    public void aFullyQualifiedMaterializedViewIsNamedWithItsAccount() {
        final String account = firstValue("SELECT CURRENT_ACCOUNT()");
        assertEquals(dml(account + ".TEST_DB.TEST_SCHEMA.MV_FQ", "C", TOO_LONG),
            outcome("CREATE MATERIALIZED VIEW test_db.test_schema.mv_fq" + CAST));
    }

    @Test
    public void anExistingMaterializedViewIsNotRebuilt() {
        assertEquals(ACCEPTED, outcome("CREATE MATERIALIZED VIEW mv_ok AS SELECT n FROM nc"));
        assertEquals(ACCEPTED, outcome("CREATE MATERIALIZED VIEW IF NOT EXISTS mv_ok" + CAST));
        assertEquals("SQL compilation error:|Object 'MV_OK' already exists.",
            outcome("CREATE MATERIALIZED VIEW mv_ok" + CAST));
        assertEquals(dml("TEST_DB.TEST_SCHEMA.MV_OK", "C", TOO_LONG),
            outcome("CREATE OR REPLACE MATERIALIZED VIEW mv_ok" + CAST));
        assertEquals("1", firstValue("SELECT COUNT(*) FROM mv_ok"), "the old view stands");
    }

    @Test
    public void aDynamicTableRefreshesAtCreate() {
        assertEquals(refresh(TOO_LONG), outcome("CREATE DYNAMIC TABLE dt_cast" + LAG + CAST));
        assertEquals(refresh("Division by zero"),
            outcome("CREATE DYNAMIC TABLE dt_div" + LAG + " AS SELECT 1 / (n - 1) AS z FROM nc"));
        assertEquals(refresh(TOO_LONG),
            outcome("CREATE DYNAMIC TABLE dt_second" + LAG + " AS SELECT n, CAST(s AS VARCHAR(5)) AS c FROM nc"));
        assertEquals(refresh(TOO_LONG), outcome("CREATE DYNAMIC TABLE dt_grp" + LAG
            + " AS SELECT n, MAX(CAST(s AS VARCHAR(5))) AS m FROM nc GROUP BY n"));
        assertEquals(refresh("Division by zero"), outcome("CREATE DYNAMIC TABLE dt_init" + LAG
            + " INITIALIZE = ON_CREATE AS SELECT 1 / (n - 1) AS z FROM nc"));
        assertEquals(refresh(TOO_LONG), outcome("CREATE DYNAMIC TABLE test_db.test_schema.dt_fq" + LAG + CAST));
        assertEquals(ACCEPTED, outcome("CREATE DYNAMIC TABLE dt_sched" + LAG + " INITIALIZE = ON_SCHEDULE" + CAST));
        assertEquals(ACCEPTED,
            outcome("CREATE DYNAMIC TABLE dt_empty" + LAG + " AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc WHERE n = 2"));
    }

    @Test
    public void aDynamicTableCompilesAtCreateWhateverItsInitialization() {
        assertEquals("SQL compilation error: error line 1 at position 82|invalid identifier 'NOSUCHCOL'",
            outcome("CREATE DYNAMIC TABLE dt_bad TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT nosuchcol FROM nc"));
        assertEquals("SQL compilation error: error line 1 at position 108|invalid identifier 'NOSUCHCOL'",
            outcome("CREATE DYNAMIC TABLE dt_bad2 TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH INITIALIZE = ON_SCHEDULE"
                + " AS SELECT nosuchcol FROM nc"));
    }

    @Test
    public void anExistingDynamicTableIsNotRebuilt() {
        assertEquals(ACCEPTED, outcome("CREATE DYNAMIC TABLE dt_ok" + LAG + " AS SELECT n FROM nc"));
        assertEquals(ACCEPTED, outcome("CREATE DYNAMIC TABLE IF NOT EXISTS dt_ok" + LAG + CAST));
        assertEquals("SQL compilation error:|Object 'DT_OK' already exists.",
            outcome("CREATE DYNAMIC TABLE dt_ok" + LAG + CAST));
        assertEquals(refresh(TOO_LONG), outcome("CREATE OR REPLACE DYNAMIC TABLE dt_ok" + LAG + CAST));
        assertEquals("1", firstValue("SELECT COUNT(*) FROM dt_ok"), "the old table stands");
    }
}
