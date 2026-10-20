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
 * What a JavaScript table function's handler object is told and how its faults read (live-verified). An error a
 * member raises lists the object's own functions after {@code stackstrace: }, each at its line counted from 0, and no
 * frame for the routine; a member that is no function is refused quoting the account's own line that calls it; a
 * body whose value is no object is {@code User Defined Table Function not an object}. {@code argumentInfo} tells a
 * constant number argument by {@code isConst} and its {@code constValue}. A name no function carries is an unknown
 * table function before the select list's names are read.
 */
public class JavaScriptTableHandlerObjectTest extends MeasuredCellsTestSupport {

    /** Faults in processRow, initialize, finalize and the object itself. */
    @Test
    public void aMembersFaultReadsAsTheAccountReportsIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (row, rowWriter, context) { throw new Error('pr bad'); }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught Error: pr bad in JSQ at '({processRow: function (row, "
                + "rowWriter, context) { throw new Error('pr bad'); }})' position 51 | stackstrace:  | processRow "
                + "line: 0"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (row, rowWriter, context) {}, finalize: function (rowWriter, context) { throw new "
                + "Error('fin bad'); }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught Error: fin bad in JSQ at '({processRow: function "
                + "(row, rowWriter, context) {}, finalize: function (rowWriter, context) { throw new Error('fin "
                + "bad'); }})' position 95 | stackstrace:  | finalize line: 0"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (row, rowWriter, context) {}, initialize: function (argumentInfo, context) { throw new "
                + "Error('init bad'); }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught Error: init bad in JSQ at '({processRow: function "
                + "(row, rowWriter, context) {}, initialize: function (argumentInfo, context) { throw new "
                + "Error('init bad'); }})' position 100 | stackstrace:  | initialize line: 0"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${\n  "
                + "processRow: function (row, rowWriter, context) {\n    var x = 1;\n    throw new Error('line "
                + "4');\n  }\n}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught Error: line 4 in JSQ at '    throw new Error('line "
                + "4');' position 4 | stackstrace:  | processRow line: 3"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$(function () { "
                + "function boom() { throw new Error('helper'); } return {processRow: function (r, w) { boom(); }}; "
                + "})()$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught Error: helper in JSQ at '((function () { function "
                + "boom() { throw new Error('helper'); } return {processRow: function (r, w) { boom(); }}; })())' "
                + "position 34 | stackstrace:  | boom line: 0 | processRow line: 0"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) { throw 'str bad'; }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught str bad in JSQ at '({processRow: function (r, w) { "
                + "throw 'str bad'; }})' position 32 | stackstrace:  | processRow line: 0"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) { null.x; }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading "
                + "'x') in JSQ at '({processRow: function (r, w) { null.x; }})' position 37 | stackstrace:  | "
                + "processRow line: 0"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) {}, initialize: 7}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught TypeError: udtfObject.initialize is not a function in "
                + "JSQ at '            udtfObject.initialize(argInfo, context);' position 23"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "7}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught TypeError: udtfObject.processRow is not a function in "
                + "JSQ at '    var keepGoing = udtfObject.processRow(row, rowWriter, context);' position 31"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) {}, finalize: 7}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught TypeError: udtfObject.finalize is not a function in "
                + "JSQ at '        udtfObject.finalize(rowWriter, context);' position 19"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED User Defined Table Function object is missing property processRow"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$null$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED User Defined Table Function not an object"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $$7$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED User Defined Table Function not an object"},
            {"CREATE OR REPLACE FUNCTION jsq(n FLOAT) RETURNS TABLE (v FLOAT) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) { w.writeRow({V: r.N}); }, initialize: function (ai, c) { undefinedFn(); }}$$",
                "Function JSQ successfully created."},
            {"SELECT * FROM TABLE(jsq(1::FLOAT))",
                "REFUSED JavaScript execution error: Uncaught ReferenceError: undefinedFn is not defined in JSQ at "
                + "'({processRow: function (r, w) { w.writeRow({V: r.N}); }, initialize: function (ai, c) { "
                + "undefinedFn(); }})' position 88 | stackstrace:  | initialize line: 0"},
        });
    }

    /** Constant and column arguments, and calls the account refuses. */
    @Test
    public void argumentInfoTellsAConstantNumber() {
        engine.execute("CREATE OR REPLACE TABLE jev (g VARCHAR, v FLOAT)");
        engine.execute("INSERT INTO jev VALUES ('a', 1), ('a', 2), (NULL, 5), ('b', 10)");
        engine.execute("SET jsv = 2.5");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION su2(n FLOAT) RETURNS TABLE (j VARCHAR) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) {}, initialize: function (ai, c) { this.j = JSON.stringify(ai); }, finalize: "
                + "function (w, c) { w.writeRow({J: this.j}); }}$$",
                "Function SU2 successfully created."},
            {"SELECT * FROM TABLE(su2(1::FLOAT))",
                "{\"N\":{\"constValue\":1,\"isNullable\":true,\"isConst\":true,\"type\":\"real\"}}"},
            {"SELECT * FROM TABLE(su2(1))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (NUMBER(1,0))"},
            {"SELECT * FROM TABLE(su2(NULL))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (NULL)"},
            {"SELECT * FROM TABLE(su2(1 + 1))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (NUMBER(2,0))"},
            {"SELECT * FROM TABLE(su2(ABS(-1.5)))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (NUMBER(3,1))"},
            {"SELECT DISTINCT j FROM jev, TABLE(su2(jev.v))",
                "{\"N\":{\"isNullable\":true,\"isConst\":false,\"type\":\"real\"}}"},
            {"SELECT * FROM TABLE(su2($jsv))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (NUMBER(2,1))"},
            {"SELECT * FROM TABLE(su2(-0.5::FLOAT))",
                "{\"N\":{\"constValue\":-0.5,\"isNullable\":true,\"isConst\":true,\"type\":\"real\"}}"},
            {"SELECT * FROM TABLE(su2('3'))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (VARCHAR(1))"},
            {"SELECT * FROM TABLE(su2(1e300 * 10))",
                "{\"N\":{\"constValue\":1e+301,\"isNullable\":true,\"isConst\":true,\"type\":\"real\"}}"},
            {"SELECT * FROM TABLE(su2(RANDOM() * 0))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'SU2': (NUMBER(20,0))"},
            {"CREATE OR REPLACE FUNCTION su3(s VARCHAR, b BOOLEAN, d DATE, n NUMBER, v VARIANT, t TIMESTAMP_NTZ, x "
                + "BINARY, a ARRAY, o OBJECT) RETURNS TABLE (j VARCHAR) LANGUAGE JAVASCRIPT AS $${processRow: "
                + "function (r, w) {}, initialize: function (ai, c) { this.j = JSON.stringify(ai); }, finalize: "
                + "function (w, c) { w.writeRow({J: this.j}); }}$$",
                "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type."},
            {"SELECT * FROM TABLE(su3('x', TRUE, '2024-01-02'::DATE, 5, PARSE_JSON('[1]'), '2024-01-02 "
                + "03:04:05'::TIMESTAMP_NTZ, TO_BINARY('AB', 'HEX'), ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('k', 1)))",
                "REFUSED SQL compilation error: | Unknown table function SU3"},
            {"SELECT * FROM TABLE(su3(NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL))",
                "REFUSED SQL compilation error: | Unknown table function SU3"},
            {"SELECT DISTINCT j FROM jev, TABLE(su3(jev.g, jev.v > 1, '2024-01-02'::DATE, jev.v::NUMBER, "
                + "jev.v::VARIANT, NULL, NULL, NULL, NULL))",
                "REFUSED SQL compilation error: | Unknown table function SU3"},
            {"CREATE OR REPLACE FUNCTION su4(n NUMBER(10, 2), f DOUBLE, i INT, t TIMESTAMP_LTZ, z TIMESTAMP_TZ, tm "
                + "TIME, s2 VARCHAR(5)) RETURNS TABLE (j VARCHAR) LANGUAGE JAVASCRIPT AS $${processRow: function (r, "
                + "w) {}, initialize: function (ai, c) { this.j = JSON.stringify(ai); }, finalize: function (w, c) { "
                + "w.writeRow({J: this.j}); }}$$",
                "REFUSED Language JAVASCRIPT does not support type 'NUMBER(10,2)' for argument or return type."},
            {"SELECT * FROM TABLE(su4(1.5, 2, 3, '2024-01-02 03:04:05'::TIMESTAMP_LTZ, '2024-01-02 03:04:05 "
                + "+01:00'::TIMESTAMP_TZ, '03:04:05'::TIME, 'ab'))",
                "REFUSED SQL compilation error: | Unknown table function SU4"},
        });
    }
}
