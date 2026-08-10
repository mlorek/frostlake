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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake's compile-time INSERT ... SELECT type matching, live-verified: an incompatible
 * source type is refused before any row is written — even under WHERE FALSE — as
 * {@code Expression type does not match column data type, expecting <target> but got <source>
 * for column <name>} with both types spelled parameterized, while VARCHAR still reaches
 * numbers/booleans/temporals (row-time parses) and VARIANT reaches every scalar (row-time
 * casts). A TIME source against a TIMESTAMP column has its own sentence.
 */
public class InsertTypeMatchingTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(InsertTypeMatchingTest.class);

    @BeforeEach
    public void createTargets() {
        engine.execute("CREATE TABLE t_num (c NUMBER(10,2))");
        engine.execute("CREATE TABLE t_vc (c VARCHAR(10))");
        engine.execute("CREATE TABLE t_dt (c DATE)");
        engine.execute("CREATE TABLE t_ts (c TIMESTAMP_NTZ)");
        engine.execute("CREATE TABLE t_var (c VARIANT)");
        engine.execute("CREATE TABLE t_arr (c ARRAY)");
        engine.execute("CREATE TABLE t_obj (c OBJECT)");
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    @Test
    public void testVarcharLiteralRefusedIntoVariant() {
        final RuntimeException e = refusal("INSERT INTO t_var SELECT 'abc'");
        assertTrue(e.getMessage().contains("Expression type does not match column data type,"
            + " expecting VARIANT but got VARCHAR(3) for column C"), e.getMessage());
    }

    @Test
    public void testRefusalFiresBeforeAnyRowExists() {
        final RuntimeException e = refusal("INSERT INTO t_var SELECT 'abc' WHERE FALSE");
        assertTrue(e.getMessage().contains(
            "expecting VARIANT but got VARCHAR(3) for column C"), e.getMessage());
    }

    @Test
    public void testNumberLiteralRefusedIntoDate() {
        final RuntimeException e = refusal("INSERT INTO t_dt SELECT 42");
        assertTrue(e.getMessage().contains(
            "expecting DATE but got NUMBER(2,0) for column C"), e.getMessage());
    }

    @Test
    public void testBooleanRefusedIntoNumber() {
        final RuntimeException e = refusal("INSERT INTO t_num SELECT TRUE");
        assertTrue(e.getMessage().contains(
            "expecting NUMBER(10,2) but got BOOLEAN for column C"), e.getMessage());
    }

    @Test
    public void testDateRefusedIntoNumber() {
        final RuntimeException e = refusal("INSERT INTO t_num SELECT CURRENT_DATE()");
        assertTrue(e.getMessage().contains(
            "expecting NUMBER(10,2) but got DATE for column C"), e.getMessage());
    }

    @Test
    public void testContainersNeverCrossFamilies() {
        final RuntimeException toObj = refusal("INSERT INTO t_obj SELECT ARRAY_CONSTRUCT(1)");
        assertTrue(toObj.getMessage().contains(
            "expecting OBJECT but got ARRAY for column C"), toObj.getMessage());

        final RuntimeException toArr = refusal("INSERT INTO t_arr SELECT OBJECT_CONSTRUCT('a', 1)");
        assertTrue(toArr.getMessage().contains(
            "expecting ARRAY but got OBJECT for column C"), toArr.getMessage());
    }

    @Test
    public void testTimeIntoTimestampHasItsOwnSentence() {
        final RuntimeException e = refusal("INSERT INTO t_ts SELECT TO_TIME('12:00:00')");
        assertTrue(e.getMessage().contains(
            "incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"), e.getMessage());
    }

    @Test
    public void testImplicitConversionsStillReachRowTime() {
        engine.execute("INSERT INTO t_num SELECT '42'");
        engine.execute("INSERT INTO t_var SELECT 42");
        engine.execute("INSERT INTO t_vc SELECT 42");
        engine.execute("INSERT INTO t_ts SELECT CURRENT_DATE()");
        engine.execute("INSERT INTO t_dt SELECT TO_TIMESTAMP_NTZ('2020-01-01 00:00:00')");
        engine.execute("INSERT INTO t_vc SELECT PARSE_JSON('\"s\"')");

        assertEquals(1L, engine.executeQuery("SELECT COUNT(*) FROM t_num")
            .getRows().get(0).getValue(0));

        logger.info("Row-time conversions untouched by the compile check");
    }
}
