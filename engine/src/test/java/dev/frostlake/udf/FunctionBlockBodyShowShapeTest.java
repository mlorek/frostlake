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
 * A function whose body is a block that holds a SHOW is refused for the statement's kind, Unsupported statement
 * type for Snowscript UDF: SHOW — after what the SHOW's own shape rules out: a page size of 0 and a WITH
 * PRIVILEGES are told first (live-verified).
 */
public class FunctionBlockBodyShowShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void theShapeIsJudgedBeforeTheKind() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t1 (a INT)");
            assertAnswer(
                "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
                "CREATE OR REPLACE FUNCTION fk2() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TABLES; RETURN 1; "
                    + "END;'");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "CREATE OR REPLACE FUNCTION fk3() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TABLES LIMIT 0; "
                    + "RETURN 1; END;'");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "CREATE OR REPLACE FUNCTION fk4() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TABLES WITH "
                    + "PRIVILEGES USAGE; RETURN 1; END;'");
            assertAnswer(
                "REFUSED Unsupported statement type for Snowscript UDF: DESCRIBE",
                "CREATE OR REPLACE FUNCTION fk5() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN DESCRIBE TABLE t1; "
                    + "RETURN 1; END;'");
            assertAnswer(
                "REFUSED Unsupported statement type for Snowscript UDF: SHOW",
                "CREATE OR REPLACE FUNCTION fk6() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TABLES IN "
                    + "nosuch_zq7.a.b.c; RETURN 1; END;'");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fk2()");
            answer(
                "DROP FUNCTION IF EXISTS fk3()");
            answer(
                "DROP FUNCTION IF EXISTS fk4()");
            answer(
                "DROP FUNCTION IF EXISTS fk5()");
            answer(
                "DROP FUNCTION IF EXISTS fk6()");
        }
    }

    @Test
    public void aPageSizeOfZero() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t1 (a INT)");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "CREATE OR REPLACE FUNCTION fk() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TABLES LIMIT 0; "
                    + "RETURN 1; END;'");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fk()");
        }
    }
}
