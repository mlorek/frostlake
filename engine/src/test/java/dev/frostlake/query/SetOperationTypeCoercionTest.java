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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake unifies the column types of set-operation branches before comparing rows: a VARCHAR branch
 * against a TIMESTAMP branch coerces to TIMESTAMP, VARCHAR against NUMBER coerces to NUMBER, and NUMBER
 * branches of different scales compare by value. The canonical real-world shape is a test fixture's
 * expected table built by CTAS from string literals ({@code '2025-09-29 14:49:57.461'}) EXCEPT-compared
 * against a typed TIMESTAMP_NTZ column — Snowflake reports no difference, so Frostlake must not either.
 * Coercion must never fire between two VARCHAR branches ({@code '01'} stays distinct from {@code '1'})
 * and numeric key comparison must stay exact for NUMBER(38,0) values beyond double precision.
 */
public class SetOperationTypeCoercionTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return engine.executeQuery(sql).getRowCount();
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
    public void unmatchableStringStaysDistinct() {
        // A string that is not a valid timestamp cannot coerce; the row simply never matches.
        engine.execute("CREATE TABLE um_ts (ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO um_ts VALUES ('2025-09-29 14:49:57.461')");
        assertEquals(1, count("SELECT 'not-a-timestamp' EXCEPT SELECT ts FROM um_ts"));
    }

    @Test
    public void exceptAllRespectsCoercedCounts() {
        engine.execute("CREATE TABLE ea_ts (ts TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO ea_ts VALUES ('2025-09-29 14:49:57.461')");
        assertEquals(1, count("""
            SELECT '2025-09-29 14:49:57.461' UNION ALL SELECT '2025-09-29 14:49:57.461'
            EXCEPT ALL SELECT ts FROM ea_ts"""));
    }
}
