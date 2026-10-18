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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The scan of a JavaScript procedure's OBJECT answer that is no object — a Uint8Array, a String object: a plain
 * {@code SELECT * FROM TABLE(RESULT_SCAN(…))} hands the answer out as the CALL did, where an alias's star, a star
 * beside another item, a subquery's star, an ORDER BY, a WHERE, a named column and a DISTINCT are refused, {@code
 * Failed to cast variant value "01" to OBJECT}; an object answer reads as itself (live-verified).
 */
public class JavaScriptScanStarTest extends MeasuredCellsTestSupport {

    @Test
    public void aPlainStarHandsTheAnswerOut() {
        engine.execute("CREATE OR REPLACE PROCEDURE po1() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
            + "Uint8Array([1]);$$");
        engine.execute("CREATE OR REPLACE PROCEDURE po2() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
            + "String('ab');$$");
        engine.execute("CREATE OR REPLACE PROCEDURE po4() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return {a: 1};$$");
        assertCells(new String[][] {
            {"CALL po1()", "01"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "01"},
            {"CALL po1()", "01"},
            {"SELECT r.* FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po1()", "01"},
            {"SELECT *, 1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po1()", "01"},
            {"SELECT * FROM (SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())))",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po1()", "01"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po1()", "01"},
            {"SELECT \"PO1\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po1()", "01"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE TRUE",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po1()", "01"},
            {"SELECT DISTINCT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED Failed to cast variant value \"01\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "ab"},
            {"CALL po2()", "ab"},
            {"SELECT r.* FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT *, 1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT * FROM (SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())))",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT \"PO2\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE TRUE",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po2()", "ab"},
            {"SELECT DISTINCT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED Failed to cast variant value \"ab\" to OBJECT"},
            {"CALL po4()", "{\"a\":1}"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "{\"a\":1}"},
        });
    }
}
