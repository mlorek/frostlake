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
 * What a JavaScript handler finds for the typed values a VARIANT, an ARRAY or an OBJECT argument holds: a DATE
 * and a TIMESTAMP are a {@code Date} - a DATE the midnight that begins it in the session's time zone, a
 * TIMESTAMP_NTZ its wall clock read in that zone, a zoned timestamp its own instant - a BINARY is a
 * {@code Uint8Array}, a FLOAT a number, NaN and the infinities included, a BOOLEAN a boolean and a number too
 * long for a double the nearest one. A TIME has no counterpart: the call is refused, for a function and a
 * procedure alike. A DATE, a text, a BINARY or a TIME written for a VARIANT parameter is refused while the call
 * compiles, where a number and a BOOLEAN are taken. A value read from a table keeps what it holds. The
 * session's TIMEZONE is set by the tests and unset whatever the assertions make of the statements between.
 * Every answer is live-verified.
 */
public class JavaScriptTypedMemberArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aTypedMemberArrivesAsItsOwnKind() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function DV successfully created.",
                "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "Function DA successfully created.",
                "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(A); $$");
            assertAnswer(
                "Function DOBJ successfully created.",
                "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(O); $$");
            assertAnswer(
                "Date(1704067200000)",
                "SELECT dv(TO_VARIANT('2024-01-01'::DATE))");
            assertAnswer(
                "[Date(1704067200000)]",
                "SELECT dv(ARRAY_CONSTRUCT('2024-01-01'::DATE))");
            assertAnswer(
                "{d=Date(1704067200000)}",
                "SELECT dv(OBJECT_CONSTRUCT('d', '2024-01-01'::DATE))");
            assertAnswer(
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function DV "
                    + "with handler DV",
                "SELECT dv(TO_VARIANT('10:11:12.5'::TIME))");
            assertAnswer(
                "Date(1709633472345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))");
            assertAnswer(
                "Date(1709615472345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345 +05:00'::TIMESTAMP_TZ))");
            assertAnswer(
                "Date(1709633472345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))");
            assertAnswer(
                "Date(1709633472345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345678912'::TIMESTAMP_NTZ))");
            assertAnswer(
                "U8(255 1)",
                "SELECT dv(TO_VARIANT(TO_BINARY('FF01', 'HEX')))");
            assertAnswer(
                "[U8(255 1)]",
                "SELECT dv(ARRAY_CONSTRUCT(TO_BINARY('FF01', 'HEX')))");
            assertAnswer(
                "number(1.5)",
                "SELECT dv(TO_VARIANT(1.5::FLOAT))");
            assertAnswer(
                "number(NaN)",
                "SELECT dv(TO_VARIANT('NaN'::FLOAT))");
            assertAnswer(
                "number(Infinity)",
                "SELECT dv(TO_VARIANT('inf'::FLOAT))");
            assertAnswer(
                "[number(NaN),number(-Infinity),number(3)]",
                "SELECT dv(ARRAY_CONSTRUCT('NaN'::FLOAT, '-inf'::FLOAT, 3::FLOAT))");
            assertAnswer(
                "number(12345678901234567000)",
                "SELECT dv(TO_VARIANT(12345678901234567890))");
            assertAnswer(
                "number(1.5)",
                "SELECT dv(TO_VARIANT(1.5))");
            assertAnswer(
                "number(9007199254740992)",
                "SELECT dv(TO_VARIANT(9007199254740993))");
            assertAnswer(
                "number(1.2345678901234568e+29)",
                "SELECT dv(TO_VARIANT(123456789012345678901234567890.123456))");
            assertAnswer(
                "boolean(true)",
                "SELECT dv(TO_VARIANT(TRUE))");
            assertAnswer(
                "string(s)",
                "SELECT dv(TO_VARIANT('s'))");
            assertAnswer(
                "number(1)",
                "SELECT dv(1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DV': (DATE)",
                "SELECT dv('2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DV': (VARCHAR(3))",
                "SELECT dv('txt')");
            assertAnswer(
                "boolean(true)",
                "SELECT dv(TRUE)");
            assertAnswer(
                "number(1.5)",
                "SELECT dv(1.5::FLOAT)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DV': (BINARY(67108864))",
                "SELECT dv(TO_BINARY('FF', 'HEX'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DV': (TIME(9))",
                "SELECT dv('10:11:12'::TIME)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DV': (TIMESTAMP_NTZ(9))",
                "SELECT dv('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)");
            assertAnswer(
                "number(1.5)",
                "SELECT dv(1.5)");
            assertAnswer(
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function DA "
                    + "with handler DA",
                "SELECT da(ARRAY_CONSTRUCT('2024-01-01'::DATE, '10:11:12'::TIME, TO_BINARY('FF01', 'HEX'), "
                    + "1.5::FLOAT))");
            assertAnswer(
                "{d=Date(1704067200000),t=Date(1709633472345)}",
                "SELECT dobj(OBJECT_CONSTRUCT('d', '2024-01-01'::DATE, 't', '2024-03-05 "
                    + "10:11:12.345'::TIMESTAMP_NTZ))");
            assertAnswer(
                "[[Date(1704067200000)],{d=Date(1704153600000)}]",
                "SELECT da(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('2024-01-01'::DATE), OBJECT_CONSTRUCT('d', "
                    + "'2024-01-02'::DATE)))");
            assertAnswer(
                "Function PV successfully created.",
                "CREATE OR REPLACE PROCEDURE pv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "Date(1704067200000)",
                "CALL pv(TO_VARIANT('2024-01-01'::DATE))");
            assertAnswer(
                "[Date(1704067200000),U8(255 1),number(1.5)]",
                "CALL pv(ARRAY_CONSTRUCT('2024-01-01'::DATE, TO_BINARY('FF01', 'HEX'), 1.5::FLOAT))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aDateFollowsTheSessionZone() {
        try {
            assertAnswer(
                "Function DV successfully created.",
                "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "Function DA successfully created.",
                "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(A); $$");
            assertAnswer(
                "Function DOBJ successfully created.",
                "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(O); $$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertAnswer(
                "Date(1704096000000)",
                "SELECT dv(TO_VARIANT('2024-01-01'::DATE))");
            assertAnswer(
                "Date(1709662272345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ))");
            assertAnswer(
                "Date(1709615472345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345 +05:00'::TIMESTAMP_TZ))");
            assertAnswer(
                "Date(1709662272345)",
                "SELECT dv(TO_VARIANT('2024-03-05 10:11:12.345'::TIMESTAMP_LTZ))");
            assertAnswer(
                "[Date(1719817200000)]",
                "SELECT dv(ARRAY_CONSTRUCT('2024-07-01'::DATE))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aTimeInsideAValueIsRefused() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function DV successfully created.",
                "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "Function DA successfully created.",
                "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(A); $$");
            assertAnswer(
                "Function DOBJ successfully created.",
                "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(O); $$");
            assertAnswer(
                "Function PV successfully created.",
                "CREATE OR REPLACE PROCEDURE pv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function PV "
                    + "with handler PV",
                "CALL pv(TO_VARIANT('10:11:12'::TIME))");
            assertAnswer(
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function PV "
                    + "with handler PV",
                "CALL pv(ARRAY_CONSTRUCT('10:11:12'::TIME))");
            assertAnswer(
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function DV "
                    + "with handler DV",
                "SELECT dv(OBJECT_CONSTRUCT('t', '10:11:12'::TIME))");
            assertAnswer(
                "REFUSED JavaScript execution error: TIME inside VARIANT value - not supported in function DOBJ "
                    + "with handler DOBJ",
                "SELECT dobj(OBJECT_CONSTRUCT('t', '10:11:12'::TIME))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aJsonNullOrAScalarForAContainerParameter() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function DV successfully created.",
                "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "Function DA successfully created.",
                "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(A); $$");
            assertAnswer(
                "Function DOBJ successfully created.",
                "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(O); $$");
            assertAnswer(
                "undef",
                "SELECT da(PARSE_JSON('null'))");
            assertAnswer(
                "undef",
                "SELECT dobj(PARSE_JSON('null'))");
            assertAnswer(
                "undef",
                "SELECT da(PARSE_JSON('null')::ARRAY)");
            assertAnswer(
                "[Date(1704067200000)]",
                "SELECT da(TO_VARIANT('2024-01-01'::DATE))");
            assertAnswer(
                "[string(s)]",
                "SELECT da(TO_VARIANT('s'))");
            assertAnswer(
                "[{a=number(1)}]",
                "SELECT da(PARSE_JSON('{\"a\":1}'))");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aStoredValueKeepsItsTypedMembers() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function DV successfully created.",
                "CREATE OR REPLACE FUNCTION dv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(V); $$");
            assertAnswer(
                "Function DA successfully created.",
                "CREATE OR REPLACE FUNCTION da(a ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(A); $$");
            assertAnswer(
                "Function DOBJ successfully created.",
                "CREATE OR REPLACE FUNCTION dobj(o OBJECT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var d = "
                    + "function (x) { if (x === null) { return 'null'; } if (x === undefined) { return 'undef'; } if "
                    + "(Array.isArray(x)) { var o = []; for (var i = 0; i < x.length; i++) { o.push((i in x ? '' : "
                    + "'hole:') + d(x[i])); } return '[' + o.join(',') + ']'; } if (x instanceof Date) { return "
                    + "'Date(' + x.getTime() + ')'; } if (x instanceof Uint8Array) { return 'U8(' + "
                    + "Array.prototype.join.call(x, ' ') + ')'; } if (typeof x === 'object') { var p = []; for (var "
                    + "k in x) { p.push(k + '=' + d(x[k])); } return '{' + p.join(',') + '}'; } return typeof x + "
                    + "'(' + x + ')'; }; return d(O); $$");
            assertAnswer(
                "Table TD successfully created.",
                "CREATE OR REPLACE TABLE td (id NUMBER, v VARIANT, a ARRAY, o OBJECT)");
            assertAnswer(
                "1",
                "INSERT INTO td SELECT 1, ARRAY_CONSTRUCT('2024-01-01'::DATE, TO_BINARY('FF01', 'HEX'), "
                    + "1.5::FLOAT, NULL), ARRAY_CONSTRUCT('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ), "
                    + "OBJECT_CONSTRUCT('d', '2024-01-02'::DATE)");
            assertAnswer(
                "1",
                "INSERT INTO td SELECT 2, TO_VARIANT('2024-01-01'::DATE), NULL, NULL");
            assertAnswer(
                "1",
                "INSERT INTO td SELECT 3, TO_VARIANT(TO_BINARY('CAFE', 'HEX')), NULL, NULL");
            assertAnswer(
                "1|[Date(1704067200000),U8(255 1),number(1.5),"
                    + "hole:undef]|[Date(1709633472345)]|{d=Date(1704153600000)} ;; "
                    + "2|Date(1704067200000)|undef|undef ;; 3|U8(202 254)|undef|undef",
                "SELECT id, dv(v), da(a), dobj(o) FROM td ORDER BY id");
            assertAnswer(
                "1|ARRAY|DATE|BINARY|TIMESTAMP_NTZ|DATE ;; 2|DATE|null|null|null|null ;; "
                    + "3|BINARY|null|null|null|null",
                "SELECT id, TYPEOF(v), TYPEOF(v[0]), TYPEOF(v[1]), TYPEOF(a[0]), TYPEOF(o:d) FROM td ORDER BY id");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
