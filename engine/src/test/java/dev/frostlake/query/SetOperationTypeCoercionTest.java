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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake unifies the column types of set-operation branches before comparing rows: a VARCHAR branch
 * against a TIMESTAMP branch coerces to TIMESTAMP, VARCHAR against NUMBER coerces to NUMBER, and NUMBER
 * branches of different scales compare by value. The canonical real-world shape is a test fixture's
 * expected table built by CTAS from string literals ({@code '2025-09-29 14:49:57.461'}) EXCEPT-compared
 * against a typed TIMESTAMP_NTZ column — Snowflake reports no difference, so Frostlake must not either.
 * Coercion must never fire between two VARCHAR branches ({@code '01'} stays distinct from {@code '1'})
 * and numeric key comparison must stay exact for NUMBER(38,0) values beyond double precision. A string
 * the unified type cannot read FAILS the statement rather than staying unmatched.
 */
public class SetOperationTypeCoercionTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void exceptMatchesVarcharLiteralAgainstTimestampColumn() {
        engine.execute("CREATE TABLE typed_ts (id INTEGER, ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO typed_ts VALUES (1, '2025-09-29 14:49:57.461'), (2, '2025-10-02 09:05:21')");
        engine.execute("""
            CREATE TABLE expected_ts AS
            SELECT 1 AS id, '2025-09-29 14:49:57.461' AS ts
            UNION ALL
            SELECT 2, '2025-10-02 09:05:21.000'""");
        assertEquals(0, count("SELECT id, ts FROM expected_ts EXCEPT SELECT id, ts FROM typed_ts"));
        assertEquals(0, count("SELECT id, ts FROM typed_ts EXCEPT SELECT id, ts FROM expected_ts"));
    }

    @Test
    public void exceptMatchesVarcharLiteralAgainstDateColumn() {
        engine.execute("CREATE TABLE typed_d (d DATE)");
        engine.execute("INSERT INTO typed_d VALUES ('2025-10-02')");
        assertEquals(0, count("SELECT '2025-10-02' EXCEPT SELECT d FROM typed_d"));
    }

    @Test
    public void intersectCoercesStringAgainstTimestamp() {
        engine.execute("CREATE TABLE its (ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO its VALUES ('2025-09-29 14:49:57.461')");
        assertEquals(1, count("SELECT '2025-09-29 14:49:57.461' INTERSECT SELECT ts FROM its"));
    }

    @Test
    public void unionDistinctMergesNumberAndNumericString() {
        // NUMBER branch + VARCHAR branch → Snowflake coerces the VARCHAR side to NUMBER: one row.
        assertEquals(1, count("SELECT 999001 UNION SELECT '999001'"));
    }

    @Test
    public void numbersOfDifferentScalesCompareByValue() {
        assertEquals(0, count("SELECT 1.0 EXCEPT SELECT 1"));
        assertEquals(1, count("SELECT 406 INTERSECT SELECT 406.0"));
    }

    @Test
    public void twoVarcharBranchesNeverCoerce() {
        // Both branches VARCHAR: text comparison, exactly like Snowflake — '01' and '1' stay distinct.
        assertEquals(1, count("SELECT '01' EXCEPT SELECT '1'"));
        assertEquals(2, count("SELECT '01' UNION SELECT '1'"));
    }

    @Test
    public void hugeNumberKeysStayExact() {
        // 20-digit NUMBER(38,0) values that differ only in low digits collide when squeezed through a
        // double (both round to 2.1000000006420546e19) — the set-op key must stay exact BigDecimal.
        engine.execute("CREATE TABLE big_a (n NUMBER(38,0))");
        engine.execute("CREATE TABLE big_b (n NUMBER(38,0))");
        engine.execute("INSERT INTO big_a VALUES (21000000006420544706)");
        engine.execute("INSERT INTO big_b VALUES (21000000006420546000)");
        assertEquals(1, count("SELECT n FROM big_a EXCEPT SELECT n FROM big_b"));
        assertEquals(0, count("SELECT n FROM big_a EXCEPT SELECT 21000000006420544706"));
    }

    @Test
    public void booleanCoercesAgainstStringBranch() {
        assertEquals(0, count("SELECT TRUE EXCEPT SELECT 'true'"));
        assertEquals(1, count("SELECT TRUE UNION SELECT 'TRUE'"));
    }

    @Test
    public void nullStillMatchesNullAcrossBranches() {
        engine.execute("CREATE TABLE nul_ts (ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO nul_ts VALUES (NULL), ('2025-09-29 14:49:57.461')");
        assertEquals(0, count(
            "SELECT NULL UNION ALL SELECT '2025-09-29 14:49:57.461' EXCEPT SELECT ts FROM nul_ts"));
    }

    @Test
    public void unmatchableStringFailsTheSetOperation() {
        // A string that is not a valid timestamp cannot coerce, and that is an ERROR — not simply an
        // unmatched row. Live-verified on a real account: the EXCEPT below fails
        // "Timestamp 'not-a-timestamp' is not recognized" (INTERSECT the same way), while the coercible
        // string matches and yields zero rows.
        engine.execute("CREATE TABLE um_ts (ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO um_ts VALUES ('2025-09-29 14:49:57.461')");
        final RuntimeException notATimestamp = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                count("SELECT 'not-a-timestamp' EXCEPT SELECT ts FROM um_ts");
            }
        });
        assertTrue(notATimestamp.getMessage().contains("Timestamp 'not-a-timestamp' is not recognized"),
            notATimestamp.getMessage());
        assertEquals(0, count("SELECT '2025-09-29 14:49:57.461' EXCEPT SELECT ts FROM um_ts"));
        // Same rule on the numeric side: SELECT 'abc' UNION SELECT 1 fails "Numeric value 'abc' is not
        // recognized" live, while a numeric-looking string unions fine.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                count("SELECT 'abc' UNION SELECT 1");
            }
        });
        assertEquals(2, count("SELECT '3' UNION SELECT 1"));
    }

    @Test
    public void exceptCoercesTimestampComparison() {
        // EXCEPT ALL is not Snowflake syntax (only UNION takes ALL); plain EXCEPT with the
        // coerced timestamp comparison removes the matching value.
        engine.execute("CREATE TABLE ea_ts (ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO ea_ts VALUES ('2025-09-29 14:49:57.461')");
        assertEquals(0, count("""
            SELECT '2025-09-29 14:49:57.461' UNION ALL SELECT '2025-09-29 14:49:57.461'
            EXCEPT SELECT ts FROM ea_ts"""));
    }

    @Test
    public void emptyLeadingBranchStillTypesTheUnion() {
        // The branch type comes from the leading branch's declared COLUMN type, not from the rows it
        // happens to produce. Live-verified on a real account: the union answers the
        // TIMESTAMP 9999-12-31 00:00:03.000 even though the leading branch is empty, and INSERTing that
        // union succeeds — while INSERTing the bare over-wide literal is rejected by the width-checked
        // DML write path. This is exactly the shape a fixture uses to borrow a table's column layout.
        engine.execute("CREATE TABLE lead_ts (ts TIMESTAMP_NTZ(9))");
        assertEquals("2024-01-01T00:00:03", scalar("""
            SELECT ts FROM lead_ts WHERE FALSE UNION ALL SELECT '2024-01-01 00:00:003'"""));
        engine.execute("""
            INSERT INTO lead_ts(ts)
            WITH c AS (SELECT ts FROM lead_ts WHERE FALSE UNION ALL SELECT '2024-01-01 00:00:003')
            SELECT ts FROM c""");
        assertEquals("2024-01-01T00:00:03", scalar("SELECT ts FROM lead_ts"));
        // An unreadable string still fails, empty leading branch or not.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                count("SELECT ts FROM lead_ts WHERE FALSE UNION ALL SELECT 'not-a-timestamp'");
            }
        });
    }

    @Test
    public void emptyLeadingBranchTypesADateUnionToo() {
        engine.execute("CREATE TABLE lead_d (d DATE)");
        assertEquals("9999-12-31", scalar("SELECT d FROM lead_d WHERE FALSE UNION ALL SELECT '9999-012-31'"));
    }

    @Test
    public void subtractiveOperationsShortCircuitOnAnEmptyLeftSide() {
        // Live-verified on a real account: with an EMPTY numeric left branch, MINUS /
        // EXCEPT / INTERSECT against a VARCHAR branch holding a non-numeric value all answer zero rows
        // WITHOUT converting the right side, while UNION [ALL] over the identical inputs errors
        // "Numeric value '…' is not recognized" and a NON-empty left errors too.
        engine.execute("CREATE TABLE sc_num (n NUMBER(38,0))");
        engine.execute("CREATE TABLE sc_str (s VARCHAR)");
        engine.execute("INSERT INTO sc_str VALUES ('00000000-0000-0000-0000-000000000000')");
        assertEquals(0, count("SELECT n FROM sc_num MINUS SELECT s FROM sc_str"));
        assertEquals(0, count("SELECT n FROM sc_num EXCEPT SELECT s FROM sc_str"));
        assertEquals(0, count("SELECT n FROM sc_num INTERSECT SELECT s FROM sc_str"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                count("SELECT n FROM sc_num UNION ALL SELECT s FROM sc_str");
            }
        });
        engine.execute("INSERT INTO sc_num VALUES (12345)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                count("SELECT n FROM sc_num MINUS SELECT s FROM sc_str");
            }
        });
    }
}
