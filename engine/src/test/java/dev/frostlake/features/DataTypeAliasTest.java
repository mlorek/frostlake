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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Snowflake's data-type vocabulary is mostly ALIASES onto a handful of real types, and a declared
 * alias has to land on the same type Snowflake gives it — the whole surface measured live
 * with {@code INFORMATION_SCHEMA.COLUMNS} and {@code SYSTEM$TYPEOF}.
 *
 * <p>Every integer spelling is {@code NUMBER(38,0)}; {@code NUMERIC} and {@code DEC} are plain
 * {@code NUMBER} synonyms; the whole floating family is {@code FLOAT}; every character spelling is
 * {@code TEXT}, defaulting to length 1 for the fixed-length three and to 16,777,216 for the varying
 * ones; and the run-together timestamp spellings are the zoned timestamp types.
 *
 * <p>The type is asserted with its PRECISION and SCALE, and separately through an arithmetic result
 * that exposes the scale: getting the metadata right but the storage wrong is exactly the shape the
 * {@code NUMERIC} defect had — the column was a VARCHAR, so {@code a + 1} came back {@code 2.5}
 * where Snowflake gives {@code 2.500}.
 */
public class DataTypeAliasTest extends BaseDatabaseTest {

    /**
     * A column's declared type as {@code DATA_TYPE/NUMERIC_PRECISION/NUMERIC_SCALE/CHARACTER_MAXIMUM_LENGTH},
     * with an absent number written {@code -}, so one assertion covers the whole reported shape.
     */
    private String declared(final String table, final String column) {
        final ResultSet rs = engine.executeQuery(
            "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE, CHARACTER_MAXIMUM_LENGTH "
            + "FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
        assertEquals(1, rs.getRowCount(), "one COLUMNS row for " + table + "." + column);
        final Row row = rs.getRows().get(0);
        return part(row.getValue(0)) + "/" + number(row.getValue(1))
            + "/" + number(row.getValue(2)) + "/" + number(row.getValue(3));
    }

    private String part(final Object value) {
        return value == null ? "-" : value.toString();
    }

    /** A reported precision/length, normalized: live returns these as decimals, the engine as ints. */
    private String number(final Object value) {
        if (value == null) {
            return "-";
        }
        return String.valueOf(((Number) value).longValue());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    // ── numeric aliases ───────────────────────────────────────────────────────

    @Test
    public void numberSynonymsKeepTheirPrecisionAndScale() {
        engine.execute("""
            CREATE TABLE t_num (
                c_number NUMBER, c_number_ps NUMBER(7,3),
                c_decimal DECIMAL, c_decimal_ps DECIMAL(7,3),
                c_numeric NUMERIC, c_numeric_ps NUMERIC(7,3),
                c_dec DEC, c_dec_ps DEC(7,3))""");
        assertEquals("NUMBER/38/0/-", declared("T_NUM", "C_NUMBER"));
        assertEquals("NUMBER/7/3/-", declared("T_NUM", "C_NUMBER_PS"));
        assertEquals("NUMBER/38/0/-", declared("T_NUM", "C_DECIMAL"));
        assertEquals("NUMBER/7/3/-", declared("T_NUM", "C_DECIMAL_PS"));
        assertEquals("NUMBER/38/0/-", declared("T_NUM", "C_NUMERIC"));
        assertEquals("NUMBER/7/3/-", declared("T_NUM", "C_NUMERIC_PS"));
        assertEquals("NUMBER/38/0/-", declared("T_NUM", "C_DEC"));
        assertEquals("NUMBER/7/3/-", declared("T_NUM", "C_DEC_PS"));
    }

    @Test
    public void everyIntegerAliasIsNumber38By0() {
        engine.execute("""
            CREATE TABLE t_int (
                c_int INT, c_integer INTEGER, c_bigint BIGINT,
                c_smallint SMALLINT, c_tinyint TINYINT, c_byteint BYTEINT)""");
        assertEquals("NUMBER/38/0/-", declared("T_INT", "C_INT"));
        assertEquals("NUMBER/38/0/-", declared("T_INT", "C_INTEGER"));
        assertEquals("NUMBER/38/0/-", declared("T_INT", "C_BIGINT"));
        // SMALLINT reported precision 5 and TINYINT/BYTEINT precision 3 before: Snowflake keeps no
        // narrower range for the small spellings.
        assertEquals("NUMBER/38/0/-", declared("T_INT", "C_SMALLINT"));
        assertEquals("NUMBER/38/0/-", declared("T_INT", "C_TINYINT"));
        assertEquals("NUMBER/38/0/-", declared("T_INT", "C_BYTEINT"));
    }

    @Test
    public void everyFloatingAliasIsFloatWithoutPrecision() {
        engine.execute("""
            CREATE TABLE t_flt (
                c_float FLOAT, c_float4 FLOAT4, c_float8 FLOAT8,
                c_double DOUBLE, c_double_p DOUBLE PRECISION, c_real REAL)""");
        assertEquals("FLOAT/-/-/-", declared("T_FLT", "C_FLOAT"));
        assertEquals("FLOAT/-/-/-", declared("T_FLT", "C_FLOAT4"));
        assertEquals("FLOAT/-/-/-", declared("T_FLT", "C_FLOAT8"));
        assertEquals("FLOAT/-/-/-", declared("T_FLT", "C_DOUBLE"));
        assertEquals("FLOAT/-/-/-", declared("T_FLT", "C_DOUBLE_P"));
        assertEquals("FLOAT/-/-/-", declared("T_FLT", "C_REAL"));
    }

    // ── character aliases ─────────────────────────────────────────────────────

    @Test
    public void varyingCharacterAliasesDefaultToTheFullLength() {
        engine.execute("""
            CREATE TABLE t_var (
                c_varchar VARCHAR, c_varchar_n VARCHAR(10),
                c_string STRING, c_text TEXT,
                c_nvarchar NVARCHAR, c_nvarchar_n NVARCHAR(10), c_nvarchar2_n NVARCHAR2(10),
                c_char_v CHAR VARYING, c_char_v_n CHAR VARYING(10),
                c_character_v_n CHARACTER VARYING(10), c_nchar_v_n NCHAR VARYING(10))""");
        assertEquals("TEXT/-/-/16777216", declared("T_VAR", "C_VARCHAR"));
        assertEquals("TEXT/-/-/10", declared("T_VAR", "C_VARCHAR_N"));
        assertEquals("TEXT/-/-/16777216", declared("T_VAR", "C_STRING"));
        assertEquals("TEXT/-/-/16777216", declared("T_VAR", "C_TEXT"));
        assertEquals("TEXT/-/-/16777216", declared("T_VAR", "C_NVARCHAR"));
        assertEquals("TEXT/-/-/10", declared("T_VAR", "C_NVARCHAR_N"));
        assertEquals("TEXT/-/-/10", declared("T_VAR", "C_NVARCHAR2_N"));
        // `X VARYING` is a VARCHAR whatever the head spelling is — CHAR VARYING was a CHAR(1) before.
        assertEquals("TEXT/-/-/16777216", declared("T_VAR", "C_CHAR_V"));
        assertEquals("TEXT/-/-/10", declared("T_VAR", "C_CHAR_V_N"));
        assertEquals("TEXT/-/-/10", declared("T_VAR", "C_CHARACTER_V_N"));
        assertEquals("TEXT/-/-/10", declared("T_VAR", "C_NCHAR_V_N"));
    }

    @Test
    public void fixedCharacterAliasesDefaultToLengthOne() {
        engine.execute("""
            CREATE TABLE t_fix (
                c_char CHAR, c_char_n CHAR(10),
                c_character CHARACTER, c_character_n CHARACTER(10),
                c_nchar NCHAR, c_nchar_n NCHAR(10))""");
        assertEquals("TEXT/-/-/1", declared("T_FIX", "C_CHAR"));
        assertEquals("TEXT/-/-/10", declared("T_FIX", "C_CHAR_N"));
        assertEquals("TEXT/-/-/1", declared("T_FIX", "C_CHARACTER"));
        assertEquals("TEXT/-/-/10", declared("T_FIX", "C_CHARACTER_N"));
        assertEquals("TEXT/-/-/1", declared("T_FIX", "C_NCHAR"));
        assertEquals("TEXT/-/-/10", declared("T_FIX", "C_NCHAR_N"));
    }

    // ── date/time and binary aliases ──────────────────────────────────────────

    @Test
    public void runTogetherTimestampSpellingsAreTheZonedTypes() {
        engine.execute("""
            CREATE TABLE t_ts (
                c_timestamp TIMESTAMP, c_datetime DATETIME,
                c_ts_ltz TIMESTAMP_LTZ, c_tsltz TIMESTAMPLTZ,
                c_ts_tz TIMESTAMP_TZ, c_tstz TIMESTAMPTZ,
                c_tsntz TIMESTAMPNTZ, c_ts_local TIMESTAMP WITH LOCAL TIME ZONE,
                c_varbinary VARBINARY)""");
        assertEquals("TIMESTAMP_NTZ/-/-/-", declared("T_TS", "C_TIMESTAMP"));
        assertEquals("TIMESTAMP_NTZ/-/-/-", declared("T_TS", "C_DATETIME"));
        assertEquals("TIMESTAMP_LTZ/-/-/-", declared("T_TS", "C_TS_LTZ"));
        assertEquals("TIMESTAMP_LTZ/-/-/-", declared("T_TS", "C_TSLTZ"));
        assertEquals("TIMESTAMP_TZ/-/-/-", declared("T_TS", "C_TS_TZ"));
        assertEquals("TIMESTAMP_TZ/-/-/-", declared("T_TS", "C_TSTZ"));
        assertEquals("TIMESTAMP_NTZ/-/-/-", declared("T_TS", "C_TSNTZ"));
        // The worded spelling carries a TIME token of its own and used to be classified as TIME.
        assertEquals("TIMESTAMP_LTZ/-/-/-", declared("T_TS", "C_TS_LOCAL"));
        assertEquals("BINARY/-/-/-", declared("T_TS", "C_VARBINARY"));
    }

    // ── the values, not just the metadata ─────────────────────────────────────

    @Test
    public void numberSynonymColumnsKeepTheDeclaredScaleInArithmetic() {
        engine.execute("CREATE TABLE t_scale (a NUMERIC(7,3), b NUMBER(7,3), c DECIMAL(7,3), d DEC(7,3))");
        engine.execute("INSERT INTO t_scale VALUES (1.5, 1.5, 1.5, 1.5)");
        // The NUMERIC column was a VARCHAR, so it answered 2.5 while the other three answered 2.500.
        assertEquals("2.500", scalar("SELECT a + 1 FROM t_scale"));
        assertEquals("2.500", scalar("SELECT b + 1 FROM t_scale"));
        assertEquals("2.500", scalar("SELECT c + 1 FROM t_scale"));
        assertEquals("2.500", scalar("SELECT d + 1 FROM t_scale"));
    }

    @Test
    public void numberSynonymCastsKeepTheDeclaredScale() {
        assertEquals("1.5000", scalar("SELECT CAST(1.5 AS NUMERIC(8,4))"));
        assertEquals("1.5000", scalar("SELECT 1.5::NUMERIC(8,4)"));
        assertEquals("1.5000", scalar("SELECT CAST(1.5 AS DEC(8,4))"));
        assertEquals("1.5000", scalar("SELECT 1.5::DEC(8,4)"));
    }

    @Test
    public void everyIntegerAliasCastRoundsHalfAwayFromZero() {
        // 2.7 -> 3, 2.5 -> 3, -2.5 -> -3 for all six spellings; the small three used to pass the
        // fractional value straight through.
        assertEquals("3", scalar("SELECT CAST(2.7 AS SMALLINT)"));
        assertEquals("3", scalar("SELECT CAST(2.5 AS TINYINT)"));
        assertEquals("-3", scalar("SELECT CAST(-2.5 AS BYTEINT)"));
        assertEquals("3", scalar("SELECT CAST(2.7 AS INT)"));
        assertEquals("3", scalar("SELECT CAST(2.7 AS INTEGER)"));
        assertEquals("3", scalar("SELECT CAST(2.7 AS BIGINT)"));
    }

    @Test
    public void characterSynonymCastsProduceTheSameValue() {
        assertEquals("ab", scalar("SELECT CAST('ab' AS NVARCHAR2)"));
        assertEquals("ab", scalar("SELECT CAST('ab' AS NVARCHAR)"));
        assertEquals("ab", scalar("SELECT CAST('ab' AS CHARACTER VARYING)"));
        assertEquals("ab", scalar("SELECT 'ab'::NCHAR VARYING"));
    }

    // ── the spellings are still ordinary names, and the unknown ones still fail ─

    @Test
    public void theAliasSpellingsStayUsableAsColumnNames() {
        // Live-verified: `CREATE TABLE kw (DEC INT, NVARCHAR2 INT, NUMERIC INT)` is accepted, so adding
        // the type spellings must not reserve the words.
        engine.execute("CREATE TABLE t_kw (dec INT, nvarchar2 INT, numeric INT, nchar INT)");
        engine.execute("INSERT INTO t_kw VALUES (1, 2, 3, 4)");
        assertEquals("1", scalar("SELECT dec FROM t_kw"));
        assertEquals("2", scalar("SELECT nvarchar2 FROM t_kw"));
        assertEquals("3", scalar("SELECT numeric FROM t_kw"));
        assertEquals("4", scalar("SELECT nchar FROM t_kw"));
    }

    @Test
    public void varyingOnlyFollowsTheFixedLengthSpellings() {
        // Live: `VARCHAR VARYING`, `NVARCHAR VARYING` and `NVARCHAR2 VARYING` are all syntax errors,
        // while CHAR / CHARACTER / NCHAR VARYING are accepted.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE t_bad_varying (a VARCHAR VARYING)");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE t_bad_varying2 (a NVARCHAR VARYING)");
            }
        });
    }

    @Test
    public void anUnknownTypeNameIsRejected() {
        // Live: "SQL compilation error:\nUnsupported data type 'FOOBAR'." A type name nobody recognizes
        // must never become a silent VARCHAR column.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE t_bad_type (a FOOBAR)");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST(1 AS MEDIUMINT)");
            }
        });
    }
}
