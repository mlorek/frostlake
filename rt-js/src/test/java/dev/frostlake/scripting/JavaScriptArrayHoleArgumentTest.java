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
 * What a JavaScript handler finds where an ARRAY, an OBJECT or a VARIANT argument holds a SQL NULL element: a
 * hole of the array. The array keeps its length, the element reads {@code undefined} and {@code 1 in A} is
 * false, at the end of the array as inside it and at any depth, while a JSON null stays {@code null} and an
 * object's JSON null member stays one. A function's and a procedure's handler find the same, and so does a
 * value read from a table. A SQL NULL argument is {@code undefined}; a JSON null is {@code null} for a
 * VARIANT parameter. A variant that is no array arrives as a one-element array for an ARRAY parameter and is
 * refused for an OBJECT one, and a number, a text or an object written for an ARRAY parameter is refused while
 * the call compiles. Every answer is live-verified.
 */
public class JavaScriptArrayHoleArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theHandlerGetsTheArrayNotItsText() {
        assertAnswer(
            "Function FA successfully created.",
            "CREATE OR REPLACE FUNCTION fa(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof A + "
                + "':' + A.length + ':' + JSON.stringify(A) + ':' + (A[1] === null) + ':' + (A[1] === undefined); $$");
        assertAnswer(
            "object:3:[1,null,\"x\"]:false:true",
            "SELECT fa(ARRAY_CONSTRUCT(1, NULL, 'x'))");
        assertAnswer(
            "object:3:[1,null,\"x\"]:true:false",
            "SELECT fa(PARSE_JSON('[1,null,\"x\"]')::ARRAY)");
        assertAnswer(
            "object:0:[]:false:true",
            "SELECT fa(ARRAY_CONSTRUCT())");
        assertAnswer(
            "REFUSED JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading "
                + "'length') in FA at ' return typeof A + ':' + A.length + ':' + JSON.stringify(A) + ':' + (A[1] === "
                + "null) + ':' + (A[1] === undefined); ' position 27 | stackstrace:  | FA line: 1",
            "SELECT fa(NULL)");
        answer(
            "SELECT fa(ARRAY_CONSTRUCT(1.5, TRUE, '2024-01-01'::DATE))");
        assertAnswer(
            "Function FV successfully created.",
            "CREATE OR REPLACE FUNCTION fv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof V + "
                + "':' + JSON.stringify(V); $$");
        assertAnswer(
            "object:[1,null,2]",
            "SELECT fv(ARRAY_CONSTRUCT(1, NULL, 2))");
        assertAnswer(
            "object:null",
            "SELECT fv(PARSE_JSON('null'))");
        assertAnswer(
            "undefined:undefined",
            "SELECT fv(NULL)");
        assertAnswer(
            "number:1.5",
            "SELECT fv(1.5)");
        assertAnswer(
            "string:\"text\"",
            "SELECT fv('text'::VARIANT)");
        assertAnswer(
            "boolean:true",
            "SELECT fv(TRUE::VARIANT)");
        assertAnswer(
            "object:{}",
            "SELECT fv(OBJECT_CONSTRUCT('a', NULL))");
        assertAnswer(
            "object:{\"a\":null}",
            "SELECT fv(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL))");
        answer(
            "SELECT fv(TO_VARIANT('2024-01-01'::DATE))");
        assertAnswer(
            "Function FO successfully created.",
            "CREATE OR REPLACE FUNCTION fo(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof O + "
                + "':' + JSON.stringify(O); $$");
        assertAnswer(
            "object:{\"a\":[1,null]}",
            "SELECT fo(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, NULL)))");
        assertAnswer(
            "object:{\"b\":1.5,\"c\":\"x\"}",
            "SELECT fo(OBJECT_CONSTRUCT('b', 1.5, 'c', 'x'))");
        assertAnswer(
            "undefined:undefined",
            "SELECT fo(NULL)");
    }

    @Test
    public void aSqlNullElementOfAVariantIsAHole() {
        assertAnswer(
            "Function DV successfully created.",
            "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(V); $$");
        assertAnswer(
            "[number(1),hole:undef,number(2)]",
            "SELECT dv(ARRAY_CONSTRUCT(1, NULL, 2))");
        assertAnswer(
            "[hole:undef]",
            "SELECT dv(ARRAY_CONSTRUCT(NULL))");
        assertAnswer(
            "[number(1),hole:undef]",
            "SELECT dv(ARRAY_CONSTRUCT(1, NULL))");
        assertAnswer(
            "[number(1),null,number(2)]",
            "SELECT dv(PARSE_JSON('[1,null,2]'))");
        assertAnswer(
            "[number(1),hole:undef,number(2)]",
            "SELECT dv(PARSE_JSON('[1,,2]'))");
        assertAnswer(
            "[number(1),hole:undef,number(2)]",
            "SELECT dv(PARSE_JSON('[1,undefined,2]'))");
        assertAnswer(
            "{a=[number(1),hole:undef]}",
            "SELECT dv(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, NULL)))");
        assertAnswer(
            "{a=null,b=number(1)}",
            "SELECT dv(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL, 'b', 1))");
        assertAnswer(
            "{a=null}",
            "SELECT dv(OBJECT_CONSTRUCT('a', PARSE_JSON('null')))");
        assertAnswer(
            "{b=number(1)}",
            "SELECT dv(OBJECT_CONSTRUCT('a', NULL, 'b', 1))");
        assertAnswer(
            "[[hole:undef,number(1)],{k=null}]",
            "SELECT dv(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(NULL, 1), OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)))");
        assertAnswer(
            "undef",
            "SELECT dv(NULL)");
        assertAnswer(
            "null",
            "SELECT dv(PARSE_JSON('null'))");
        assertAnswer(
            "undef",
            "SELECT dv(NULL::VARIANT)");
        assertAnswer(
            "undef",
            "SELECT dv(TO_VARIANT(NULL))");
        assertAnswer(
            "undef",
            "SELECT dv(ARRAY_CONSTRUCT(1, NULL, 2)[1])");
        assertAnswer(
            "null",
            "SELECT dv(PARSE_JSON('{\"a\":null}'):a)");
        assertAnswer(
            "undef",
            "SELECT dv(PARSE_JSON('{\"a\":1}'):b)");
        assertAnswer(
            "[null,hole:undef,null]",
            "SELECT dv(ARRAY_CONSTRUCT(PARSE_JSON('null'), NULL, PARSE_JSON('null')))");
    }

    @Test
    public void aSqlNullElementOfAnArrayIsAHole() {
        assertAnswer(
            "Function DA successfully created.",
            "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(A); $$");
        assertAnswer(
            "[number(1),hole:undef,string(x)]",
            "SELECT da(ARRAY_CONSTRUCT(1, NULL, 'x'))");
        assertAnswer(
            "[hole:undef]",
            "SELECT da(ARRAY_CONSTRUCT(NULL))");
        assertAnswer(
            "[hole:undef,hole:undef]",
            "SELECT da(ARRAY_CONSTRUCT(NULL, NULL))");
        assertAnswer(
            "[number(1),hole:undef]",
            "SELECT da(ARRAY_CONSTRUCT(1, NULL))");
        assertAnswer(
            "[null,hole:undef]",
            "SELECT da(ARRAY_CONSTRUCT(PARSE_JSON('null'), NULL))");
        assertAnswer(
            "[number(1),number(2)]",
            "SELECT da(ARRAY_CONSTRUCT_COMPACT(1, NULL, 2))");
        assertAnswer(
            "[number(1),null,number(2)]",
            "SELECT da(PARSE_JSON('[1,null,2]'))");
        assertAnswer(
            "[number(1),null,number(2)]",
            "SELECT da(PARSE_JSON('[1,null,2]')::ARRAY)");
        assertAnswer(
            "[number(1),hole:undef,number(2)]",
            "SELECT da(PARSE_JSON('[1,,2]')::ARRAY)");
        assertAnswer(
            "[number(1),hole:undef]",
            "SELECT da(ARRAY_APPEND(ARRAY_CONSTRUCT(1), NULL))");
        assertAnswer(
            "[number(1),hole:undef,number(2)]",
            "SELECT da([1, NULL, 2])");
        assertAnswer(
            "[[hole:undef],{k=null}]",
            "SELECT da(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(NULL), OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)))");
        assertAnswer(
            "[number(1),number(3)]",
            "SELECT da(ARRAY_AGG(x) WITHIN GROUP (ORDER BY x)) FROM (SELECT 1 AS x UNION ALL SELECT NULL UNION "
                + "ALL SELECT 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DA': (NUMBER(1,0))",
            "SELECT da(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DA': (VARCHAR(1))",
            "SELECT da('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DA': (OBJECT)",
            "SELECT da(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "[number(1)]",
            "SELECT da(TO_VARIANT(1))");
        assertAnswer(
            "undef",
            "SELECT da(NULL)");
        assertAnswer(
            "[]",
            "SELECT da(ARRAY_CONSTRUCT())");
    }

    @Test
    public void anObjectsArrayKeepsItsHoles() {
        assertAnswer(
            "Function DOBJ successfully created.",
            "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(O); $$");
        assertAnswer(
            "{a=[number(1),hole:undef]}",
            "SELECT dobj(OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(1, NULL)))");
        assertAnswer(
            "{a=null}",
            "SELECT dobj(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL))");
        assertAnswer(
            "{}",
            "SELECT dobj(OBJECT_CONSTRUCT('a', NULL))");
        assertAnswer(
            "{a=number(1)}",
            "SELECT dobj(OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'b', NULL))");
        assertAnswer(
            "{a=[null,hole:undef,number(1)]}",
            "SELECT dobj(PARSE_JSON('{\"a\":[null,,1]}')::OBJECT)");
        assertAnswer(
            "{a=number(1)}",
            "SELECT dobj(PARSE_JSON('{\"a\":1}'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DOBJ': (ARRAY)",
            "SELECT dobj(ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "REFUSED Failed to cast variant value 1 to OBJECT",
            "SELECT dobj(TO_VARIANT(1))");
        assertAnswer(
            "undef",
            "SELECT dobj(NULL)");
        assertAnswer(
            "{}",
            "SELECT dobj(OBJECT_CONSTRUCT())");
    }

    @Test
    public void aStoredValueKeepsItsHoles() {
        assertAnswer(
            "Function DV successfully created.",
            "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(V); $$");
        assertAnswer(
            "Function DA successfully created.",
            "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(A); $$");
        assertAnswer(
            "Function DOBJ successfully created.",
            "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(O); $$");
        assertAnswer(
            "Table TV successfully created.",
            "CREATE OR REPLACE TABLE tv (id NUMBER, v VARIANT, a ARRAY, o OBJECT)");
        assertAnswer(
            "1",
            "INSERT INTO tv SELECT 1, ARRAY_CONSTRUCT(1, NULL), ARRAY_CONSTRUCT(NULL, 'x'), OBJECT_CONSTRUCT('k', "
                + "ARRAY_CONSTRUCT(NULL))");
        assertAnswer(
            "1",
            "INSERT INTO tv SELECT 2, NULL, NULL, NULL");
        assertAnswer(
            "1",
            "INSERT INTO tv SELECT 3, PARSE_JSON('null'), PARSE_JSON('[null]')::ARRAY, "
                + "PARSE_JSON('{\"k\":null}')::OBJECT");
        assertAnswer(
            "1|[number(1),hole:undef]|[hole:undef,string(x)]|{k=[hole:undef]} ;; 2|undef|undef|undef ;; "
                + "3|null|[null]|{k=null}",
            "SELECT id, dv(v), da(a), dobj(o) FROM tv ORDER BY id");
    }

    @Test
    public void aProcedureFindsTheSameHoles() {
        assertAnswer(
            "Function PV successfully created.",
            "CREATE OR REPLACE PROCEDURE pv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(V); $$");
        assertAnswer(
            "Function PA successfully created.",
            "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = function "
                + "(x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return 'Date(' "
                + "+ x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + Array.prototype.join.call(x, "
                + "' ') + ')'; } if (typeof x === 'object') { var p = []; for (var k in x) { p.push(k + '=' + "
                + "d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + '(' + x + ')'; }; return d(A); $$");
        assertAnswer(
            "[number(1),hole:undef,number(2)]",
            "CALL pv(ARRAY_CONSTRUCT(1, NULL, 2))");
        assertAnswer(
            "[number(1),hole:undef]",
            "CALL pa(ARRAY_CONSTRUCT(1, NULL))");
        assertAnswer(
            "[number(1),null,number(2)]",
            "CALL pv(PARSE_JSON('[1,null,2]'))");
        assertAnswer(
            "undef",
            "CALL pv(NULL)");
        assertAnswer(
            "null",
            "CALL pv(PARSE_JSON('null'))");
        assertAnswer(
            "{a=null}",
            "CALL pv(OBJECT_CONSTRUCT_KEEP_NULL('a', NULL))");
    }
}
