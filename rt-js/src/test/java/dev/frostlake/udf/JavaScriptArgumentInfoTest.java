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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript table function's argumentInfo tells each argument's type and a constant's value as the handler
 * reads it, and a TIME column is refused at the call by its storage (all live-verified).
 */
public class JavaScriptArgumentInfoTest extends MeasuredCellsTestSupport {

    @Test
    public void constantsAndTimeColumns() {
        engine.execute("CREATE OR REPLACE FUNCTION ai1(a VARCHAR, b BOOLEAN, c FLOAT, d DATE) RETURNS TABLE (info "
            + "VARCHAR) LANGUAGE JAVASCRIPT AS $${ initialize: function (argumentInfo, context) { this.info = "
            + "JSON.stringify(argumentInfo); }, processRow: function (row, rowWriter, context) { "
            + "rowWriter.writeRow({INFO: this.info}); } }$$");
        engine.execute("CREATE OR REPLACE FUNCTION tb1(n FLOAT) RETURNS TABLE (t TIME, b BINARY) LANGUAGE JAVASCRIPT "
            + "AS $${ processRow: function (row, rowWriter, context) { rowWriter.writeRow({T: '10:11:12', B: "
            + "'ABCD'}); } }$$");
        engine.execute("CREATE OR REPLACE FUNCTION tb2(n FLOAT) RETURNS TABLE (t TIME, b BINARY) LANGUAGE JAVASCRIPT "
            + "AS $${ processRow: function (row, rowWriter, context) { rowWriter.writeRow({T: new Date(3600000), "
            + "B: new Uint8Array([1, 2])}); } }$$");
        answer("SELECT * FROM TABLE(ai1('x', TRUE, 2.5::FLOAT, '2024-01-02'::DATE))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function 'AI1': (NULL, NULL, NULL, NULL)",
            "SELECT * FROM TABLE(ai1(NULL, NULL, NULL, NULL))");
        answer("SELECT f.info FROM (SELECT 'y' AS s) t, TABLE(ai1(t.s, FALSE, 1::FLOAT, CURRENT_DATE())) f");
        assertAnswer("REFUSED Snowflake type TIME[SB8](0,9){nullable} is not supported in function TB1",
            "SELECT * FROM TABLE(tb1(1::FLOAT))");
        assertAnswer("REFUSED Snowflake type TIME[SB8](0,9){nullable} is not supported in function TB2",
            "SELECT * FROM TABLE(tb2(1::FLOAT))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function TB3",
            "SELECT * FROM TABLE(tb3('10:11:12'::TIME, TO_BINARY('ABCD', 'HEX')))");
    }

    @Test
    public void timeScales() {
        engine.execute("CREATE OR REPLACE FUNCTION tb4(n FLOAT) RETURNS TABLE (t TIME(3)) LANGUAGE JAVASCRIPT AS $${ "
            + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({T: '10:11:12'}); } }$$");
        engine.execute("CREATE OR REPLACE FUNCTION tb5(n FLOAT) RETURNS TABLE (t TIME(0)) LANGUAGE JAVASCRIPT AS $${ "
            + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({T: '10:11:12'}); } }$$");
        engine.execute("CREATE OR REPLACE FUNCTION tb6(n FLOAT) RETURNS TABLE (b BINARY) LANGUAGE JAVASCRIPT AS $${ "
            + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({B: 'ABCD'}); } }$$");
        engine.execute("CREATE OR REPLACE FUNCTION tb7(n FLOAT) RETURNS TABLE (v FLOAT, t TIME) LANGUAGE JAVASCRIPT "
            + "AS $${ processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: 1}); } }$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function AI2",
            "SELECT * FROM TABLE(ai2(PARSE_JSON('{\"k\":1}'), '2024-01-02 03:04:05'::TIMESTAMP_NTZ, 7, "
                + "ARRAY_CONSTRUCT(1, 2), OBJECT_CONSTRUCT('a', 1), '2024-01-02'::DATE))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function AI2",
            "SELECT * FROM TABLE(ai2(TO_VARIANT(3), CURRENT_TIMESTAMP()::TIMESTAMP_NTZ, 7.5, ARRAY_CONSTRUCT(), "
                + "OBJECT_CONSTRUCT(), '1969-12-31'::DATE))");
        assertAnswer("REFUSED Snowflake type TIME[SB4](0,3){nullable} is not supported in function TB4",
            "SELECT * FROM TABLE(tb4(1::FLOAT))");
        assertAnswer("REFUSED Snowflake type TIME[SB4]{nullable} is not supported in function TB5",
            "SELECT * FROM TABLE(tb5(1::FLOAT))");
        assertAnswer("REFUSED JavaScript execution error: Return value is not a BINARY",
            "SELECT * FROM TABLE(tb6(1::FLOAT))");
        assertAnswer("REFUSED Snowflake type TIME[SB8](0,9){nullable} is not supported in function TB7",
            "SELECT * FROM TABLE(tb7(1::FLOAT))");
    }

    @Test
    public void constantsOfEveryType() {
        engine.execute("CREATE OR REPLACE FUNCTION ai3(a VARIANT, b TIMESTAMP_NTZ, d ARRAY, e OBJECT, f DATE, g "
            + "VARCHAR, h TIMESTAMP_LTZ) RETURNS TABLE (info VARCHAR) LANGUAGE JAVASCRIPT AS $${ initialize: "
            + "function (argumentInfo, context) { this.info = JSON.stringify(argumentInfo).replace(/[-:]/g, "
            + "'_'); }, processRow: function (row, rowWriter, context) { rowWriter.writeRow({INFO: this.info}); "
            + "} }$$");
        engine.execute("CREATE OR REPLACE FUNCTION ai4(a BINARY) RETURNS TABLE (info VARCHAR) LANGUAGE JAVASCRIPT AS "
            + "$${ initialize: function (argumentInfo, context) { this.info = JSON.stringify(argumentInfo); }, "
            + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({INFO: this.info}); } }$$");
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            assertAnswer("{\"A\"_{\"constValue\"_{\"k\"_1},\"isNullable\"_true,\"isConst\"_true,\"type\"_\"variant\"},"
                + "\"B\"_{\"constValue\"_\"2024_01_02T11_04_05.123Z\",\"isNullable\"_true,\"isConst\"_true,"
                + "\"type\"_\"timestamp_ntz\"},\"D\"_{\"constValue\"_[1,\"a\"],\"isNullable\"_true,\"isConst\"_true,"
                + "\"type\"_\"array\"},\"E\"_{\"constValue\"_{\"a\"_1},\"isNullable\"_true,\"isConst\"_true,"
                + "\"type\"_\"object\"},\"F\"_{\"constValue\"_\"2024_01_02T08_00_00.000Z\",\"isNullable\"_true,"
                + "\"isConst\"_true,\"type\"_\"date\"},\"G\"_{\"constValue\"_\"q\",\"isNullable\"_true,"
                + "\"isConst\"_true,\"type\"_\"text\"},\"H\"_{\"constValue\"_\"2024_01_02T01_04_05.000Z\","
                + "\"isNullable\"_true,\"isConst\"_true,\"type\"_\"timestamp_ltz\"}}",
                "SELECT * FROM TABLE(ai3(PARSE_JSON('{\"k\":1}'), '2024-01-02 03:04:05.123'::TIMESTAMP_NTZ, "
                + "ARRAY_CONSTRUCT(1, 'a'), OBJECT_CONSTRUCT('a', 1), '2024-01-02'::DATE, 'q', '2024-01-02 03:04:05 "
                + "+02:00'::TIMESTAMP_LTZ))");
            assertAnswer("{\"A\"_{\"constValue\"_\"s\",\"isNullable\"_true,\"isConst\"_true,\"type\"_\"variant\"},"
                + "\"B\"_{\"constValue\"_\"1970_01_01T07_59_59.000Z\",\"isNullable\"_true,\"isConst\"_true,"
                + "\"type\"_\"timestamp_ntz\"},\"D\"_{\"constValue\"_[],\"isNullable\"_true,\"isConst\"_true,"
                + "\"type\"_\"array\"},\"E\"_{\"constValue\"_{},\"isNullable\"_true,\"isConst\"_true,"
                + "\"type\"_\"object\"},\"F\"_{\"constValue\"_\"1969_12_31T08_00_00.000Z\",\"isNullable\"_true,"
                + "\"isConst\"_true,\"type\"_\"date\"},\"G\"_{\"constValue\"_\"\",\"isNullable\"_true,"
                + "\"isConst\"_true,\"type\"_\"text\"},\"H\"_{\"constValue\"_\"1970_01_01T08_00_00.000Z\","
                + "\"isNullable\"_true,\"isConst\"_true,\"type\"_\"timestamp_ltz\"}}",
                "SELECT * FROM TABLE(ai3(TO_VARIANT('s'), '1969-12-31 23:59:59'::TIMESTAMP_NTZ, ARRAY_CONSTRUCT(), "
                + "OBJECT_CONSTRUCT(), '1969-12-31'::DATE, '', '1970-01-01 00:00:00'::TIMESTAMP_LTZ))");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
        assertAnswer("{\"A\":{\"constValue\":{\"0\":171,\"1\":205},\"isNullable\":true,\"isConst\":true,"
                + "\"type\":\"binary\"}}",
            "SELECT * FROM TABLE(ai4(TO_BINARY('ABCD', 'HEX')))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 67 | Invalid argument types for "
                + "function 'AI3': (NULL, NULL, NULL, NULL, DATE, VARCHAR(1), NULL)",
            "SELECT f.info FROM (SELECT 'y' AS s, CURRENT_DATE() AS d) t, TABLE(ai3(NULL, NULL, NULL, NULL, t.d, t.s,"
                + " NULL)) f");
    }
}
