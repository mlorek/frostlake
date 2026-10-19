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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The members of a JavaScript procedure's result set and statement (live-verified): the column getters by number
 * or name — {@code getColumnName}, {@code getColumnType}, {@code getColumnSqlType}, {@code getColumnScale},
 * {@code isColumnNullable}, the ten {@code isColumn…} kinds, {@code getColumnDescription} — before a row is read as
 * after, {@code getColumnValueAsString}, the query's id, the rows a DML statement wrote, a statement's status, and
 * the refusals of a column the result does not have and of a statement not executed yet. Each procedure prints
 * every member's answer on a line of its own, {@code label=type:value} or {@code label!code/state message}.
 *
 * <p>Not mirrored: a column's nullability is told only where the engine knows it (a column read as it is, a
 * constant), where the account works it out for any expression; and the account's {@code isDml},
 * {@code getOutParameterValues} and {@code getColumnValBoxedType} of a result set fail as calls of functions it does
 * not have, where the engine answers the first two as the statement's do.
 */
public class JavaScriptResultSetMembersTest extends MeasuredCellsTestSupport {

    private static final String RS_META =
        "CREATE OR REPLACE PROCEDURE rs_meta() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = []; var t "
        + "= function (label, f) { try { var v = f(); out.push(label + '=' + (v !== null && typeof v === 'objec"
        + "t' ? 'object:' + JSON.stringify(v) : typeof v + ':' + String(v))); } catch (e) { out.push(label + '!"
        + "' + (e && e.code !== undefined ? e.code + '/' + e.state + ' ' : '') + (e && e.message !== undefined "
        + "? e.message : String(e))); } }; var rs = snowflake.execute({sqlText: \"SELECT 1 AS a, 1.5::FLOAT AS "
        + "f, 'txt' AS s, TRUE AS t, '2024-03-05'::DATE AS d, NULL AS n, PARSE_JSON('{\\\"k\\\":1}') AS v, ARRA"
        + "Y_CONSTRUCT(1) AS arr, TO_BINARY('FF', 'HEX') AS b, '10:11:12'::TIME AS tm, '2024-03-05 10:11:12'::T"
        + "IMESTAMP_NTZ AS ts, OBJECT_CONSTRUCT('a', 1) AS o, 12.34::NUMBER(10,2) AS dec, 'x'::VARCHAR(5) AS v5"
        + ", '2024-03-05 10:11:12 +01:00'::TIMESTAMP_TZ AS tz\"}); t('count', function () { return rs.getColumn"
        + "Count(); }); for (var i = 0; i <= 16; i++) { (function (i) { t('name' + i, function () { return rs.g"
        + "etColumnName(i); }); t('type' + i, function () { return rs.getColumnType(i); }); t('sql' + i, functi"
        + "on () { return rs.getColumnSqlType(i); }); t('scale' + i, function () { return rs.getColumnScale(i);"
        + " }); t('nullable' + i, function () { return rs.isColumnNullable(i); }); t('kinds' + i, function () {"
        + " return [rs.isColumnArray(i), rs.isColumnBinary(i), rs.isColumnBoolean(i), rs.isColumnDate(i), rs.is"
        + "ColumnNumber(i), rs.isColumnObject(i), rs.isColumnText(i), rs.isColumnTime(i), rs.isColumnTimestamp("
        + "i), rs.isColumnVariant(i)].map(function (b) { return b ? 1 : 0; }).join(''); }); })(i); } for (var i"
        + " = 0; i <= 2; i++) { (function (i) { t('boxed' + i, function () { return rs.getColumnValBoxedType(i)"
        + "; }); t('desc' + i, function () { return rs.getColumnDescription(i); }); })(i); } return out.join('"
        + "\\n'); $$";

    private static final String RS_VALUES =
        "CREATE OR REPLACE PROCEDURE rs_values() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = []; var "
        + "t = function (label, f) { try { var v = f(); out.push(label + '=' + (v !== null && typeof v === 'obj"
        + "ect' ? 'object:' + JSON.stringify(v) : typeof v + ':' + String(v))); } catch (e) { out.push(label + "
        + "'!' + (e && e.code !== undefined ? e.code + '/' + e.state + ' ' : '') + (e && e.message !== undefine"
        + "d ? e.message : String(e))); } }; var rs = snowflake.execute({sqlText: \"SELECT 1 AS a, 1.5::FLOAT A"
        + "S f, 'txt' AS s, TRUE AS t, '2024-03-05'::DATE AS d, NULL AS n, PARSE_JSON('{\\\"k\\\":1}') AS v, AR"
        + "RAY_CONSTRUCT(1) AS arr, TO_BINARY('FF', 'HEX') AS b, '10:11:12'::TIME AS tm, '2024-03-05 10:11:12':"
        + ":TIMESTAMP_NTZ AS ts, OBJECT_CONSTRUCT('a', 1) AS o, 12.34::NUMBER(10,2) AS dec, 'x'::VARCHAR(5) AS "
        + "v5, '2024-03-05 10:11:12 +01:00'::TIMESTAMP_TZ AS tz\"}); t('strFirst', function () { return rs.getC"
        + "olumnValueAsString(1); }); t('rows', function () { return rs.getRowCount(); }); t('affected', functi"
        + "on () { return rs.getNumRowsAffected(); }); t('sqlcode', function () { return rs.getSqlcode(); }); t"
        + "('dml', function () { return rs.isDml(); }); t('qid', function () { var q = rs.getQueryId(); return "
        + "typeof q + ':' + (q ? q.length : 0); }); t('out', function () { return rs.getOutParameterValues(); }"
        + "); rs.next(); for (var i = 1; i <= 15; i++) { (function (i) { t('str' + i, function () { return rs.g"
        + "etColumnValueAsString(i); }); })(i); } t('strName', function () { return rs.getColumnValueAsString('"
        + "S'); }); t('strNone', function () { return rs.getColumnValueAsString(99); }); t('rows', function () "
        + "{ return rs.getRowCount(); }); t('affected', function () { return rs.getNumRowsAffected(); }); t('sq"
        + "lcode', function () { return rs.getSqlcode(); }); t('dml', function () { return rs.isDml(); }); t('q"
        + "id', function () { var q = rs.getQueryId(); return typeof q + ':' + (q ? q.length : 0); }); t('out',"
        + " function () { return rs.getOutParameterValues(); }); return out.join('\\n'); $$";

    private static final String RS_DML =
        "CREATE OR REPLACE PROCEDURE rs_dml() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = []; var t ="
        + " function (label, f) { try { var v = f(); out.push(label + '=' + (v !== null && typeof v === 'object"
        + "' ? 'object:' + JSON.stringify(v) : typeof v + ':' + String(v))); } catch (e) { out.push(label + '!'"
        + " + (e && e.code !== undefined ? e.code + '/' + e.state + ' ' : '') + (e && e.message !== undefined ?"
        + " e.message : String(e))); } }; var rs = snowflake.execute({sqlText: \"INSERT INTO js204 VALUES (4, '"
        + "d'), (5, 'e')\"}); t('count', function () { return rs.getColumnCount(); }); t('name1', function () {"
        + " return rs.getColumnName(1); }); t('type1', function () { return rs.getColumnType(1); }); t('rows', "
        + "function () { return rs.getRowCount(); }); t('affected', function () { return rs.getNumRowsAffected("
        + "); }); t('sqlcode', function () { return rs.getSqlcode(); }); t('dml', function () { return rs.isDml"
        + "(); }); t('qid', function () { var q = rs.getQueryId(); return typeof q + ':' + (q ? q.length : 0); "
        + "}); t('out', function () { return rs.getOutParameterValues(); }); t('next', function () { return rs."
        + "next(); }); t('val1', function () { return rs.getColumnValue(1); }); var u = snowflake.execute({sqlT"
        + "ext: \"UPDATE js204 SET s = 'z' WHERE k > 3\"}); t('upd.name1', function () { return u.getColumnName"
        + "(1); }); t('upd.name2', function () { return u.getColumnName(2); }); t('upd.affected', function () {"
        + " return u.getNumRowsAffected(); }); t('upd.dml', function () { return u.isDml(); }); var d = snowfla"
        + "ke.execute({sqlText: \"DELETE FROM js204 WHERE k > 3\"}); t('del.name1', function () { return d.getC"
        + "olumnName(1); }); t('del.affected', function () { return d.getNumRowsAffected(); }); var c = snowfla"
        + "ke.execute({sqlText: \"CREATE OR REPLACE TABLE js204b (a INT)\"}); t('ddl.name1', function () { retu"
        + "rn c.getColumnName(1); }); t('ddl.dml', function () { return c.isDml(); }); t('ddl.affected', functi"
        + "on () { return c.getNumRowsAffected(); }); t('ddl.rows', function () { return c.getRowCount(); }); r"
        + "eturn out.join('\\n'); $$";

    private static final String ST_BEFORE =
        "CREATE OR REPLACE PROCEDURE st_before() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = []; var "
        + "t = function (label, f) { try { var v = f(); out.push(label + '=' + (v !== null && typeof v === 'obj"
        + "ect' ? 'object:' + JSON.stringify(v) : typeof v + ':' + String(v))); } catch (e) { out.push(label + "
        + "'!' + (e && e.code !== undefined ? e.code + '/' + e.state + ' ' : '') + (e && e.message !== undefine"
        + "d ? e.message : String(e))); } }; var st = snowflake.createStatement({sqlText: 'SELECT k, s FROM js2"
        + "04 ORDER BY k'}); t('sql', function () { return st.getSqlText(); }); t('qid', function () { return s"
        + "t.getQueryId(); }); t('status', function () { return st.getStatus(); }); t('rows', function () { ret"
        + "urn st.getRowCount(); }); t('count', function () { return st.getColumnCount(); }); t('name1', functi"
        + "on () { return st.getColumnName(1); }); t('type1', function () { return st.getColumnType(1); }); t('"
        + "dml', function () { return st.isDml(); }); t('affected', function () { return st.getNumRowsAffected("
        + "); }); t('inserted', function () { return st.getNumRowsInserted(); }); t('req', function () { var q "
        + "= st.getRequestId(); return typeof q + ':' + (q ? q.length : 0); }); t('stid', function () { var q ="
        + " st.getStatementId(); return typeof q + ':' + String(q).length; }); t('persisted', function () { ret"
        + "urn st.getPersistedStatus(); }); t('stack', function () { return st.getProcedureStack(); }); t('out'"
        + ", function () { return st.getOutParameterValues(); }); t('wait', function () { return st.wait(); });"
        + " t('cancel', function () { return st.cancel(); }); return out.join('\\n'); $$";

    private static final String ST_AFTER =
        "CREATE OR REPLACE PROCEDURE st_after() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = []; var t"
        + " = function (label, f) { try { var v = f(); out.push(label + '=' + (v !== null && typeof v === 'obje"
        + "ct' ? 'object:' + JSON.stringify(v) : typeof v + ':' + String(v))); } catch (e) { out.push(label + '"
        + "!' + (e && e.code !== undefined ? e.code + '/' + e.state + ' ' : '') + (e && e.message !== undefined"
        + " ? e.message : String(e))); } }; var st = snowflake.createStatement({sqlText: 'SELECT k, s FROM js20"
        + "4 ORDER BY k'}); var rs = st.execute(); t('status', function () { return st.getStatus(); }); t('rows"
        + "', function () { return st.getRowCount(); }); t('qid', function () { var q = st.getQueryId(); return"
        + " typeof q + ':' + (q ? q.length : 0); }); t('sameqid', function () { return st.getQueryId() === rs.g"
        + "etQueryId(); }); t('dml', function () { return st.isDml(); }); t('affected', function () { return st"
        + ".getNumRowsAffected(); }); t('inserted', function () { return st.getNumRowsInserted(); }); t('update"
        + "d', function () { return st.getNumRowsUpdated(); }); t('deleted', function () { return st.getNumRows"
        + "Deleted(); }); t('dup', function () { return st.getNumDuplicateRowsUpdated(); }); t('persisted', fun"
        + "ction () { return st.getPersistedStatus(); }); t('stack', function () { return st.getProcedureStack("
        + "); }); t('wait', function () { return st.wait(); }); t('out', function () { return st.getOutParamete"
        + "rValues(); }); for (var i = 0; i <= 2; i++) { (function (i) { t('name' + i, function () { return st."
        + "getColumnName(i); }); t('type' + i, function () { return st.getColumnType(i); }); t('sql' + i, funct"
        + "ion () { return st.getColumnSqlType(i); }); t('scale' + i, function () { return st.getColumnScale(i)"
        + "; }); t('nullable' + i, function () { return st.isColumnNullable(i); }); t('kinds' + i, function () "
        + "{ return [st.isColumnArray(i), st.isColumnBinary(i), st.isColumnBoolean(i), st.isColumnDate(i), st.i"
        + "sColumnNumber(i), st.isColumnObject(i), st.isColumnText(i), st.isColumnTime(i), st.isColumnTimestamp"
        + "(i), st.isColumnVariant(i)].map(function (b) { return b ? 1 : 0; }).join(''); }); })(i); } var ins ="
        + " snowflake.createStatement({sqlText: \"INSERT INTO js204 VALUES (6, 'f')\"}); ins.execute(); t('ins."
        + "dml', function () { return ins.isDml(); }); t('ins.affected', function () { return ins.getNumRowsAff"
        + "ected(); }); t('ins.inserted', function () { return ins.getNumRowsInserted(); }); t('ins.updated', f"
        + "unction () { return ins.getNumRowsUpdated(); }); t('ins.rows', function () { return ins.getRowCount("
        + "); }); t('ins.count', function () { return ins.getColumnCount(); }); var up = snowflake.createStatem"
        + "ent({sqlText: \"UPDATE js204 SET s = 'y' WHERE k = 6\"}); up.execute(); t('up.updated', function () "
        + "{ return up.getNumRowsUpdated(); }); t('up.affected', function () { return up.getNumRowsAffected(); "
        + "}); t('up.dup', function () { return up.getNumDuplicateRowsUpdated(); }); var de = snowflake.createS"
        + "tatement({sqlText: 'DELETE FROM js204 WHERE k = 6'}); de.execute(); t('de.deleted', function () { re"
        + "turn de.getNumRowsDeleted(); }); t('de.affected', function () { return de.getNumRowsAffected(); }); "
        + "return out.join('\\n'); $$";

    private static final String RS_ERRORS =
        "CREATE OR REPLACE PROCEDURE rs_errors() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var out = []; var "
        + "t = function (label, f) { try { var v = f(); out.push(label + '=' + (v !== null && typeof v === 'obj"
        + "ect' ? 'object:' + JSON.stringify(v) : typeof v + ':' + String(v))); } catch (e) { out.push(label + "
        + "'!' + (e && e.code !== undefined ? e.code + '/' + e.state + ' ' : '') + (e && e.message !== undefine"
        + "d ? e.message : String(e))); } }; var rs = snowflake.execute({sqlText: 'SELECT k FROM js204 ORDER BY"
        + " k'}); t('val0', function () { return rs.getColumnValue(1); }); t('str0', function () { return rs.ge"
        + "tColumnValueAsString(1); }); t('scale0', function () { return rs.getColumnScale(0); }); t('name9', f"
        + "unction () { return rs.getColumnName(9); }); t('typeName', function () { return rs.getColumnType('K'"
        + "); }); t('sqlName', function () { return rs.getColumnSqlType('K'); }); t('nullName', function () { r"
        + "eturn rs.isColumnNullable('K'); }); t('numName', function () { return rs.isColumnNumber('K'); }); re"
        + "turn out.join('\\n'); $$";

    private static final String[] RS_META_ANSWERS = {
        "count=number:15",
        "name0!100183/P0000 Given column name/index does not exist: 0",
        "type0!100183/P0000 Given column name/index does not exist: 0",
        "sql0!100183/P0000 Given column name/index does not exist: 0",
        "scale0!100183/P0000 Given column name/index does not exist: 0",
        "nullable0!100183/P0000 Given column name/index does not exist: 0",
        "kinds0!100183/P0000 Given column name/index does not exist: 0",
        "name1=string:A",
        "type1=string:number",
        "sql1=string:FIXED",
        "scale1=number:0",
        "nullable1=boolean:false",
        "kinds1=string:0000100000",
        "name2=string:F",
        "type2=string:number",
        "sql2=string:REAL",
        "scale2=number:0",
        "nullable2=boolean:false",
        "kinds2=string:0000100000",
        "name3=string:S",
        "type3=string:string",
        "sql3=string:TEXT",
        "scale3=number:0",
        "nullable3=boolean:false",
        "kinds3=string:0000001000",
        "name4=string:T",
        "type4=string:boolean",
        "sql4=string:BOOLEAN",
        "scale4=number:0",
        "kinds4=string:0010000000",
        "name5=string:D",
        "type5=string:date",
        "sql5=string:DATE",
        "scale5=number:0",
        "nullable5=boolean:false",
        "kinds5=string:0001000000",
        "name6=string:N",
        "type6=string:string",
        "sql6=string:TEXT",
        "scale6=number:0",
        "kinds6=string:0000001000",
        "name7=string:V",
        "type7=string:json",
        "sql7=string:VARIANT",
        "scale7=number:0",
        "kinds7=string:0000000001",
        "name8=string:ARR",
        "type8=string:json",
        "sql8=string:ARRAY",
        "scale8=number:0",
        "kinds8=string:1000000001",
        "name9=string:B",
        "type9=string:buffer",
        "sql9=string:BINARY",
        "scale9=number:0",
        "nullable9=boolean:false",
        "kinds9=string:0100000000",
        "name10=string:TM",
        "type10=string:invalid",
        "sql10=string:TIME",
        "scale10=number:9",
        "nullable10=boolean:false",
        "kinds10=string:0000000100",
        "name11=string:TS",
        "type11=string:date",
        "sql11=string:TIMESTAMP_NTZ",
        "scale11=number:9",
        "nullable11=boolean:false",
        "kinds11=string:0000000010",
        "name12=string:O",
        "type12=string:json",
        "sql12=string:OBJECT",
        "scale12=number:0",
        "kinds12=string:0000010001",
        "name13=string:DEC",
        "type13=string:number",
        "sql13=string:FIXED",
        "scale13=number:2",
        "nullable13=boolean:false",
        "kinds13=string:0000100000",
        "name14=string:V5",
        "type14=string:string",
        "sql14=string:TEXT",
        "scale14=number:0",
        "nullable14=boolean:false",
        "kinds14=string:0000001000",
        "name15=string:TZ",
        "type15=string:date",
        "sql15=string:TIMESTAMP_TZ",
        "scale15=number:9",
        "nullable15=boolean:false",
        "kinds15=string:0000000010",
        "name16!100183/P0000 Given column name/index does not exist: 16",
        "type16!100183/P0000 Given column name/index does not exist: 16",
        "sql16!100183/P0000 Given column name/index does not exist: 16",
        "scale16!100183/P0000 Given column name/index does not exist: 16",
        "nullable16!100183/P0000 Given column name/index does not exist: 16",
        "kinds16!100183/P0000 Given column name/index does not exist: 16",
        "desc0!100183/P0000 Given column name/index does not exist: 0",
        "desc1=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":1,\"dbType\":0}",
        "desc2=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":1}"
    };

    private static final String[] RS_VALUES_ANSWERS = {
        "strFirst!100183/P0000 ResultSet is empty or not prepared (call next() first).",
        "rows=number:1",
        "affected=number:0",
        "sqlcode=number:0",
        "qid=string:string:36",
        "str1=string:1",
        "str2=string:1.5",
        "str3=string:txt",
        "str4=string:true",
        "str6=string:null",
        "str7=string:{\"k\":1}",
        "str8=string:[1]",
        "str9=string:{\"0\":255}",
        "str10=string:10:11:12.000000000",
        "str12=string:{\"a\":1}",
        "str13=string:12.34",
        "str14=string:x",
        "strName=string:txt",
        "strNone!100183/P0000 Given column name/index does not exist: 99",
        "rows=number:1",
        "affected=number:0",
        "sqlcode=number:0",
        "qid=string:string:36"
    };

    private static final String[] RS_DML_ANSWERS = {
        "count=number:1",
        "name1=string:number of rows inserted",
        "type1=string:number",
        "rows=number:1",
        "affected=number:2",
        "sqlcode=number:0",
        "qid=string:string:36",
        "next=boolean:true",
        "val1=number:2",
        "upd.name1=string:number of rows updated",
        "upd.name2=string:number of multi-joined rows updated",
        "upd.affected=number:2",
        "del.name1=string:number of rows deleted",
        "del.affected=number:2",
        "ddl.name1=string:status",
        "ddl.affected=number:0",
        "ddl.rows=number:1"
    };

    private static final String[] ST_BEFORE_ANSWERS = {
        "sql=string:SELECT k, s FROM js204 ORDER BY k",
        "qid!100183/P0000 Statement is not executed yet.",
        "status=string:initialized",
        "rows!100183/P0000 Statement is not executed yet.",
        "count!100183/P0000 Statement is not executed yet.",
        "name1!100183/P0000 Statement is not executed yet.",
        "type1!100183/P0000 Statement is not executed yet.",
        "dml!100183/P0000 Statement is not executed yet.",
        "affected!100183/P0000 Statement is not executed yet.",
        "inserted!100183/P0000 Statement is not executed yet.",
        "req=string:undefined:0",
        "stid=string:undefined:9",
        "persisted=string:initialized",
        "stack!100183/P0000 Statement is not executed yet.",
        "out!100183/P0000 Statement is not executed yet.",
        "wait=undefined:undefined",
        "cancel=undefined:undefined"
    };

    private static final String[] ST_AFTER_ANSWERS = {
        "status=string:success",
        "rows=number:3",
        "qid=string:string:36",
        "sameqid=boolean:true",
        "dml=boolean:false",
        "affected=number:0",
        "inserted=number:0",
        "updated=number:0",
        "deleted=number:0",
        "dup=number:0",
        "persisted=string:success",
        "stack=string:",
        "wait=undefined:undefined",
        "out=object:{}",
        "name0!100183/P0000 Given column name/index does not exist: 0",
        "type0!100183/P0000 Given column name/index does not exist: 0",
        "sql0!100183/P0000 Given column name/index does not exist: 0",
        "scale0!100183/P0000 Given column name/index does not exist: 0",
        "nullable0!100183/P0000 Given column name/index does not exist: 0",
        "kinds0!100183/P0000 Given column name/index does not exist: 0",
        "name1=string:K",
        "type1=string:number",
        "sql1=string:FIXED",
        "scale1=number:0",
        "nullable1=boolean:true",
        "kinds1=string:0000100000",
        "name2=string:S",
        "type2=string:string",
        "sql2=string:TEXT",
        "scale2=number:0",
        "nullable2=boolean:true",
        "kinds2=string:0000001000",
        "ins.dml=boolean:true",
        "ins.affected=number:1",
        "ins.inserted=number:1",
        "ins.updated=number:0",
        "ins.rows=number:1",
        "ins.count=number:1",
        "up.updated=number:1",
        "up.affected=number:1",
        "up.dup=number:0",
        "de.deleted=number:1",
        "de.affected=number:1"
    };

    private static final String[] RS_ERRORS_ANSWERS = {
        "val0!100183/P0000 ResultSet is empty or not prepared (call next() first).",
        "str0!100183/P0000 ResultSet is empty or not prepared (call next() first).",
        "scale0!100183/P0000 Given column name/index does not exist: 0",
        "name9!100183/P0000 Given column name/index does not exist: 9",
        "typeName=string:number",
        "sqlName=string:FIXED",
        "nullName=boolean:true",
        "numName=boolean:true"
    };

    /** Every column's description, and its value as text once the row is read and past the last row. */
    private static final String RS_TEXTS = "CREATE OR REPLACE PROCEDURE rs_texts() RETURNS VARCHAR LANGUAGE JAVASCRIPT"
        + " AS $$ " + RS_META.substring(RS_META.indexOf("var out"), RS_META.indexOf("t('count'"))
        + "rs.next(); for (var i = 1; i <= 15; i++) { (function (i) { "
        + "t('str' + i, function () { return rs.getColumnValueAsString(i); }); "
        + "t('desc' + i, function () { return rs.getColumnDescription(i); }); })(i); } "
        + "t('more', function () { return rs.next(); }); "
        + "t('afterEnd', function () { return rs.getColumnValueAsString(1); }); return out.join('\\n'); $$";

    @BeforeEach
    public void createTheTable() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE js204 (k INT, s VARCHAR)", "Table JS204 successfully created."},
            {"INSERT INTO js204 VALUES (1, 'a'), (2, 'b'), (3, 'c')", "3"},
        });
    }

    /** Asserts a procedure answers each of the measured lines, whatever else it prints. */
    private void assertLines(final String create, final String procedure, final String... measured) {
        answer(create);
        final String answered = answer("CALL " + procedure + "()");
        final Set<String> lines = new HashSet<>(Arrays.asList(answered.split(" \\| ")));
        for (final String line : measured) {
            assertTrue(lines.contains(line), line + " in " + answered);
        }
    }

    @Test
    public void aResultSetTellsItsColumnsBeforeARowIsRead() {
        assertLines(RS_META, "rs_meta", RS_META_ANSWERS);
    }

    @Test
    public void aResultSetTellsTheQueryAndItsValuesAsText() {
        assertLines(RS_VALUES, "rs_values", RS_VALUES_ANSWERS);
        assertLines(RS_TEXTS, "rs_texts",
            "str5=string:2024-03-05 00:00:00",
            "str11=string:2024-03-05 10:11:12.000000000",
            "str15=string:2024-03-05 10:11:12.000000000 +01:00",
            "desc1=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":1,\"dbType\":0}",
            "desc4=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":12}",
            "desc5=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":3}",
            "desc7=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":7}",
            "desc8=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":9}",
            "desc9=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":10}",
            "desc10=object:{\"sqlType\":\"\",\"scale\":9,\"precision\":0,\"dbType\":11}",
            "desc11=object:{\"sqlType\":\"\",\"scale\":9,\"precision\":0,\"dbType\":5}",
            "desc12=object:{\"sqlType\":\"\",\"scale\":0,\"precision\":0,\"dbType\":8}",
            "desc13=object:{\"sqlType\":\"\",\"scale\":2,\"precision\":10,\"dbType\":0}",
            "desc15=object:{\"sqlType\":\"\",\"scale\":9,\"precision\":0,\"dbType\":6}",
            "more=boolean:false", "afterEnd=string:null");
    }

    @Test
    public void aDmlResultCountsTheRowsWritten() {
        assertLines(RS_DML, "rs_dml", RS_DML_ANSWERS);
    }

    @Test
    public void aStatementRefusesWhatItHasNotRunYet() {
        assertLines(ST_BEFORE, "st_before", ST_BEFORE_ANSWERS);
    }

    @Test
    public void aStatementTellsItsRunAndWhatItWrote() {
        assertLines(ST_AFTER, "st_after", ST_AFTER_ANSWERS);
    }

    @Test
    public void aMissingColumnOrRowIsRefused() {
        assertLines(RS_ERRORS, "rs_errors", RS_ERRORS_ANSWERS);
    }

    @Test
    public void aFailedStatementIsFramedAndMarkedAnError() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE js_st() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var st = "
                + "snowflake.createStatement({sqlText: 'SELECT 1/0'}); st.execute(); return 'x'; $$",
                "Function JS_ST successfully created."},
            {"CALL js_st()", "REFUSED Execution error in store procedure JS_ST: | Division by zero"
                + " | At Statement.execute, line 1 position 65 | "},
            {"CREATE OR REPLACE PROCEDURE js_st_status() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var st = "
                + "snowflake.createStatement({sqlText: 'SELECT 1/0'}); try { st.execute(); } catch (e) { } "
                + "return st.getStatus() + '|' + st.getPersistedStatus(); $$",
                "Function JS_ST_STATUS successfully created."},
            {"CALL js_st_status()", "error|error"},
            {"CREATE OR REPLACE PROCEDURE js_inner() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "snowflake.execute({sqlText: 'SELECT 1/0'}); return 'x'; $$",
                "Function JS_INNER successfully created."},
            {"CREATE OR REPLACE PROCEDURE js_outer() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "snowflake.execute({sqlText: 'CALL js_inner()'}); return 'y'; $$",
                "Function JS_OUTER successfully created."},
            {"CALL js_outer()", "REFUSED Execution error in store procedure JS_OUTER: | Execution error in store"
                + " procedure JS_INNER: | Division by zero | At Snowflake.execute, line 1 position 11 |  | At"
                + " Snowflake.execute, line 1 position 11 | "},
        });
    }

    /** The account's result set fails these as functions it lacks; the engine answers as a statement does. */
    @Test
    public void aResultSetTellsWhetherItsStatementWroteRows() {
        assertLines(RS_DML, "rs_dml", "dml=boolean:true", "upd.dml=boolean:true", "ddl.dml=boolean:false",
            "out=object:{}");
    }
}
