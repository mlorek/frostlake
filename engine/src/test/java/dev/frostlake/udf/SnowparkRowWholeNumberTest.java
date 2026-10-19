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
 * A whole number a Java handler collects through Snowpark: a NUMBER of no scale arrives as a Long, Long's own bounds
 * included, and one no Long holds fails the collect as the driver under Snowpark refuses it, a {@code SQLException}
 * of SQLSTATE 0A000 and code 200038, {@code Cannot convert value in the driver from type:FIXED(null,null) to
 * type:Long, value=12345678901234567890.} — over a literal, a cast, a table's NUMBER(38,0) and NUMBER(20,0) column,
 * and whichever row holds it. A NUMBER with a scale is a BigDecimal whatever its size, and a FLOAT a Double
 * (live-verified).
 */
public class SnowparkRowWholeNumberTest extends MeasuredCellsTestSupport {

    @Test
    public void aWholeNumberIsALongOrNothing() {
        engine.execute("CREATE OR REPLACE PROCEDURE jw(q VARCHAR, how VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
            + "String q, String how) {\n    Row[] rows;\n    try {\n      rows = s.sql(q).collect();\n    "
            + "} catch (Exception e) {\n      if (e instanceof java.sql.SQLException) {\n        "
            + "java.sql.SQLException failure = (java.sql.SQLException) e;\n        return \"C:SQLException "
            + "\" + failure.getSQLState() + \"/\" + failure.getErrorCode() + \": \" + "
            + "failure.getMessage();\n      }\n      return \"C:\" + e.getClass().getName() + \": \" + "
            + "e.getMessage();\n    }\n    Row r = rows[0];\n    try {\n      switch (how) {\n        case "
            + "\"get\": Object o = r.get(0); return \"O:\" + (o == null ? \"null\" : "
            + "o.getClass().getName() + \"=\" + o);\n        case \"getLong\": return \"L:\" + "
            + "r.getLong(0);\n        case \"getDecimal\": return \"N:\" + r.getDecimal(0);\n        case "
            + "\"row\": return \"R:\" + r;\n        case \"size\": return \"Z:\" + rows.length;\n        "
            + "default: return \"?\";\n      }\n    } catch (Exception e) {\n      String m = "
            + "String.valueOf(e.getMessage());\n      int p = m.indexOf(\" (\");\n      return \"E:\" + "
            + "e.getClass().getName() + \": \" + (p < 0 ? m : m.substring(0, p));\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE TABLE jwn (n NUMBER(38,0), m NUMBER(20,0), f NUMBER(38,2))");
        engine.execute("INSERT INTO jwn VALUES (12345678901234567890, 12345678901234567890, 12345678901234567890)");
        assertCells(new String[][] {
            {"CALL jw('SELECT 12345678901234567890', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
            {"CALL jw('SELECT -12345678901234567890', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=-12345678901234567890."},
        });
        assertShapedAnswer("O:java.lang.Long=<ID19>", "CALL jw('SELECT 9223372036854775807', 'get')");
        assertShapedAnswer("C:SQLException 0A000/200038: Cannot convert value in the driver from "
                + "type:FIXED(null,null) to type:Long, value=<ID19>.",
            "CALL jw('SELECT 9223372036854775808', 'get')");
        assertShapedAnswer("O:java.lang.Long=-<ID19>", "CALL jw('SELECT -9223372036854775808', 'get')");
        assertShapedAnswer("C:SQLException 0A000/200038: Cannot convert value in the driver from "
                + "type:FIXED(null,null) to type:Long, value=-<ID19>.",
            "CALL jw('SELECT -9223372036854775809', 'get')");
        assertShapedAnswer("O:java.lang.Long=<ID19>", "CALL jw('SELECT 1234567890123456789', 'get')");
        assertCells(new String[][] {
            {"CALL jw('SELECT 123456789012345678', 'get')", "O:java.lang.Long=123456789012345678"},
            {"CALL jw('SELECT 99999999999999999999999999999999999999', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=99999999999999999999999999999999999999."},
            {"CALL jw('SELECT 12345678901234567890::NUMBER(38,0)', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
            {"CALL jw('SELECT n FROM jwn', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
            {"CALL jw('SELECT m FROM jwn', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
            {"CALL jw('SELECT f FROM jwn', 'get')", "O:java.math.BigDecimal=12345678901234567890.00"},
            {"CALL jw('SELECT 12345678901234567890.5', 'get')", "O:java.math.BigDecimal=12345678901234567890.5"},
            {"CALL jw('SELECT 1, 12345678901234567890', 'get')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
            {"CALL jw('SELECT 12345678901234567890::FLOAT', 'get')", "O:java.lang.Double=1.2345678901234567E19"},
            {"CALL jw('SELECT 1 UNION ALL SELECT 12345678901234567890', 'size')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
            {"CALL jw('SELECT NULL::NUMBER(38,0)', 'get')", "O:null"},
        });
        assertShapedAnswer("L:<ID19>", "CALL jw('SELECT 9223372036854775807', 'getLong')");
        assertCells(new String[][] {
            {"CALL jw('SELECT 1234567890123456789', 'getDecimal')",
                "E:java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.math.BigDecimal"},
            {"CALL jw('SELECT 12345678901234567890::NUMBER(38,2)', 'get')",
                "O:java.math.BigDecimal=12345678901234567890.00"},
            {"CALL jw('SELECT 12345678901234567890', 'row')",
                "C:SQLException 0A000/200038: Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890."},
        });
        assertShapedAnswer("C:SQLException 0A000/200038: Cannot convert value in the driver from "
                + "type:FIXED(null,null) to type:Long, value=<ID19>.",
            "CALL jw('SELECT 9999999999999999999', 'get')");
        assertShapedAnswer("O:java.lang.Long=-<ID19>", "CALL jw('SELECT -1234567890123456789', 'get')");
        assertShapedAnswer("O:java.math.BigDecimal=<ID19>.000000", "CALL jw('SELECT n / 10 FROM jwn', 'get')");
        assertCells(new String[][] {
            {"CALL jw('SELECT 12345678901234567890::VARCHAR', 'get')", "O:java.lang.String=12345678901234567890"},
        });
        assertShapedAnswer("L:-<ID19>", "CALL jw('SELECT -9223372036854775808', 'getLong')");
    }
}
