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
 * A text that begins with a control character reaches a JavaScript handler, and comes back from one, as the
 * text it is - inside a VARIANT, an ARRAY or an OBJECT argument, as a VARCHAR argument, as a member of a
 * VARIANT answer and as a VARCHAR answer, for a function and a procedure alike. The engine hands
 * semi-structured values to a handler as JSON in which it marks what JSON cannot spell, and the marks begin
 * with such characters; a text that happens to look like one is never read as one. Every answer is
 * live-verified.
 */
public class JavaScriptControlCharacterTextTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentThatLooksLikeAMark() {
        assertAnswer(
            "Function CV successfully created.",
            "CREATE OR REPLACE FUNCTION cv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var c = function "
                + "(x) { if (typeof x !== 'string') { return typeof x; } var o = []; for (var i = 0; i < x.length; "
                + "i++) { o.push(x.charCodeAt(i)); } return 's' + o.join('.'); }; return Array.isArray(V) ? "
                + "V.map(c).join('|') : c(V); $$");
        assertAnswer(
            "s2.70.58.49",
            "SELECT cv(TO_VARIANT(CHR(2) || 'F:1'))");
        assertAnswer(
            "s2.74.58.49",
            "SELECT cv(TO_VARIANT(CHR(2) || 'J:1'))");
        assertAnswer(
            "s2.68.58.53|s2.66.58.70.70|s2.83.58.120|s2.77.58.91.49.93|s2.84.58.49|s2",
            "SELECT cv(ARRAY_CONSTRUCT(CHR(2) || 'D:5', CHR(2) || 'B:FF', CHR(2) || 'S:x', CHR(2) || 'M:[1]', "
                + "CHR(2) || 'T:1', CHR(2)))");
        assertAnswer(
            "s2.70.58.78.97.78",
            "SELECT cv(OBJECT_CONSTRUCT('k', CHR(2) || 'F:NaN'):k)");
        assertAnswer(
            "s1.82.58.120",
            "SELECT cv(TO_VARIANT(CHR(1) || 'R:x'))");
        assertAnswer(
            "Function FS successfully created.",
            "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var c = function "
                + "(x) { if (typeof x !== 'string') { return typeof x; } var o = []; for (var i = 0; i < x.length; "
                + "i++) { o.push(x.charCodeAt(i)); } return 's' + o.join('.'); }; return c(X); $$");
        assertAnswer(
            "s2.74.58.49",
            "SELECT fs(CHR(2) || 'J:1')");
        assertAnswer(
            "s2.77.58.91.49.93",
            "SELECT fs(CHR(2) || 'M:[1]')");
        assertAnswer(
            "Function PCV successfully created.",
            "CREATE OR REPLACE PROCEDURE pcv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var c = "
                + "function (x) { if (typeof x !== 'string') { return typeof x; } var o = []; for (var i = 0; i < "
                + "x.length; i++) { o.push(x.charCodeAt(i)); } return 's' + o.join('.'); }; return Array.isArray(V) "
                + "? V.map(c).join('|') : c(V); $$");
        assertAnswer(
            "s2.70.58.49",
            "CALL pcv(TO_VARIANT(CHR(2) || 'F:1'))");
        assertAnswer(
            "s2.68.58.53|s2.66.58.70.70",
            "CALL pcv(ARRAY_CONSTRUCT(CHR(2) || 'D:5', CHR(2) || 'B:FF'))");
    }

    @Test
    public void anAnswerThatLooksLikeAMark() {
        assertAnswer(
            "Function RV successfully created.",
            "CREATE OR REPLACE FUNCTION rv(v VARIANT) RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return V; $$");
        assertAnswer(
            "true|VARCHAR",
            "SELECT rv(TO_VARIANT(CHR(2) || 'F:1')) = TO_VARIANT(CHR(2) || 'F:1'), TYPEOF(rv(TO_VARIANT(CHR(2) || "
                + "'F:1')))");
        assertAnswer(
            "true",
            "SELECT rv(ARRAY_CONSTRUCT(CHR(2) || 'D:5', CHR(2) || 'B:FF', CHR(2) || 'S:x')) = "
                + "ARRAY_CONSTRUCT(CHR(2) || 'D:5', CHR(2) || 'B:FF', CHR(2) || 'S:x')");
        assertAnswer(
            "VARCHAR|4",
            "SELECT TYPEOF(rv(ARRAY_CONSTRUCT(CHR(2) || 'D:5'))[0]), LENGTH(rv(ARRAY_CONSTRUCT(CHR(2) || "
                + "'D:5'))[0]::VARCHAR)");
        assertAnswer(
            "Function PRV successfully created.",
            "CREATE OR REPLACE PROCEDURE prv() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return ['\\u0002D:5', "
                + "'\\u0002S:x', '\\u0001R:y']; $$");
        assertAnswer(
            "[\"\\u0002D:5\",\"\\u0002S:x\",\"\\u0001R:y\"]",
            "CALL prv()");
        assertAnswer(
            "VARCHAR|4|4|4",
            "SELECT TYPEOF($1[0]), LENGTH($1[0]::VARCHAR), LENGTH($1[1]::VARCHAR), LENGTH($1[2]::VARCHAR) FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function RR successfully created.",
            "CREATE OR REPLACE FUNCTION rr() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return '\\u0001R:not a "
                + "refusal'; $$");
        assertAnswer(
            "16",
            "SELECT LENGTH(rr())");
        assertAnswer(
            "Function RR2 successfully created.",
            "CREATE OR REPLACE FUNCTION rr2() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return '\\u0001R:not a "
                + "refusal'; $$");
        assertAnswer(
            "16|VARCHAR",
            "SELECT LENGTH(rr2()::VARCHAR), TYPEOF(rr2())");
    }
}
