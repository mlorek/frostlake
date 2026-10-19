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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The objects the account gives a JavaScript handler: {@code snowflake} with the members of its prototype, the
 * user exception it builds and the error it throws unredacted, {@code snowflakeLogger} and
 * {@code _c_snowflake_logger} with what their members answer, and {@code console} with the account's members.
 * A procedure's {@code snowflake} adds the statement methods. Every cell is live-verified.
 */
public class JavaScriptApiObjectsTest extends BaseDatabaseTest {

    /** An expression, and its text or what it throws. */
    private static final String[][] VALUES = {
            {"snowflake.getAccountParameterValue('TIMEZONE')",
                "THREW undefined: undefined / string"},
            {"snowflake.getAccountParameterValue('NOSUCH_PARAMETER')",
                "THREW undefined: undefined / string"},
            {"snowflake.getAccountParameterValue()",
                "THREW undefined: undefined / string"},
            {"snowflake.getAccountParameterValue('QUERY_TAG')",
                "THREW undefined: undefined / string"},
            {"snowflake.getAccountParameterValue('timezone')",
                "THREW undefined: undefined / string"},
            {"snowflake.getAccountParameterValue(5)",
                "THREW undefined: undefined / string"},
            {"typeof snowflake.SnowflakeUserException",
                "function"},
            {"(function(){ var e = new snowflake.SnowflakeUserException('m'); return [e.name, e.message, e instanceof Error, Object.getOwnPropertyNames(e).sort().join('/'), String(e), e.constructor.name, Object.getPrototypeOf(e) === Error.prototype].join('~'); })()",
                "SnowflakeUserError~m~true~_isCustomizableErrorFormat/keepErrorCodeFormatString/keepErrorMessage/keepStackTraceAndProc/message/stack~SnowflakeUserError: m~SnowflakeUserError~false"},
            {"(function(){ var e = new snowflake.SnowflakeUserException(); return [e.name, e.message].join('~'); })()",
                "SnowflakeUserError~"},
            {"(function(){ var e = snowflake.SnowflakeUserException('m'); return typeof e; })()",
                "object"},
            {"snowflake.log('info', 'x')",
                "undefined"},
            {"snowflake.log_info('x')",
                "undefined"},
            {"snowflake.log()",
                "undefined"},
            {"snowflake.log('nolevel', 'x')",
                "undefined"},
            {"snowflake.log('INFO', 'x')",
                "undefined"},
            {"snowflake.log_info()",
                "undefined"},
            {"snowflake.log_info({a: 1})",
                "undefined"},
            {"snowflake.record_exception_as_log_event(new Error('x'))",
                "undefined"},
            {"snowflake.record_exception_as_log_event()",
                "undefined"},
            {"snowflakeLogger.getMinLogLevel()",
                "100"},
            {"snowflakeLogger.log('info', 'x')",
                "undefined"},
            {"snowflakeLogger.logException(new Error('x'))",
                "undefined"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(1))",
                "{\"doubleValue\":1}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue('a'))",
                "{\"stringValue\":\"a\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue({a:1}))",
                "{\"kvlistValue\":{\"values\":[{\"key\":\"a\",\"value\":{\"doubleValue\":1}}]}}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(true))",
                "{\"boolValue\":true}"},
            {"JSON.stringify(snowflakeLogger.convertToArrayValue([1,'a']))",
                "[{\"doubleValue\":1},{\"stringValue\":\"a\"}]"},
            {"JSON.stringify(snowflakeLogger.convertToKvListValue({a:1}))",
                "[{\"key\":\"a\",\"value\":{\"doubleValue\":1}}]"},
            {"JSON.stringify(snowflakeLogger.levelToSeverity('info'))",
                "\"SEVERITY_NUMBER_UNSPECIFIED\""},
            {"JSON.stringify(snowflakeLogger.levelToSeverity('nolevel'))",
                "\"SEVERITY_NUMBER_UNSPECIFIED\""},
            {"JSON.stringify(snowflakeLogger.populateAttributes({a:1}))",
                "[{\"key\":\"a\",\"value\":{\"doubleValue\":1}}]"},
            {"JSON.stringify(snowflake)",
                "{}"},
            {"_c_snowflake_logger.get_min_log_level()",
                "100"},
            {"_c_snowflake_logger.get_trace_level()",
                "OFF"},
            {"_c_snowflake_logger.now_ms() > 0",
                "true"},
            {"_c_snowflake_logger.constructor.name",
                ""},
            {"Object.prototype.toString.call(_c_snowflake_logger)",
                "[object Object]"},
            {"Object.prototype.toString.call(console)",
                "[object console]"},
            {"Object.prototype.toString.call(snowflake)",
                "[object Object]"},
            {"console.log('x')",
                "undefined"},
            {"console.table([1])",
                "undefined"},
            {"console.trace('x')",
                "undefined"},
            {"console.timeStamp('x')",
                "undefined"},
            {"console.profile('x')",
                "undefined"},
            {"console.profileEnd('x')",
                "undefined"},
            {"console.dirxml(1)",
                "undefined"},
            {"console.assert(false, 'x')",
                "undefined"},
            {"console.count('x')",
                "undefined"},
            {"console.time('x')",
                "undefined"},
            {"console.timeEnd('x')",
                "undefined"},
            {"console.group('x')",
                "undefined"},
            {"String(console.log)",
                "function log() { [native code] }"},
            {"typeof console.context()",
                "object"},
            {"String(Object.getPrototypeOf(snowflake) === Snowflake.prototype)",
                "true"},
            {"String(snowflake)",
                "[object Object]"},
            {"String(snowflakeLogger)",
                "[object Object]"},
            {"Object.getOwnPropertyNames(Snowflake.prototype.log).join()",
                "length,name,prototype"},
            {"(function(){ var e = new snowflake.SnowflakeUserException('m'); return [e._isCustomizableErrorFormat, e.keepErrorCodeFormatString, e.keepErrorMessage, e.keepStackTraceAndProc].join('~'); })()",
                "true~false~true~false"},
            {"(function(){ var e = new snowflake.SnowflakeUserException(); return Object.getOwnPropertyNames(e).sort().join('/') + '~' + JSON.stringify(e.message); })()",
                "_isCustomizableErrorFormat/keepErrorCodeFormatString/keepErrorMessage/keepStackTraceAndProc/message/stack~undefined"},
            {"(function(){ try { snowflake.throwUnredactedError('m'); } catch (e) { return [e._isCustomizableErrorFormat, e.keepErrorCodeFormatString, e.keepErrorMessage, e.keepStackTraceAndProc, Object.getOwnPropertyNames(e).sort().join('/')].join('~'); } })()",
                "false~true~true~true~_isCustomizableErrorFormat/keepErrorCodeFormatString/keepErrorMessage/keepStackTraceAndProc/message/stack"},
            {"['INFO','ERROR','TRACE','DEBUG','WARN','FATAL','info','OFF'].map(function (l) { return snowflakeLogger.levelToSeverity(l); }).join()",
                "SEVERITY_NUMBER_INFO,SEVERITY_NUMBER_ERROR,SEVERITY_NUMBER_TRACE,SEVERITY_NUMBER_DEBUG,SEVERITY_NUMBER_WARN,SEVERITY_NUMBER_FATAL,SEVERITY_NUMBER_UNSPECIFIED,SEVERITY_NUMBER_UNSPECIFIED"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue([1,'a']))",
                "{\"arrayValue\":{\"values\":[{\"doubleValue\":1},{\"stringValue\":\"a\"}]}}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(null))",
                "{\"stringValue\":null}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(undefined))",
                "{\"stringValue\":null}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue({a:{b:[true]}}))",
                "{\"kvlistValue\":{\"values\":[{\"key\":\"a\",\"value\":{\"kvlistValue\":{\"values\":[{\"key\":\"b\",\"value\":{\"arrayValue\":{\"values\":[{\"boolValue\":true}]}}}]}}}]}}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(1.5))",
                "{\"doubleValue\":1.5}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType({a:1}))",
                "{\"kvlistValue\":{\"values\":[{\"key\":\"a\",\"value\":{\"doubleValue\":1}}]}}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType([1]))",
                "{\"arrayValue\":{\"values\":[{\"doubleValue\":1}]}}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType(5))",
                "{\"doubleValue\":5}"},
            {"String(snowflakeLogger.logHelper('INFO', 'x'))",
                "undefined"},
            {"String(snowflakeLogger.toProto({}, {}))",
                "[object Object]"},
            {"String(_c_snowflake_logger.get_parent_trace())",
                ""},
            {"String(_c_snowflake_logger.hex_to_base64('ff'))",
                "/w=="},
            {"Object.getOwnPropertyNames(console.context()).sort().join()",
                "assert,clear,count,countReset,debug,dir,dirxml,error,group,groupCollapsed,groupEnd,info,log,profile,profileEnd,table,time,timeEnd,timeLog,timeStamp,trace,warn"},
            {"(function(){ var e = new snowflake.SnowflakeUserException('m'); return Object.keys(e).join('/') + '~' + JSON.stringify(e) + '~' + Object.getOwnPropertyNames(e).join('/'); })()",
                "message/_isCustomizableErrorFormat/keepErrorMessage/keepStackTraceAndProc/keepErrorCodeFormatString~{\"message\":\"m\",\"_isCustomizableErrorFormat\":true,\"keepErrorMessage\":true,\"keepStackTraceAndProc\":false,\"keepErrorCodeFormatString\":false}~message/stack/_isCustomizableErrorFormat/keepErrorMessage/keepStackTraceAndProc/keepErrorCodeFormatString"},
            {"[5, null, {}, undefined, 'm', '', true].map(function (v) { var e = new snowflake.SnowflakeUserException(v); return typeof e.message + ':' + e.message + ':' + String(e); }).join('~')",
                "number:5:SnowflakeUserError: 5~object:null:SnowflakeUserError: null~object:[object Object]:SnowflakeUserError: [object Object]~undefined:undefined:SnowflakeUserError~string:m:SnowflakeUserError: m~string::SnowflakeUserError~boolean:true:SnowflakeUserError: true"},
            {"(function(){ try { snowflake.throwUnredactedError(5); } catch (e) { return [typeof e.message, e.message, String(e), Object.keys(e).join('/'), Object.getOwnPropertyNames(e).join('/'), JSON.stringify(e)].join('~'); } })()",
                "number~5~SnowflakeUserError: 5~message/_isCustomizableErrorFormat/keepErrorMessage/keepStackTraceAndProc/keepErrorCodeFormatString~message/stack/_isCustomizableErrorFormat/keepErrorMessage/keepStackTraceAndProc/keepErrorCodeFormatString~{\"message\":5,\"_isCustomizableErrorFormat\":false,\"keepErrorMessage\":true,\"keepStackTraceAndProc\":true,\"keepErrorCodeFormatString\":true}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType('s'))",
                "{\"stringValue\":\"s\"}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType(null))",
                "{\"stringValue\":null}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType(true))",
                "{\"boolValue\":true}"},
            {"JSON.stringify(snowflakeLogger.handleObjectType(undefined))",
                "{\"stringValue\":null}"},
            {"JSON.stringify(snowflakeLogger.convertToArrayValue([1, 'a']))",
                "[{\"doubleValue\":1},{\"stringValue\":\"a\"}]"},
            {"JSON.stringify(snowflakeLogger.convertToKvListValue({a: 1, b: 'x'}))",
                "[{\"key\":\"a\",\"value\":{\"doubleValue\":1}},{\"key\":\"b\",\"value\":{\"stringValue\":\"x\"}}]"},
            {"JSON.stringify(snowflakeLogger.populateAttributes({a: 1}))",
                "[{\"key\":\"a\",\"value\":{\"doubleValue\":1}}]"},
            {"JSON.stringify(snowflakeLogger.populateAttributes({}))",
                "[]"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(function () { }))",
                "{\"stringValue\":\"function () { }\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(''))",
                "{\"stringValue\":\"\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(false))",
                "{\"boolValue\":false}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(NaN))",
                "{\"doubleValue\":null}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(7))",
                "{\"doubleValue\":7}"},
            {"(function(){ var v = snowflakeLogger.convertToAnyValue(Symbol('s')); return Object.keys(v).join() + ':' + typeof v[Object.keys(v)[0]]; })()",
                "stringValue:string"},
            {"(function(){ var v = snowflakeLogger.convertToAnyValue(10n); return Object.keys(v).join() + ':' + typeof v[Object.keys(v)[0]]; })()",
                "doubleValue:bigint"},
            {"typeof snowflakeLogger.levelToSeverity()",
                "string"},
            {"String(snowflakeLogger.levelToSeverity(5))",
                "SEVERITY_NUMBER_UNSPECIFIED"},
            {"String(snowflakeLogger.log('INFO', 'x'))",
                "undefined"},
            {"String(snowflakeLogger.logException(new Error('x')))",
                "undefined"},
            {"String(snowflake.log('info', 'x'))",
                "undefined"},
            {"String(snowflake.log_info('x', {a: 1}))",
                "undefined"},
            {"String(snowflake.record_exception_as_log_event(new Error('x')))",
                "undefined"},
            {"String(_c_snowflake_logger.hex_to_base64(''))",
                ""},
            {"String(_c_snowflake_logger.hex_to_base64('FFFE'))",
                "//4="},
            {"String(_c_snowflake_logger.hex_to_base64('00010203'))",
                "AAECAw=="},
            {"typeof _c_snowflake_logger.get_parent_trace()",
                "string"},
            {"typeof _c_snowflake_logger.now_ms()",
                "number"},
            {"_c_snowflake_logger.get_min_log_level() + '/' + _c_snowflake_logger.get_trace_level()",
                "100/OFF"},
            {"typeof console.context().log + '/' + (console.context() === console) + '/' + (console.context('x') === console.context('x')) + '/' + Object.prototype.toString.call(console.context()) + '/' + String(console.context().log === console.log)",
                "function/false/false/[object Object]/false"},
            {"String(console.log('x')) + String(console.table([1])) + String(console.trace()) + String(console.dirxml(1)) + String(console.profile()) + String(console.timeStamp())",
                "undefinedundefinedundefinedundefinedundefinedundefined"},
            {"Object.getOwnPropertyNames(console).sort().join()",
                "assert,clear,context,count,countReset,debug,dir,dirxml,error,group,groupCollapsed,groupEnd,info,log,profile,profileEnd,table,time,timeEnd,timeLog,timeStamp,trace,warn"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(new (class A { constructor() { this.a = 1; } })()))",
                "{\"stringValue\":\"[object Object]\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(new Map([[1, 2]])))",
                "{\"stringValue\":\"[object Map]\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(new Error('x')))",
                "{\"stringValue\":\"Error: x\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(/re/))",
                "{\"stringValue\":\"/re/\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue([null, undefined, [1]]))",
                "{\"arrayValue\":{\"values\":[{\"stringValue\":null},{\"stringValue\":null},{\"arrayValue\":{\"values\":[{\"doubleValue\":1}]}}]}}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue({a: null, b: undefined, c: function () { }}))",
                "{\"kvlistValue\":{\"values\":[{\"key\":\"a\",\"value\":{\"stringValue\":null}},{\"key\":\"b\",\"value\":{\"stringValue\":null}},{\"key\":\"c\",\"value\":{\"stringValue\":\"function () { }\"}}]}}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(Infinity))",
                "{\"doubleValue\":null}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(new String('s')))",
                "{\"stringValue\":\"s\"}"},
            {"JSON.stringify(snowflakeLogger.convertToAnyValue(new Number(5)))",
                "{\"doubleValue\":5}"},
            {"JSON.stringify(snowflakeLogger.convertToKvListValue([7, 8]))",
                "[{\"key\":\"0\",\"value\":{\"doubleValue\":7}},{\"key\":\"1\",\"value\":{\"doubleValue\":8}}]"},
            {"JSON.stringify(snowflakeLogger.convertToKvListValue('ab'))",
                "[{\"key\":\"0\",\"value\":{\"stringValue\":\"a\"}},{\"key\":\"1\",\"value\":{\"stringValue\":\"b\"}}]"},
            {"JSON.stringify(snowflakeLogger.convertToArrayValue('ab'))",
                "[{\"stringValue\":\"a\"},{\"stringValue\":\"b\"}]"},
            {"JSON.stringify(snowflakeLogger.convertToArrayValue([]))",
                "[]"},
            {"JSON.stringify(snowflakeLogger.populateAttributes('ab'))",
                "[{\"key\":\"0\",\"value\":{\"stringValue\":\"a\"}},{\"key\":\"1\",\"value\":{\"stringValue\":\"b\"}}]"},
            {"JSON.stringify(snowflakeLogger.populateAttributes([5]))",
                "[{\"key\":\"0\",\"value\":{\"doubleValue\":5}}]"},
    };

    private static final String MEMBERS = "var out = []; Object.getOwnPropertyNames(o).sort().forEach(function (n) {"
        + " var v = o[n]; out.push(n + ':' + typeof v + (typeof v === 'function' ? '/' + v.length : '')); });"
        + " return out.join();";

    private static final String TRY = "function t(f){try{return String(f())}catch(e){return 'THREW '+(e&&e.name)+': '"
        + "+(e&&e.message)+' / '+(typeof e)}}\n";

    /** The only cell of a one-row answer. */
    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** Each expression, evaluated inside a handler, has the given text, or throws what the text tells. */
    private void assertValues(final String[][] cases) {
        for (int from = 0; from < cases.length; from += 12) {
            final int to = Math.min(cases.length, from + 12);
            final StringBuilder body = new StringBuilder(TRY).append("return [");
            for (int i = from; i < to; i++) {
                body.append(i > from ? ",\n" : "").append("t(function(){ return ").append(cases[i][0]).append("; })");
            }
            engine.execute("CREATE OR REPLACE FUNCTION ev() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$"
                + body.append("].join(' ;; ');") + "$$");
            final String[] cells = cell("SELECT ev()").split(" ;; ", -1);
            assertEquals(to - from, cells.length, String.join(" ;; ", cells));
            for (int i = from; i < to; i++) {
                assertEquals(cases[i][1], cells[i - from], cases[i][0]);
            }
        }
    }

    /** The own members of an object a handler sees, each with its kind and, for a function, its length. */
    private String members(final String object, final boolean procedure) {
        final String body = "var o = " + object + "; " + MEMBERS;
        if (procedure) {
            engine.execute("CREATE OR REPLACE PROCEDURE mp() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + body + "$$");
            return cell("CALL mp()");
        }
        engine.execute("CREATE OR REPLACE FUNCTION mf() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + body + "$$");
        return cell("SELECT mf()");
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql).getRows();
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void theApiObjectsAnswerAsTheAccountsDo() {
        assertValues(VALUES);
    }

    @Test
    public void theApiObjectsHaveTheAccountsMembers() {
        assertEquals("", members("snowflake", false));
        assertEquals("SnowflakeUserException:function/1,constructor:function/0,getAccountParameterValue:function/1,ingest_event_proto:function/2,log:function/2,log_debug:function/1,log_error:function/1,log_fatal:function/1,log_info:function/1,log_trace:function/1,log_warn:function/1,record_exception_as_log_event:function/1,throwUnredactedError:function/1",
            members("Object.getPrototypeOf(snowflake)", false));
        assertEquals("convertToAnyValue:function/1,convertToArrayValue:function/1,convertToKvListValue:function/1,getMinLogLevel:function/0,handleObjectType:function/1,ingest_event_proto:function/2,levelToSeverity:function/1,log:function/2,logException:function/1,logHelper:function/2,populateAttributes:function/1,toProto:function/2",
            members("snowflakeLogger", false));
        assertEquals("add_metric:function/0,add_span:function/0,get_min_log_level:function/0,get_parent_trace:function/0,get_trace_level:function/0,hex_to_base64:function/0,ingest_event_proto:function/0,log_proto:function/0,now_ms:function/0",
            members("_c_snowflake_logger", false));
        assertEquals("assert:function/0,clear:function/0,context:function/1,count:function/0,countReset:function/0,debug:function/0,dir:function/0,dirxml:function/0,error:function/0,group:function/0,groupCollapsed:function/0,groupEnd:function/0,info:function/0,log:function/0,profile:function/0,profileEnd:function/0,table:function/0,time:function/0,timeEnd:function/0,timeLog:function/0,timeStamp:function/0,trace:function/0,warn:function/0",
            members("console", false));
    }

    @Test
    public void aProceduresApiObjectAddsTheStatementMethods() {
        assertEquals("SnowflakeUserException:function/1,constructor:function/0,createStatement:function/1,execute:function/2,executeAsync:function/2,getAccountParameterValue:function/1,ingest_event_proto:function/2,log:function/2,log_debug:function/1,log_error:function/1,log_fatal:function/1,log_info:function/1,log_trace:function/1,log_warn:function/1,record_exception_as_log_event:function/1,throwUnredactedError:function/1",
            members("Object.getPrototypeOf(snowflake)", true));
        engine.execute("CREATE OR REPLACE PROCEDURE ea() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var r = snowflake.executeAsync({sqlText: 'select 7 as a'}); r.next(); return String(r.getColumnValue(1));$$");
        assertEquals("7", cell("CALL ea()"));
    }

    @Test
    public void aUserExceptionReachesSqlAsItsMessageAndAnUnredactedErrorAsAnError() {
        engine.execute("CREATE OR REPLACE FUNCTION ue() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$throw new snowflake.SnowflakeUserException('boom');$$");
        assertRefused("SELECT ue()", "boom");
        engine.execute("CREATE OR REPLACE FUNCTION ur() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$snowflake.throwUnredactedError('boom');$$");
        assertRefused("SELECT ur()",
            "JavaScript execution error: Uncaught SnowflakeUserError: boom in UR at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nUR line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION up() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$try { snowflake.getAccountParameterValue('TIMEZONE'); return 'read'; } catch (e) { return typeof e + ': ' + e; }$$");
        assertEquals("string: Invalid method getAccountParameterValue", cell("SELECT up()"));
    }
}
