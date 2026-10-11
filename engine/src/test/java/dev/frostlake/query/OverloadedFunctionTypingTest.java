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

import org.junit.jupiter.api.Test;

/**
 * A call of a SQL function with several signatures of one argument count is typed by the body of the
 * signature the call runs, chosen by its arguments' types as the call chooses it: a VARCHAR(3) for a body
 * 'num' whatever RETURNS says, a NUMBER the body's arithmetic gives over the argument's own type - in the
 * result's columns, in SYSTEM$TYPEOF, in a table created from it and in a view. An untyped NULL goes to the
 * number, a date and a BOOLEAN to the text. Every answer is live-verified.
 */
public class OverloadedFunctionTypingTest extends MeasuredCellsTestSupport {

    @Test
    public void theChosenSignaturesBodyTypesTheCall() {
        engine.execute(
            "CREATE OR REPLACE FUNCTION fo(n NUMBER) RETURNS VARCHAR AS '''num'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fo(v VARCHAR) RETURNS VARCHAR AS '''var'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(n NUMBER) RETURNS VARCHAR AS '''num'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fw(n NUMBER) RETURNS NUMBER AS 'n + 0.5'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fw(v VARCHAR) RETURNS VARCHAR AS 'v || ''!'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fa(a NUMBER) RETURNS VARCHAR AS '''one'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fa(a NUMBER, b NUMBER) RETURNS VARCHAR AS '''twoo'''");
        engine.execute(
            "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR(10))");
        engine.execute(
            "INSERT INTO tq VALUES (1.5, 'ab')");
        try {
            assertShape(
                "G1:VARCHAR(3), G2:VARCHAR(3)",
                "SELECT fo(1) AS g1, fo('a') AS g2");
            assertAnswer(
                "num|var",
                "SELECT fo(1) AS g1, fo('a') AS g2");
            assertShape(
                "F6:VARCHAR(3), F7:VARCHAR(3), F8:VARCHAR(3), F9:VARCHAR(3)",
                "SELECT fo(NULL::VARCHAR(10)) AS f6, fo(NULL::DATE) AS f7, fo(NULL) AS f8, fo(1) AS f9");
            assertAnswer(
                "var|var|num|num",
                "SELECT fo(NULL::VARCHAR(10)) AS f6, fo(NULL::DATE) AS f7, fo(NULL) AS f8, fo(1) AS f9");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(fo(1)), SYSTEM$TYPEOF(fo('a'))");
            assertAnswer(
                "Table FOC successfully created.",
                "CREATE OR REPLACE TABLE foc AS SELECT fo(1) AS c1, fo('a') AS c2");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(f1(1))");
            assertShape(
                "F1(1):VARCHAR(3)",
                "SELECT f1(1)");
            assertAnswer(
                "NUMBER(4,1)[SB1]|VARCHAR(2)[LOB]",
                "SELECT SYSTEM$TYPEOF(fw(1)), SYSTEM$TYPEOF(fw('a'))");
            assertShape(
                "A:NUMBER(4,1), B:VARCHAR(2)",
                "SELECT fw(1) AS a, fw('a') AS b");
            assertAnswer(
                "1.5|a!",
                "SELECT fw(1), fw('a')");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(4)[LOB]",
                "SELECT SYSTEM$TYPEOF(fa(1)), SYSTEM$TYPEOF(fa(1, 2))");
            assertShape(
                "A:VARCHAR(3), B:VARCHAR(4)",
                "SELECT fa(1) AS a, fa(1, 2) AS b");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(3)[LOB]|NUMBER(11,2)[SB2]|VARCHAR(11)[LOB]",
                "SELECT SYSTEM$TYPEOF(fo(n)), SYSTEM$TYPEOF(fo(s)), SYSTEM$TYPEOF(fw(n)), SYSTEM$TYPEOF(fw(s)) "
                    + "FROM tq");
            assertShape(
                "A:VARCHAR(3), B:VARCHAR(3), C:NUMBER(11,2), D:VARCHAR(11)",
                "SELECT fo(n) AS a, fo(s) AS b, fw(n) AS c, fw(s) AS d FROM tq");
            assertAnswer(
                "VARCHAR(3)[LOB]|VARCHAR(3)[LOB]|VARCHAR(3)[LOB]|VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(fo(1.5)), SYSTEM$TYPEOF(fo(TRUE)), SYSTEM$TYPEOF(fo(1::FLOAT)), "
                    + "SYSTEM$TYPEOF(fo(NULL))");
            assertAnswer(
                "NUMBER(38,1)[SB1]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(fw(NULL)), SYSTEM$TYPEOF(fw(NULL::VARCHAR)), "
                    + "SYSTEM$TYPEOF(fw('2024-01-01'::DATE))");
            assertAnswer(
                "VARCHAR(4)[LOB]|VARCHAR(9)[LOB]|VARCHAR(5)[LOB]",
                "SELECT SYSTEM$TYPEOF(fo(1) || 'x'), SYSTEM$TYPEOF(UPPER(fo('a'))), SYSTEM$TYPEOF(COALESCE(fo(1), "
                    + "'zzzzz'))");
            assertAnswer(
                "View VFO successfully created.",
                "CREATE OR REPLACE VIEW vfo AS SELECT fo(1) AS c1, fw(1) AS c2");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
