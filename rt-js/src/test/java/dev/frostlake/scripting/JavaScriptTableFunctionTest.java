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
 * A table function written in JavaScript. Its body is an object: {@code processRow(row, rowWriter, context)}
 * reads a row, whose members are the parameters by their names, and writes rows with
 * {@code rowWriter.writeRow}; {@code initialize} is called before the first row and {@code finalize} after
 * the last. A written row's columns are the object's members of the declared columns' upper-case names, each
 * converted as a function's answer of that type is; a member the object lacks, and an object that is none,
 * are NULLs. A body with no {@code processRow} is refused when the function is called, as is a declared
 * column of an exact NUMBER type. Called OVER a partition, the object reads the partition's rows in the
 * order asked for. Every answer is live-verified.
 */
public class JavaScriptTableFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlerObjectWritesRows() {
        answer(
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        answer(
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10)");
        assertAnswer(
            "Function JST successfully created.",
            "CREATE OR REPLACE FUNCTION jst(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: row.N}); "
                + "rowWriter.writeRow({V: row.N + 1}); } } $$");
        assertAnswer(
            "5.0 ;; 6.0",
            "SELECT * FROM TABLE(jst(5::FLOAT)) ORDER BY 1");
        shape(
            "SELECT * FROM TABLE(jst(5::FLOAT))");
        assertAnswer(
            "1|1.0 ;; 1|2.0 ;; 2|2.0 ;; 2|3.0 ;; 10|10.0 ;; 10|11.0",
            "SELECT ev.v, f.v FROM ev, TABLE(jst(ev.v::FLOAT)) f ORDER BY 1, 2");
        assertAnswer(
            "Function JSA successfully created.",
            "CREATE OR REPLACE FUNCTION jsa(S VARCHAR, N FLOAT) RETURNS TABLE (name VARCHAR, total FLOAT) "
                + "LANGUAGE JAVASCRIPT AS $$ { initialize: function (argumentInfo, context) { this.sum = 0; "
                + "this.rows = 0; }, processRow: function (row, rowWriter, context) { this.sum += row.N; this.rows "
                + "+= 1; rowWriter.writeRow({NAME: row.S, TOTAL: row.N}); }, finalize: function (rowWriter, context) "
                + "{ rowWriter.writeRow({NAME: 'end' + this.rows, TOTAL: this.sum}); } } $$");
        assertAnswer(
            "end1|5.0 ;; x|5.0",
            "SELECT * FROM TABLE(jsa('x', 5::FLOAT)) ORDER BY 1");
        assertAnswer(
            "Function JSQ successfully created.",
            "CREATE OR REPLACE FUNCTION jsq(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$SELECT 42$$");
        answer(
            "SELECT * FROM TABLE(jsq(1::FLOAT))");
        assertAnswer(
            "Function JSLOW successfully created.",
            "CREATE OR REPLACE FUNCTION jslow(N FLOAT) RETURNS TABLE (v FLOAT, w VARCHAR) LANGUAGE JAVASCRIPT AS "
                + "$$ { processRow: function (row, rowWriter, context) { rowWriter.writeRow({v: row.N}); "
                + "rowWriter.writeRow({V: row.N, EXTRA: 1}); rowWriter.writeRow({W: 'only w'}); } } $$");
        assertAnswer(
            "5.0|null ;; null|only w ;; null|null",
            "SELECT * FROM TABLE(jslow(5::FLOAT)) ORDER BY 1, 2");
        assertAnswer(
            "Function JSEX successfully created.",
            "CREATE OR REPLACE FUNCTION jsex(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { if (row.N > 1) { throw new Error('too big ' + "
                + "row.N); } rowWriter.writeRow({V: row.N}); } } $$");
        assertAnswer(
            "1.0",
            "SELECT * FROM TABLE(jsex(1::FLOAT))");
        answer(
            "SELECT * FROM TABLE(jsex(5::FLOAT))");
        assertAnswer(
            "Function JSNOP successfully created.",
            "CREATE OR REPLACE FUNCTION jsnop(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "finalize: function (rowWriter, context) { rowWriter.writeRow({V: 7}); } } $$");
        assertAnswer(
            "REFUSED User Defined Table Function object is missing property processRow",
            "SELECT * FROM TABLE(jsnop(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JST.",
            "SELECT jst(5::FLOAT)");
    }

    @Test
    public void writtenValuesAndWhatACallRefuses() {
        answer(
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        answer(
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10), (NULL, 5)");
        assertAnswer(
            "Function SU1 successfully created.",
            "CREATE OR REPLACE FUNCTION su1(N FLOAT, S VARCHAR) RETURNS TABLE (v FLOAT, t VARCHAR, o VARIANT, d "
                + "DATE, b BOOLEAN) LANGUAGE JAVASCRIPT AS $$ { processRow: function (row, rowWriter, context) { "
                + "rowWriter.writeRow({V: \"7\", T: 12, O: {a: [1, row.S]}, D: new Date(86400000), B: 1}); "
                + "rowWriter.writeRow({V: null, T: undefined, O: null, D: null}); rowWriter.writeRow({}); } } $$");
        shape(
            "SELECT * FROM TABLE(su1(1::FLOAT, 'x'))");
        assertAnswer(
            "Function SU2 successfully created.",
            "CREATE OR REPLACE FUNCTION su2(N FLOAT) RETURNS TABLE (info VARCHAR) LANGUAGE JAVASCRIPT AS $$ { "
                + "initialize: function (argumentInfo, context) { this.info = JSON.stringify(argumentInfo) + \"|\" + "
                + "JSON.stringify(context) + \"|\" + typeof context; }, processRow: function (row, rowWriter, "
                + "context) { rowWriter.writeRow({INFO: this.info + \"|\" + JSON.stringify(row) + \"|\" + "
                + "Object.keys(rowWriter).join(\",\") + \"|\" + typeof rowWriter.writeRow}); } } $$");
        answer(
            "SELECT * FROM TABLE(su2(1::FLOAT))");
        assertAnswer(
            "Function SU3 successfully created.",
            "CREATE OR REPLACE FUNCTION su3(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ function "
                + "(row, rowWriter) { rowWriter.writeRow({V: 1}); } $$");
        assertAnswer(
            "REFUSED User Defined Table Function object is missing property processRow",
            "SELECT * FROM TABLE(su3(1::FLOAT))");
        assertAnswer(
            "Function SU4 successfully created.",
            "CREATE OR REPLACE FUNCTION su4(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: 1})  $$");
        answer(
            "SELECT * FROM TABLE(su4(1::FLOAT))");
        assertAnswer(
            "Function SU5 successfully created.",
            "CREATE OR REPLACE FUNCTION su5(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { }, finalize: function (rowWriter, context) { "
                + "throw new Error(\"fin fails\"); } } $$");
        answer(
            "SELECT * FROM TABLE(su5(1::FLOAT))");
        assertAnswer(
            "Function SU6 successfully created.",
            "CREATE OR REPLACE FUNCTION su6(N FLOAT) RETURNS TABLE (v NUMBER) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: row.N}); } } $$");
        assertAnswer(
            "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported in function SU6",
            "SELECT * FROM TABLE(su6(1::FLOAT))");
        assertAnswer(
            "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.",
            "CREATE OR REPLACE FUNCTION su7(N NUMBER) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: row.N}); } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function SU7",
            "SELECT * FROM TABLE(su7(1))");
        assertAnswer(
            "Function SU8 successfully created.",
            "CREATE OR REPLACE FUNCTION su8(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { rowWriter.writeRow({V: row.N * 2}); return 5; }, "
                + "initialize: 7 } $$");
        answer(
            "SELECT * FROM TABLE(su8(1::FLOAT))");
        assertAnswer(
            "Function SU9 successfully created.",
            "CREATE OR REPLACE FUNCTION su9(N FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$ { "
                + "processRow: function (row, rowWriter, context) { rowWriter.writeRow(5); } } $$");
        assertAnswer(
            "null",
            "SELECT * FROM TABLE(su9(1::FLOAT))");
        assertAnswer(
            "12",
            "SELECT COUNT(*) FROM ev, TABLE(su1(ev.v::FLOAT, ev.g)) f");
    }

    @Test
    public void aPartitionIsReadByOneObject() {
        assertAnswer(
            "Table EV successfully created.",
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        assertAnswer(
            "3",
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10)");
        assertAnswer(
            "Function JS_ECHO successfully created.",
            "CREATE OR REPLACE FUNCTION js_echo(V FLOAT) RETURNS TABLE (o FLOAT, pos FLOAT) LANGUAGE JAVASCRIPT "
                + "AS $$ { initialize: function (argumentInfo, context) { this.i = 0; }, processRow: function (row, "
                + "rowWriter, context) { this.i += 1; rowWriter.writeRow({O: row.V, POS: this.i}); } } $$");
        shape(
            "SELECT * FROM ev, TABLE(js_echo(ev.v::FLOAT) OVER (PARTITION BY ev.g ORDER BY ev.v DESC)) f");
        assertAnswer(
            "a|2.0|1.0 ;; a|1.0|2.0 ;; b|10.0|1.0",
            "SELECT ev.g, f.o, f.pos FROM ev, TABLE(js_echo(ev.v::FLOAT) OVER (PARTITION BY ev.g ORDER BY ev.v "
                + "DESC)) f ORDER BY 1, 3");
        assertAnswer(
            "a|1.0|1.0 ;; a|2.0|2.0 ;; b|10.0|1.0",
            "SELECT ev.g, f.o, f.pos FROM ev, TABLE(js_echo(ev.v::FLOAT) OVER (PARTITION BY ev.g ORDER BY ev.v)) "
                + "f ORDER BY 1, 3");
        assertAnswer(
            "10.0|1.0 ;; 2.0|2.0 ;; 1.0|3.0",
            "SELECT f.o, f.pos FROM ev, TABLE(js_echo(ev.v::FLOAT) OVER (ORDER BY ev.v DESC)) f ORDER BY 2");
        assertAnswer(
            "1.0|1.0 ;; 2.0|2.0 ;; 10.0|3.0",
            "SELECT f.o, f.pos FROM ev, TABLE(js_echo(ev.v::FLOAT) OVER ()) f ORDER BY 1");
        assertAnswer(
            "3.0|3",
            "SELECT MAX(f.pos), COUNT(*) FROM ev, TABLE(js_echo(ev.v::FLOAT) OVER (PARTITION BY 1)) f");
    }
}
