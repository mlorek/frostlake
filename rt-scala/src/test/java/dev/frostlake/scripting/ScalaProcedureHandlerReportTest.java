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
 * What a Scala procedure's handler raises reaches SQL as a Scala function's does: {@code User Error Report:}, the
 * exception, its frames in {@code ScalaUDF} - an object's method under its forwarder - and its cause, which a
 * block's handler reads as SQLCODE 100315 and SQLSTATE P0000; a statement the handler runs through its session that
 * fails is the driver's exception, reported from the session's {@code collect}. A handler class the body does not
 * hold is refused when the routine is created, as the class loader of Java 11 reports it. Every answer is
 * live-verified.
 */
public class ScalaProcedureHandlerReportTest extends MeasuredCellsTestSupport {

    private static final String HEAD = "CREATE OR REPLACE PROCEDURE %s RETURNS %s LANGUAGE SCALA RUNTIME_VERSION = "
        + "'2.12' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = '%s' AS $$ import "
        + "com.snowflake.snowpark_java.Session; %s $$";

    private static final String MISSING_CLASS = "REFUSED User Error Report:  | Java Stack Trace: | "
        + "java.lang.ClassNotFoundException: %s | \tat java.base/java.net.URLClassLoader.findClass("
        + "URLClassLoader.java:476) | \tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
        + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function %s with handler %s";

    private static String procedure(final String signature, final String returns, final String body) {
        return String.format(HEAD, signature, returns, "H.run", body);
    }

    @Test
    public void anExceptionReadsAsTheHandlersReport() {
        assertCells(new String[][] {
            {procedure("sjthrow(a NUMBER)", "NUMBER", "object H { def run(s: Session, a: Int): Int = throw new "
                + "IllegalArgumentException(\"proc bad \" + a) }"), "Function SJTHROW successfully created."},
            {"CALL sjthrow(1)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: "
                + "proc bad 1 | \tat function_handler_0//H$.run(ScalaUDF:1) | \tat function_handler_0//H.run(ScalaUDF) "
                + "|  in function SJTHROW with handler H.run"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL sjthrow(1); EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' || "
                + "SQLSTATE || '|' || SQLERRM; END; $$",
                "100315|P0000|User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: proc bad 1 "
                + "| \tat function_handler_0//H$.run(ScalaUDF:1) | \tat function_handler_0//H.run(ScalaUDF) |  in "
                + "function SJTHROW with handler H.run"},
            {procedure("sjcls(a NUMBER)", "NUMBER", "class H { def run(s: Session, a: Int): Int = throw new "
                + "IllegalArgumentException(\"cls bad \" + a) }"), "Function SJCLS successfully created."},
            {"CALL sjcls(2)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: "
                + "cls bad 2 | \tat function_handler_0//H.run(ScalaUDF:1) |  in function SJCLS with handler H.run"},
            {procedure("sjcause(a NUMBER)", "NUMBER", "object H { def run(s: Session, a: Int): Int = throw new "
                + "RuntimeException(\"outer\", new java.io.IOException(\"inner \" + a)) }"),
                "Function SJCAUSE successfully created."},
            {"CALL sjcause(4)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException: outer | "
                + "\tat function_handler_0//H$.run(ScalaUDF:1) | \tat function_handler_0//H.run(ScalaUDF) | Caused by: "
                + "java.io.IOException: inner 4 | \t... 2 more |  in function SJCAUSE with handler H.run"},
        });
    }

    @Test
    public void aFailedStatementIsTheDriversException() {
        assertCells(new String[][] {
            {procedure("sjsql(a NUMBER)", "VARCHAR", "object H { def run(s: Session, a: Int): String = { "
                + "s.sql(\"SELECT * FROM no_such_tbl_sj\").collect(); \"x\" } }"),
                "Function SJSQL successfully created."},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL sjsql(1); EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' || "
                + "STARTSWITH(SQLERRM, 'User Error Report: \\nJava Stack Trace:\\nnet.snowflake.client.jdbc."
                + "SnowflakeSQLException: SQL compilation error:\\nObject ''NO_SUCH_TBL_SJ'' does not exist or not "
                + "authorized.')::VARCHAR || '|' || CONTAINS(SQLERRM, '\\n\\tat function_handler_0//com.snowflake."
                + "snowpark_java.DataFrame.collect(DataFrame.java:')::VARCHAR || '|' || ENDSWITH(SQLERRM, ')\\n\\tat "
                + "function_handler_0//H$.run(ScalaUDF:1)\\n\\tat function_handler_0//H.run(ScalaUDF)\\n in function "
                + "SJSQL with handler H.run')::VARCHAR; END; $$", "100315|true|true|true"},
        });
    }

    @Test
    public void aHandlerClassTheBodyLacksIsRefusedAtCreate() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION snoc(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'G.f' AS $$ object H { def f(a: Int): Int = a } $$",
                String.format(MISSING_CLASS, "G", "SNOC", "G.f")},
            {"SELECT snoc(1)", "REFUSED SQL compilation error: | Unknown function SNOC."},
            {String.format(HEAD, "sjnoc(a NUMBER)", "NUMBER", "G.run", "object H { def run(s: Session, a: Int): Int "
                + "= a }"), String.format(MISSING_CLASS, "G", "SJNOC", "G.run")},
            {"CALL sjnoc(1)", "REFUSED SQL compilation error: | Unknown function SJNOC."},
            {"CREATE OR REPLACE FUNCTION snop(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'p.G.f' AS $$ object H { def f(a: Int): Int = a } $$",
                String.format(MISSING_CLASS, "p.G", "SNOP", "p.G.f")},
            {"CREATE OR REPLACE FUNCTION snot(a NUMBER) RETURNS TABLE (v NUMBER) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'G' AS $$ object H { def f(a: Int): Int = a } $$",
                String.format(MISSING_CLASS, "G", "SNOT", "G")},
        });
    }
}
