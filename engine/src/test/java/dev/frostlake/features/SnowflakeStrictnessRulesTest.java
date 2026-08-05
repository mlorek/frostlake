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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live-Snowflake-verified strictness rules: temporal functions reject declared-VARCHAR columns
 * (string constants and derived values stay coercible), VALUES rejects string literals for
 * semi-structured columns, masking policies enforce attachment rules, task cron schedules are
 * validated, and a lateral table function cannot take an ON predicate.
 */
public class SnowflakeStrictnessRulesTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void temporalFunctionsRejectDeclaredVarcharColumns() {
        engine.execute("CREATE TABLE tf_v (d VARCHAR, ts TIMESTAMP)");
        engine.execute("INSERT INTO tf_v VALUES ('2024-04-08', '2024-04-08 10:00:00')");
        // A string CONSTANT is rejected exactly like a column (live-verified: DATE_TRUNC,
        // EXTRACT, LAST_DAY and the part extractors all reject '2024-04-08'); an explicit cast is
        // the portable form.
        assertEquals("2024-04-01",
            String.valueOf(scalar("SELECT DATE_TRUNC('month', '2024-04-08'::DATE)")));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT DATE_TRUNC('month', '2024-04-08')");
            }
        });
        // ...and a declared-VARCHAR column likewise...
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT DATE_TRUNC('month', d) FROM tf_v");
            }
        });
        assertTrue(e.getMessage().contains("does not support VARCHAR("),
            "unexpected: " + e.getMessage());
        // ...while casts, temporal columns, and CTE-derived values all stay usable.
        assertEquals("2024-04-01", String.valueOf(scalar("SELECT DATE_TRUNC('month', d::DATE) FROM tf_v")));
        assertEquals("2024-04-01", String.valueOf(scalar("SELECT DATE_TRUNC('month', ts) FROM tf_v"))
            .substring(0, 10));
        assertEquals(Boolean.TRUE, scalar(
            "WITH t AS (SELECT CURRENT_TIMESTAMP(3) AS end_time) "
            + "SELECT DATEDIFF(millisecond, '2024-01-01', end_time) >= 0 FROM t"));
    }

    @Test
    public void valuesRejectsStringLiteralsForSemiStructuredColumns() {
        engine.execute("CREATE TABLE ss (v VARIANT)");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ss VALUES ('{\"a\": 1}')");
            }
        });
        assertTrue(e.getMessage().contains("expecting VARIANT but got VARCHAR"),
            "unexpected: " + e.getMessage());
        // The Snowflake-supported path.
        engine.execute("INSERT INTO ss SELECT PARSE_JSON('{\"a\": 1}')");
        assertEquals("1", String.valueOf(scalar("SELECT v:a FROM ss")));
    }

    @Test
    public void maskingPolicyAttachmentRulesAreEnforced() {
        engine.execute("CREATE MASKING POLICY sp_m1 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE TABLE sp_t (secret VARCHAR, other VARCHAR)");
        engine.execute("ALTER TABLE sp_t ALTER COLUMN secret SET MASKING POLICY sp_m1");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP MASKING POLICY sp_m1");
            }
        }, "a policy in use cannot be dropped");
        // Re-attaching the SAME policy is a no-op — live-verified on a real account.
        engine.execute("ALTER TABLE sp_t ALTER COLUMN secret SET MASKING POLICY sp_m1");
        // Attaching a DIFFERENT one without unsetting first is the error: "Specified column already
        // attached to another masking policy..." (live-verified).
        engine.execute("CREATE MASKING POLICY sp_m3 AS (v VARCHAR) RETURNS VARCHAR -> '###'");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE sp_t ALTER COLUMN secret SET MASKING POLICY sp_m3");
            }
        }, "one masking policy per column");
        engine.execute("CREATE MASKING POLICY sp_m2 AS (v VARCHAR, s VARCHAR) RETURNS VARCHAR -> '***'");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE sp_t ALTER COLUMN other SET MASKING POLICY sp_m2 USING (other, secret)");
            }
        }, "a masked column cannot be a policy argument");

        engine.execute("ALTER TABLE sp_t ALTER COLUMN secret UNSET MASKING POLICY");
        engine.execute("DROP MASKING POLICY sp_m1");
    }

    @Test
    public void taskCronSchedulesAreValidated() {
        engine.execute("CREATE WAREHOUSE sp_wh");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TASK sp_bad WAREHOUSE = sp_wh SCHEDULE = 'USING CRON * * * UTC' AS SELECT 1");
            }
        }, "too few cron fields");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TASK sp_bad2 WAREHOUSE = sp_wh SCHEDULE = 'USING CRON 0 * * * *' AS SELECT 1");
            }
        }, "the time zone is mandatory");
        engine.execute("CREATE TASK sp_ok WAREHOUSE = sp_wh "
            + "SCHEDULE = 'USING CRON 0 9 * * MON America/Los_Angeles' AS SELECT 1");
        engine.execute("CREATE TASK sp_min WAREHOUSE = sp_wh SCHEDULE = '5 MINUTE' AS SELECT 1");
    }

    @Test
    public void lateralTableFunctionWithOnPredicateIsRejected() {
        engine.execute("CREATE TABLE lt (id INTEGER, tags VARIANT)");
        engine.execute("INSERT INTO lt SELECT 1, PARSE_JSON('[\"a\",\"b\"]')");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT t.id, f.value FROM lt t JOIN LATERAL FLATTEN(INPUT => t.tags) f ON TRUE");
            }
        });
        assertTrue(e.getMessage().contains("lateral table function"), "unexpected: " + e.getMessage());
        // The restriction is specific to a lateral TABLE FUNCTION: the same query with an INNER join
        // and the very same ON TRUE runs (live-verified), so it is the OUTER/predicate rule
        // on TABLE FUNCTIONS, not a ban on predicates over laterals. The null-extending LEFT form of a
        // lateral SUBQUERY lives in LateralLeftJoinNullExtendTest, over plain tables — Snowflake reports
        // "Unsupported subquery type cannot be evaluated" for some OUTER-lateral shapes for reasons of
        // its own (a FROM-less derived table, or this VARIANT-carrying source), and that limitation is
        // not the rule under test here.
        assertEquals(1, engine.executeQuery(
            "SELECT t.id FROM lt t JOIN LATERAL (SELECT 1 AS k) l ON TRUE").getRowCount());
        // The predicate-free spellings stay supported.
        assertEquals(2, engine.executeQuery(
            "SELECT t.id, f.value FROM lt t, LATERAL FLATTEN(INPUT => t.tags) f").getRowCount());
        assertEquals(1, engine.executeQuery(
            "SELECT t.id FROM lt t CROSS JOIN LATERAL (SELECT 1 AS k) l").getRowCount());
    }
}
