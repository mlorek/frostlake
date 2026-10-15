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
 * A JavaScript procedure's answer and the scan of its CALL. An answer of RETURNS OBJECT that is no object - a
 * Uint8Array's hex, a String object's text, a Date's timestamp - is handed out by the CALL, and a statement
 * that reads it from the scan is refused: Failed to cast variant value "01" to OBJECT. A value that holds
 * itself is refused as the answer of a VARIANT, an ARRAY or an OBJECT - Object contains cyclic references - and
 * a symbol or a fraction of a millisecond answers a DATE and a TIMESTAMP as the account answers it
 * (live-verified).
 */
public class JavaScriptAnswerScanTest extends MeasuredCellsTestSupport {

    @Test
    public void anObjectAnswerThatIsNoObject() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function PO1 successfully created.",
                "CREATE OR REPLACE PROCEDURE po1() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1]);$$");
            assertAnswer(
                "01",
                "CALL po1()");
            assertAnswer(
                "REFUSED Failed to cast variant value \"01\" to OBJECT",
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL po1()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL po1()");
            assertAnswer(
                "01",
                "CALL po1()");
            assertAnswer(
                "REFUSED Failed to cast variant value \"01\" to OBJECT",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL po1()");
            assertAnswer(
                "OBJECT[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PO2 successfully created.",
                "CREATE OR REPLACE PROCEDURE po2() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new "
                    + "String('ab');$$");
            assertAnswer(
                "ab",
                "CALL po2()");
            assertAnswer(
                "REFUSED Failed to cast variant value \"ab\" to OBJECT",
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "ab",
                "CALL po2()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "ab",
                "CALL po2()");
            assertAnswer(
                "ab",
                "CALL po2()");
            assertAnswer(
                "REFUSED Failed to cast variant value \"ab\" to OBJECT",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "ab",
                "CALL po2()");
            assertAnswer(
                "OBJECT[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PO3 successfully created.",
                "CREATE OR REPLACE PROCEDURE po3() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return new Date(0);$$");
            answer(
                "CALL po3()");
            answer(
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            answer(
                "CALL po3()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            answer(
                "CALL po3()");
            answer(
                "CALL po3()");
            answer(
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            answer(
                "CALL po3()");
            assertAnswer(
                "OBJECT[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PO4 successfully created.",
                "CREATE OR REPLACE PROCEDURE po4() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$return {a: 1};$$");
            assertAnswer(
                "{\"a\":1}",
                "CALL po4()");
            assertAnswer(
                "OBJECT|{\"a\":1}",
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "{\"a\":1}",
                "CALL po4()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "{\"a\":1}",
                "CALL po4()");
            assertAnswer(
                "{\"a\":1}",
                "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "{\"a\":1}",
                "CALL po4()");
            assertAnswer(
                "{\"a\":1}",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "{\"a\":1}",
                "CALL po4()");
            assertAnswer(
                "OBJECT[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PA1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa1() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return 5;$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa1()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa1()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa1()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa1()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa1()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PA2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa2() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$return 'x';$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa2()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa2()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa2()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa2()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED JavaScript execution error: Return value is not an ARRAY",
                "CALL pa2()");
            assertShapedAnswer(
                "REFUSED Query <QID> has no result because it failed",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Function PV1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pv1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$return new "
                    + "Uint8Array([1]);$$");
            assertAnswer(
                "01",
                "CALL pv1()");
            assertAnswer(
                "VARCHAR|\"01\"",
                "SELECT TYPEOF($1), TO_JSON($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL pv1()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL pv1()");
            assertAnswer(
                "01",
                "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL pv1()");
            assertAnswer(
                "01",
                "SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "01",
                "CALL pv1()");
            assertAnswer(
                "VARIANT[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS po1()");
            answer(
                "DROP PROCEDURE IF EXISTS po2()");
            answer(
                "DROP PROCEDURE IF EXISTS po3()");
            answer(
                "DROP PROCEDURE IF EXISTS po4()");
            answer(
                "DROP PROCEDURE IF EXISTS pa1()");
            answer(
                "DROP PROCEDURE IF EXISTS pa2()");
            answer(
                "DROP PROCEDURE IF EXISTS pv1()");
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void cyclesSymbolsAndFractions() {
        try {
            answer(
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function PC1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pc1() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$var a = {}; a.self = "
                    + "a; return a;$$");
            assertAnswer(
                "REFUSED Object contains cyclic references",
                "CALL pc1()");
            assertAnswer(
                "Function PC2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pc2() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$var a = []; a.push(a); "
                    + "return a;$$");
            assertAnswer(
                "REFUSED Object contains cyclic references",
                "CALL pc2()");
            assertAnswer(
                "Function PC3 successfully created.",
                "CREATE OR REPLACE PROCEDURE pc3() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$var a = {}; a.self = "
                    + "a; return a;$$");
            assertAnswer(
                "REFUSED Object contains cyclic references",
                "CALL pc3()");
            assertAnswer(
                "Function PD1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pd1() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "CALL pd1()");
            assertAnswer(
                "Function PD2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pd2() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return "
                    + "Symbol('x');$$");
            assertAnswer(
                "REFUSED JavaScript execution error: Incorrect Timestamp returned",
                "CALL pd2()");
            assertAnswer(
                "Function PD3 successfully created.",
                "CREATE OR REPLACE PROCEDURE pd3() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return -0.5;$$");
            assertAnswer(
                "1970-01-01",
                "CALL pd3()");
            assertAnswer(
                "Function PD4 successfully created.",
                "CREATE OR REPLACE PROCEDURE pd4() RETURNS DATE LANGUAGE JAVASCRIPT AS $$return -86400000.5;$$");
            assertAnswer(
                "1969-12-31",
                "CALL pd4()");
            assertAnswer(
                "Function PD5 successfully created.",
                "CREATE OR REPLACE PROCEDURE pd5() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$return -0.5;$$");
            assertAnswer(
                "1970-01-01T00:00",
                "CALL pd5()");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pc1()");
            answer(
                "DROP PROCEDURE IF EXISTS pc2()");
            answer(
                "DROP PROCEDURE IF EXISTS pc3()");
            answer(
                "DROP PROCEDURE IF EXISTS pd1()");
            answer(
                "DROP PROCEDURE IF EXISTS pd2()");
            answer(
                "DROP PROCEDURE IF EXISTS pd3()");
            answer(
                "DROP PROCEDURE IF EXISTS pd4()");
            answer(
                "DROP PROCEDURE IF EXISTS pd5()");
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
