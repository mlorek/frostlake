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
 * What a JavaScript handler's body reads besides its parameters (live-verified): the global names alone - snowflake,
 * Snowflake, its function's own name - and none of the invoker's (parse, toJson, isArray, handler or its helpers); a
 * table function's name is an empty function of that name while its handler runs. A timestamp or a DATE argument is a
 * Date at its instant, a DATE at midnight in the session's zone, and an LTZ inside a VARIANT a Date as well; a
 * timestamp argument arrives cut to its parameter's precision.
 */
public class JavaScriptInvokerNamesTest extends MeasuredCellsTestSupport {

    /** The names a function's, a procedure's and a table function's body reads. */
    @Test
    public void bodyNames() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jn795() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nreturn [typeof parse, "
                + "typeof toJson, typeof isArray, typeof handler, typeof snowflake, typeof Snowflake, typeof JN795, "
                + "typeof jn795, typeof arrive, typeof bytesOf, typeof a0, typeof result, typeof had, typeof saved, "
                + "typeof api, typeof made, typeof ran, typeof refuse, typeof globalThis, typeof "
                + "arguments].join(',');\n$$",
                "Function JN795 successfully created."},
            {"CREATE OR REPLACE FUNCTION jna795(x FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nreturn [typeof "
                + "parse, typeof toJson, typeof isArray, typeof handler, typeof snowflake, typeof Snowflake, typeof "
                + "JNA795, typeof X, typeof arrive, typeof bytesOf, typeof a0, typeof result, typeof had, typeof "
                + "saved, typeof api, typeof made, typeof ran, typeof refuse, typeof globalThis, typeof "
                + "arguments].join(',');\n$$",
                "Function JNA795 successfully created."},
            {"CREATE OR REPLACE PROCEDURE jnp795() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nreturn [typeof parse, "
                + "typeof toJson, typeof isArray, typeof handler, typeof snowflake, typeof Snowflake, typeof JNP795, "
                + "typeof jnp795, typeof arrive, typeof bytesOf, typeof a0, typeof result, typeof had, typeof saved, "
                + "typeof api, typeof made, typeof ran, typeof refuse, typeof globalThis, typeof "
                + "arguments].join(',');\n$$",
                "Function JNP795 successfully created."},
            {"CREATE OR REPLACE FUNCTION jnt795() RETURNS TABLE (v VARCHAR) LANGUAGE JAVASCRIPT AS $$\n{ processRow: "
                + "function (row, rowWriter, context) {},\n  finalize: function (rowWriter, context) {\n    "
                + "rowWriter.writeRow({V: [typeof parse, typeof toJson, typeof isArray, typeof handler, typeof "
                + "snowflake, typeof row, typeof JNT795].join(',')}); } }\n$$",
                "Function JNT795 successfully created."},
            {"SELECT jn795()",
                "undefined,undefined,undefined,undefined,object,function,function,undefined,undefined,undefined,undefin"
                + "ed,undefined,undefined,undefined,undefined,undefined,undefined,undefined,object,object"},
            {"SELECT jna795(1)",
                "undefined,undefined,undefined,undefined,object,function,function,number,undefined,undefined,undefined,"
                + "undefined,undefined,undefined,undefined,undefined,undefined,undefined,object,object"},
            {"CALL jnp795()",
                "undefined,undefined,undefined,undefined,object,function,function,undefined,undefined,undefined,undefin"
                + "ed,undefined,undefined,undefined,undefined,undefined,undefined,undefined,object,object"},
            {"SELECT * FROM TABLE(jnt795())",
                "undefined,undefined,undefined,undefined,object,undefined,function"},
        });
    }

    /** A function's and a table function's own name. */
    @Test
    public void ownNames() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION jn796() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nreturn [typeof JN796, "
                + "JN796.name, JN796.length, String(JN796).slice(0, 60).replace(/\\n/g, '~')].join('|');\n$$",
                "Function JN796 successfully created."},
            {"SELECT jn796()",
                "function|JN796|0|function JN796() {~~return [typeof JN796, JN796.name, JN796."},
            {"CREATE OR REPLACE FUNCTION jnt796() RETURNS TABLE (v VARCHAR) LANGUAGE JAVASCRIPT AS $$\n{ processRow: "
                + "function (row, rowWriter, context) {},\n  finalize: function (rowWriter, context) {\n    "
                + "rowWriter.writeRow({V: [typeof JNT796, JNT796.name, JNT796.length, String(JNT796).slice(0, "
                + "60).replace(/\\n/g, '~')].join('|')}); } }\n$$",
                "Function JNT796 successfully created."},
            {"SELECT * FROM TABLE(jnt796())",
                "function|JNT796|0|function JNT796() {}"},
            {"CREATE OR REPLACE FUNCTION jt0796(t TIMESTAMP_NTZ(0), l TIMESTAMP_LTZ(1)) RETURNS VARCHAR LANGUAGE "
                + "JAVASCRIPT AS $$\nreturn (T.getTime() % 1000) + '|' + (L.getTime() % 1000);\n$$",
                "Function JT0796 successfully created."},
            {"SELECT jt0796('2024-01-02 10:11:12.9'::TIMESTAMP_NTZ(1), '2024-01-02 10:11:12.987'::TIMESTAMP_LTZ(3))",
                "0|900"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** Timestamps and dates as arguments, alone and inside a VARIANT. */
    @Test
    public void timestampArguments() {
        engine.execute("CREATE OR REPLACE FUNCTION jtz794(a TIMESTAMP_TZ, w VARCHAR) RETURNS VARCHAR LANGUAGE "
            + "JAVASCRIPT AS $$\nvar r;\ntry {\n  if (W == 'type') r = typeof A + ':' + "
            + "Object.prototype.toString.call(A);\n  if (W == 'str') r = A.toString();\n  if (W == 'string') r = "
            + "String(A);\n  if (W == 'ms') r = String(A.getTime());\n  if (W == 'm') r = ['getEpochSeconds', "
            + "'getNanoSeconds', 'getScale', 'getTimezone', 'toISOString',\n    'toJSON', 'getTime'].map(function (n) "
            + "{ return n + '=' + typeof A[n]; }).join(',');\n  if (W == 'es') r = String(A.getEpochSeconds());\n  if "
            + "(W == 'ns') r = String(A.getNanoSeconds());\n  if (W == 'sc') r = String(A.getScale());\n  if (W == "
            + "'tz') r = String(A.getTimezone());\n  if (W == 'json') r = JSON.stringify(A);\n  if (W == 'iso') r = "
            + "A.toISOString();\n  if (W == 'own') r = Object.getOwnPropertyNames(A).join(',');\n  if (W == 'proto') r "
            + "= Object.getOwnPropertyNames(Object.getPrototypeOf(A)).sort().join(',');\n  if (W == 'plus') r = "
            + "String(A + 0);\n} catch (e) { r = 'E:' + e; }\nreturn String(r).replace(/-/g, '/');\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jltz794(a TIMESTAMP_LTZ, w VARCHAR) RETURNS VARCHAR LANGUAGE "
            + "JAVASCRIPT AS $$\nvar r;\ntry {\n  if (W == 'type') r = typeof A + ':' + "
            + "Object.prototype.toString.call(A);\n  if (W == 'str') r = A.toString();\n  if (W == 'string') r = "
            + "String(A);\n  if (W == 'ms') r = String(A.getTime());\n  if (W == 'm') r = ['getEpochSeconds', "
            + "'getNanoSeconds', 'getScale', 'getTimezone', 'toISOString',\n    'toJSON', 'getTime'].map(function (n) "
            + "{ return n + '=' + typeof A[n]; }).join(',');\n  if (W == 'es') r = String(A.getEpochSeconds());\n  if "
            + "(W == 'ns') r = String(A.getNanoSeconds());\n  if (W == 'sc') r = String(A.getScale());\n  if (W == "
            + "'tz') r = String(A.getTimezone());\n  if (W == 'json') r = JSON.stringify(A);\n  if (W == 'iso') r = "
            + "A.toISOString();\n  if (W == 'own') r = Object.getOwnPropertyNames(A).join(',');\n  if (W == 'proto') r "
            + "= Object.getOwnPropertyNames(Object.getPrototypeOf(A)).sort().join(',');\n  if (W == 'plus') r = "
            + "String(A + 0);\n} catch (e) { r = 'E:' + e; }\nreturn String(r).replace(/-/g, '/');\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jntz794(a TIMESTAMP_NTZ, w VARCHAR) RETURNS VARCHAR LANGUAGE "
            + "JAVASCRIPT AS $$\nvar r;\ntry {\n  if (W == 'type') r = typeof A + ':' + "
            + "Object.prototype.toString.call(A);\n  if (W == 'str') r = A.toString();\n  if (W == 'string') r = "
            + "String(A);\n  if (W == 'ms') r = String(A.getTime());\n  if (W == 'm') r = ['getEpochSeconds', "
            + "'getNanoSeconds', 'getScale', 'getTimezone', 'toISOString',\n    'toJSON', 'getTime'].map(function (n) "
            + "{ return n + '=' + typeof A[n]; }).join(',');\n  if (W == 'es') r = String(A.getEpochSeconds());\n  if "
            + "(W == 'ns') r = String(A.getNanoSeconds());\n  if (W == 'sc') r = String(A.getScale());\n  if (W == "
            + "'tz') r = String(A.getTimezone());\n  if (W == 'json') r = JSON.stringify(A);\n  if (W == 'iso') r = "
            + "A.toISOString();\n  if (W == 'own') r = Object.getOwnPropertyNames(A).join(',');\n  if (W == 'proto') r "
            + "= Object.getOwnPropertyNames(Object.getPrototypeOf(A)).sort().join(',');\n  if (W == 'plus') r = "
            + "String(A + 0);\n} catch (e) { r = 'E:' + e; }\nreturn String(r).replace(/-/g, '/');\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jd794(a DATE, w VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS "
            + "$$\nvar r;\ntry {\n  if (W == 'type') r = typeof A + ':' + Object.prototype.toString.call(A);\n  if (W "
            + "== 'str') r = A.toString();\n  if (W == 'string') r = String(A);\n  if (W == 'ms') r = "
            + "String(A.getTime());\n  if (W == 'm') r = ['getEpochSeconds', 'getNanoSeconds', 'getScale', "
            + "'getTimezone', 'toISOString',\n    'toJSON', 'getTime'].map(function (n) { return n + '=' + typeof "
            + "A[n]; }).join(',');\n  if (W == 'es') r = String(A.getEpochSeconds());\n  if (W == 'ns') r = "
            + "String(A.getNanoSeconds());\n  if (W == 'sc') r = String(A.getScale());\n  if (W == 'tz') r = "
            + "String(A.getTimezone());\n  if (W == 'json') r = JSON.stringify(A);\n  if (W == 'iso') r = "
            + "A.toISOString();\n  if (W == 'own') r = Object.getOwnPropertyNames(A).join(',');\n  if (W == 'proto') r "
            + "= Object.getOwnPropertyNames(Object.getPrototypeOf(A)).sort().join(',');\n  if (W == 'plus') r = "
            + "String(A + 0);\n} catch (e) { r = 'E:' + e; }\nreturn String(r).replace(/-/g, '/');\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jv794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nreturn "
            + "(typeof V + ':' + JSON.stringify(V)).replace(/-/g, '/');\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jn794() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nvar names = "
            + "['parse', 'toJson', 'isArray', 'handler', 'snowflake', 'Snowflake', 'JN794', 'jn794', 'arguments', "
            + "'arrive', 'bytesOf', 'a0', 'result', 'had', 'saved', 'api', 'made', 'ran', 'refuse', "
            + "'globalThis'];\nvar out = [];\nfor (var i = 0; i < names.length; i++) { var t; try { t = eval('typeof ' "
            + "+ names[i]); } catch (e) { t = 'E'; } out.push(names[i] + '=' + t); }\nreturn out.join(',');\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jnp794() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nvar names = "
            + "['parse', 'toJson', 'isArray', 'handler', 'snowflake', 'Snowflake', 'JNP794', 'jnp794', 'arguments', "
            + "'arrive', 'bytesOf', 'a0', 'result', 'had', 'saved', 'api', 'made', 'ran', 'refuse', "
            + "'globalThis'];\nvar out = [];\nfor (var i = 0; i < names.length; i++) { var t; try { t = eval('typeof ' "
            + "+ names[i]); } catch (e) { t = 'E'; } out.push(names[i] + '=' + t); }\nreturn out.join(',');\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jnt794() RETURNS TABLE (v VARCHAR) LANGUAGE JAVASCRIPT AS $$\n{ "
            + "processRow: function (row, rowWriter, context) {},\n  finalize: function (rowWriter, context) {\n    "
            + "var names = ['parse', 'toJson', 'isArray', 'handler', 'snowflake', 'row', 'rowWriter', 'JNT794'];\n    "
            + "var out = [];\n    for (var i = 0; i < names.length; i++) { out.push(names[i] + '=' + eval('typeof ' + "
            + "names[i])); }\n    rowWriter.writeRow({V: out.join(',')}); } }\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jty794(v VARIANT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nvar r "
            + "= [];\nfor (var k in V) { var x = V[k]; r.push(k + '=' + typeof x + ':' + "
            + "Object.prototype.toString.call(x)); }\nreturn r.join(',');\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'",
                "Statement executed successfully."},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'type')",
                "object:[object Date]"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'str')",
                "Tue Jan 02 2024 18:20:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'string')",
                "Tue Jan 02 2024 18:20:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'ms')",
                "1704187230123"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'm')",
                "getEpochSeconds=undefined,getNanoSeconds=undefined,getScale=undefined,getTimezone=undefined,toISOStrin"
                + "g=function,toJSON=function,getTime=function"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'es')",
                "E:TypeError: A.getEpochSeconds is not a function"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'ns')",
                "E:TypeError: A.getNanoSeconds is not a function"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'sc')",
                "E:TypeError: A.getScale is not a function"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'tz')",
                "E:TypeError: A.getTimezone is not a function"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'json')",
                "\"2024/01/02T09:20:30.123Z\""},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'iso')",
                "2024/01/02T09:20:30.123Z"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'own')",
                ""},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'proto')",
                "constructor,getDate,getDay,getFullYear,getHours,getMilliseconds,getMinutes,getMonth,getSeconds,getTime"
                + ",getTimezoneOffset,getUTCDate,getUTCDay,getUTCFullYear,getUTCHours,getUTCMilliseconds,getUTCMinutes,"
                + "getUTCMonth,getUTCSeconds,getYear,setDate,setFullYear,setHours,setMilliseconds,setMinutes,setMonth,s"
                + "etSeconds,setTime,setUTCDate,setUTCFullYear,setUTCHours,setUTCMilliseconds,setUTCMinutes,setUTCMonth"
                + ",setUTCSeconds,setYear,toDateString,toGMTString,toISOString,toJSON,toLocaleDateString,toLocaleString"
                + ",toLocaleTimeString,toString,toTimeString,toUTCString,valueOf"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'plus')",
                "Tue Jan 02 2024 18:20:30 GMT+0900 (Japan Standard Time)0"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'type')",
                "object:[object Date]"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'str')",
                "Tue Jan 02 2024 10:20:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'ms')",
                "1704158430500"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'm')",
                "getEpochSeconds=undefined,getNanoSeconds=undefined,getScale=undefined,getTimezone=undefined,toISOStrin"
                + "g=function,toJSON=function,getTime=function"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'es')",
                "E:TypeError: A.getEpochSeconds is not a function"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'ns')",
                "E:TypeError: A.getNanoSeconds is not a function"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'sc')",
                "E:TypeError: A.getScale is not a function"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'tz')",
                "E:TypeError: A.getTimezone is not a function"},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'json')",
                "\"2024/01/02T01:20:30.500Z\""},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'type')",
                "object:[object Date]"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'str')",
                "Tue Jan 02 2024 10:20:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'ms')",
                "1704158430500"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'm')",
                "getEpochSeconds=undefined,getNanoSeconds=undefined,getScale=undefined,getTimezone=undefined,toISOStrin"
                + "g=function,toJSON=function,getTime=function"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'es')",
                "E:TypeError: A.getEpochSeconds is not a function"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'ns')",
                "E:TypeError: A.getNanoSeconds is not a function"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'sc')",
                "E:TypeError: A.getScale is not a function"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'tz')",
                "E:TypeError: A.getTimezone is not a function"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'json')",
                "\"2024/01/02T01:20:30.500Z\""},
            {"SELECT jd794('2024-01-02'::DATE, 'type')",
                "object:[object Date]"},
            {"SELECT jd794('2024-01-02'::DATE, 'str')",
                "Tue Jan 02 2024 00:00:00 GMT+0900 (Japan Standard Time)"},
            {"SELECT jd794('2024-01-02'::DATE, 'ms')",
                "1704121200000"},
            {"SELECT jd794('2024-01-02'::DATE, 'm')",
                "getEpochSeconds=undefined,getNanoSeconds=undefined,getScale=undefined,getTimezone=undefined,toISOStrin"
                + "g=function,toJSON=function,getTime=function"},
            {"SELECT jd794('2024-01-02'::DATE, 'json')",
                "\"2024/01/01T15:00:00.000Z\""},
            {"SELECT jtz794('2024-01-02 10:20:30 -05:30'::TIMESTAMP_TZ, 'str')",
                "Wed Jan 03 2024 00:50:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jtz794('2024-01-02 10:20:30 -05:30'::TIMESTAMP_TZ, 'tz')",
                "E:TypeError: A.getTimezone is not a function"},
            {"SELECT jtz794('2024-01-02 10:20:30.5'::TIMESTAMP_TZ(1), 'sc')",
                "E:TypeError: A.getScale is not a function"},
            {"SELECT jtz794('2024-01-02 10:20:30.5'::TIMESTAMP_TZ(1), 'str')",
                "Tue Jan 02 2024 10:20:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jtz794('2024-07-02 10:20:30'::TIMESTAMP_TZ, 'str')",
                "Tue Jul 02 2024 10:20:30 GMT+0900 (Japan Standard Time)"},
            {"SELECT jltz794('2024-01-02 10:20:30.123456789'::TIMESTAMP_LTZ(9), 'ns')",
                "E:TypeError: A.getNanoSeconds is not a function"},
            {"SELECT jntz794('1969-12-31 23:59:59.5'::TIMESTAMP_NTZ, 'es')",
                "E:TypeError: A.getEpochSeconds is not a function"},
            {"SELECT jntz794('1969-12-31 23:59:59.5'::TIMESTAMP_NTZ, 'ns')",
                "E:TypeError: A.getNanoSeconds is not a function"},
            {"SELECT jv794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "object:\"2024/01/02T01:11:12.000Z\""},
            {"SELECT jv794(ARRAY_CONSTRUCT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "object:[\"2024/01/02T01:11:12.000Z\"]"},
            {"SELECT jv794(OBJECT_CONSTRUCT('t', '2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ))",
                "object:{\"t\":\"2024/01/02T09:11:12.000Z\"}"},
            {"SELECT jty794(OBJECT_CONSTRUCT('l', '2024-01-02 10:11:12'::TIMESTAMP_LTZ, 'z', '2024-01-02 10:11:12 "
                + "+01:00'::TIMESTAMP_TZ, 'n', '2024-01-02 10:11:12'::TIMESTAMP_NTZ, 'd', '2024-01-02'::DATE))",
                "d=object:[object Date],l=object:[object Date],n=object:[object Date],z=object:[object Date]"},
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
            {"SELECT jv794(TO_VARIANT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "object:\"2024/01/02T10:11:12.000Z\""},
            {"SELECT jv794(ARRAY_CONSTRUCT('2024-01-02 10:11:12'::TIMESTAMP_LTZ))",
                "object:[\"2024/01/02T10:11:12.000Z\"]"},
            {"SELECT jv794(OBJECT_CONSTRUCT('t', '2024-01-02 10:11:12.5'::TIMESTAMP_LTZ))",
                "object:{\"t\":\"2024/01/02T10:11:12.500Z\"}"},
            {"SELECT jv794(TO_VARIANT('2024-01-02 10:11:12.123456'::TIMESTAMP_LTZ(6)))",
                "object:\"2024/01/02T10:11:12.123Z\""},
            {"SELECT jltz794('2024-01-02 10:20:30.5'::TIMESTAMP_LTZ, 'str')",
                "Tue Jan 02 2024 10:20:30 GMT+0000 (Coordinated Universal Time)"},
            {"SELECT jtz794('2024-01-02 10:20:30.123456789 +01:00'::TIMESTAMP_TZ(9), 'str')",
                "Tue Jan 02 2024 09:20:30 GMT+0000 (Coordinated Universal Time)"},
            {"SELECT jntz794('2024-01-02 10:20:30.5'::TIMESTAMP_NTZ, 'str')",
                "Tue Jan 02 2024 10:20:30 GMT+0000 (Coordinated Universal Time)"},
            {"SELECT jn794()",
                "parse=E,toJson=E,isArray=E,handler=E,snowflake=E,Snowflake=E,JN794=E,jn794=E,arguments=E,arrive=E,byte"
                + "sOf=E,a0=E,result=E,had=E,saved=E,api=E,made=E,ran=E,refuse=E,globalThis=E"},
            {"CALL jnp794()",
                "parse=E,toJson=E,isArray=E,handler=E,snowflake=E,Snowflake=E,JNP794=E,jnp794=E,arguments=E,arrive=E,by"
                + "tesOf=E,a0=E,result=E,had=E,saved=E,api=E,made=E,ran=E,refuse=E,globalThis=E"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
