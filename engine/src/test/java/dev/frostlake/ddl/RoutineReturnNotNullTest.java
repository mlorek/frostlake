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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A routine's RETURNS type written NOT NULL is told by DESCRIBE FUNCTION's and DESCRIBE PROCEDURE's returns row,
 * {@code VARCHAR NOT NULL}, a sized type and a number alike (live-verified).
 */
public class RoutineReturnNotNullTest extends MeasuredCellsTestSupport {

    @Test
    public void aNotNullReturnIsDescribed() {
        engine.execute("CREATE OR REPLACE FUNCTION rv_g() RETURNS VARCHAR NOT NULL AS '''a'''");
        engine.execute("CREATE OR REPLACE FUNCTION rv_n() RETURNS NUMBER(5,2) NOT NULL AS '1.5'");
        engine.execute("CREATE OR REPLACE FUNCTION rv_s() RETURNS VARCHAR(10) NOT NULL AS '''a'''");
        engine.execute("CREATE OR REPLACE FUNCTION rv_p() RETURNS VARCHAR AS '''a'''");
        engine.execute("CREATE OR REPLACE PROCEDURE rp_g() RETURNS VARCHAR NOT NULL LANGUAGE SQL AS 'BEGIN RETURN "
            + "''a''; END'");
        engine.execute("DESCRIBE FUNCTION rv_g()");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "signature|() ;; returns|VARCHAR NOT NULL ;; language|SQL ;; body|'a'"},
        });
        engine.execute("DESCRIBE FUNCTION rv_n()");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "signature|() ;; returns|NUMBER(5,2) NOT NULL ;; language|SQL ;; body|1.5"},
        });
        engine.execute("DESCRIBE FUNCTION rv_s()");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "signature|() ;; returns|VARCHAR(10) NOT NULL ;; language|SQL ;; body|'a'"},
        });
        engine.execute("DESCRIBE FUNCTION rv_p()");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "signature|() ;; returns|VARCHAR ;; language|SQL ;; body|'a'"},
        });
        engine.execute("DESCRIBE PROCEDURE rp_g()");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "signature|() ;; returns|VARCHAR NOT NULL ;; language|SQL ;; execute as|OWNER ;; body|BEGIN RETURN "
                + "'a'; END"},
        });
    }
}
