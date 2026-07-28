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

package dev.frostlake.constraints;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Write-path type coercion (Phase 4), gated by {@code constraints.enforce.types} (default off; ON here).
 * Snowflake coerces values to the column type on write: VARCHAR(n) length is enforced, numeric strings are
 * parsed, and non-numeric strings into numeric columns are rejected. See docs/acid-snowflake-plan.md.
 */
public class TypeCoercionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_CONSTRAINTS_ENFORCE_TYPES, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void expectError(final String sql) {
        try {
            engine.execute(sql);
            fail("expected a type/length error for: " + sql);
        } catch (final RuntimeException expected) {
            // expected
        }
    }

    private long rowCount() {
        return engine.executeQuery("SELECT * FROM t").getRowCount();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void varcharLengthIsEnforced() {
        engine.execute("CREATE TABLE t (v VARCHAR(3))");
        engine.execute("INSERT INTO t VALUES ('abc')");   // exactly 3 → ok
        expectError("INSERT INTO t VALUES ('abcd')");     // 4 > 3 → error
        assertEquals(1, rowCount());
    }

    @Test
    public void nonNumericStringIntoNumericColumnIsRejected() {
        engine.execute("CREATE TABLE t (n NUMBER(10,0))");
        expectError("INSERT INTO t VALUES ('not a number')");
        assertEquals(0, rowCount());
    }

    @Test
    public void numericStringIntoNumericColumnIsParsed() {
        engine.execute("CREATE TABLE t (n NUMBER(10,0))");
        engine.execute("INSERT INTO t VALUES ('123')");   // numeric string → parsed, accepted
        assertEquals(1, rowCount());
    }

    @Test
    public void validValuesPassThrough() {
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR(10))");
        engine.execute("INSERT INTO t VALUES (1, 'hello')");
        engine.execute("INSERT INTO t VALUES (2, 'world')");
        assertEquals(2, rowCount());
    }

    // ---- temporal coercion: a string written into a DATE/TIME/TIMESTAMP column becomes a real temporal
    //      value (the same one TO_DATE/TO_TIMESTAMP yields), so it compares equal to a computed value ----

    @Test
    public void stringIntoTimestampColumnBecomesTemporalValue() {
        engine.execute("CREATE TABLE t (ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO t VALUES ('2024-02-09 12:24:12.000')");
        final Object v = scalar("SELECT ts FROM t");
        assertEquals(LocalDateTime.class, v.getClass());
        assertEquals("2024-02-09T12:24:12", v.toString());
    }

    @Test
    public void stringIntoDateColumnBecomesLocalDate() {
        engine.execute("CREATE TABLE t (d DATE)");
        engine.execute("INSERT INTO t VALUES ('2024-02-09')");
        final Object v = scalar("SELECT d FROM t");
        assertEquals(LocalDate.class, v.getClass());
        assertEquals("2024-02-09", v.toString());
    }

    @Test
    public void stringInsertedTimestampEqualsComputedTimestamp() {
        // The shape a snapshot-loader test relies on: a string-inserted timestamp must compare equal (under
        // '=' and EXCEPT) to a TO_TIMESTAMP_NTZ-computed one, or an expected-vs-actual diff wrongly mismatches.
        engine.execute("CREATE TABLE expected (ts TIMESTAMP_NTZ)");
        engine.execute("CREATE TABLE actual (ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO expected VALUES ('2024-02-09 12:24:12.000')");
        engine.execute("INSERT INTO actual SELECT TO_TIMESTAMP_NTZ('2024-02-09 12:24:12.000')");
        assertEquals(0, engine.executeQuery(
            "SELECT ts FROM expected EXCEPT SELECT ts FROM actual").getRowCount());
        assertEquals(Boolean.TRUE, scalar(
            "SELECT (SELECT ts FROM expected) = (SELECT ts FROM actual)"));
    }

    @Test
    public void stringCastToTimestampEqualsToTimestampNtz() {
        assertEquals(Boolean.TRUE, scalar(
            "SELECT '2024-02-09 12:24:12.000'::TIMESTAMP_NTZ = TO_TIMESTAMP_NTZ('2024-02-09 12:24:12.000')"));
    }

    // ---- fixed-point scale: a value written into a NUMBER(p,s) column is padded to exactly s fractional
    //      digits, so it equals()/EXCEPTs identically to the same value arriving via a ::NUMBER(p,s) cast ----

    @Test
    public void integerIntoScaledNumberColumnIsPaddedToScale() {
        engine.execute("CREATE TABLE t (n NUMBER(8,4))");
        engine.execute("INSERT INTO t VALUES (0)");                 // scale 0 → must become 0.0000
        assertEquals("0.0000", scalar("SELECT n FROM t").toString());
    }

    @Test
    public void insertedScaledNumberEqualsCastScaledNumber() {
        // Under EXCEPT (scale-sensitive BigDecimal equality) a column-coerced value must match a cast value.
        engine.execute("CREATE TABLE viaCol (n NUMBER(8,4))");
        engine.execute("CREATE TABLE viaCast (n NUMBER(8,4))");
        engine.execute("INSERT INTO viaCol VALUES (0), (1), (0.1)");
        engine.execute("INSERT INTO viaCast SELECT 0::NUMBER(8,4) UNION ALL "
            + "SELECT 1::NUMBER(8,4) UNION ALL SELECT 0.1::NUMBER(8,4)");
        assertEquals(0, engine.executeQuery(
            "SELECT n FROM viaCol EXCEPT SELECT n FROM viaCast").getRowCount());
    }

    @Test
    public void tooManyFractionalDigitsStillRoundToScale() {
        engine.execute("CREATE TABLE t (n NUMBER(8,2))");
        engine.execute("INSERT INTO t VALUES (1.239)");             // scale 3 → rounds HALF_UP to 1.24
        assertEquals("1.24", scalar("SELECT n FROM t").toString());
    }
}
