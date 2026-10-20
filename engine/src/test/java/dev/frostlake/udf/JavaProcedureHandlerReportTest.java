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
 * What a Java procedure's handler raises reaches SQL as a function's handler's does: {@code User Error Report:},
 * the exception, its frames in {@code InlineCode.java} and its cause, then {@code  in function P with handler
 * H.run}, which a block's handler reads as SQLCODE 100315 and SQLSTATE P0000. A NullPointerException is named
 * without the sentence a newer Java composes for it under RUNTIME_VERSION 11 and with it under 17. A statement the
 * handler runs through its session that fails is the driver's exception, {@code net.snowflake.client.jdbc.
 * SnowflakeSQLException} and the statement's refusal, with the code and the state of that refusal, reported from
 * the session's {@code collect} down to the handler. A handler class whose constructor raises is refused when the
 * procedure is created (live-verified).
 */
public class JavaProcedureHandlerReportTest extends MeasuredCellsTestSupport {

    private static final String HEAD = "CREATE OR REPLACE PROCEDURE %s RETURNS %s LANGUAGE JAVA RUNTIME_VERSION = '%s' "
        + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$%s$$";

    private static String procedure(final String signature, final String returns, final String body) {
        return String.format(HEAD, signature, returns, "11", body);
    }

    /** A block that calls the procedure and answers what its handler reads of the failure. */
    private static String caught(final String call, final String read) {
        return "EXECUTE IMMEDIATE $$ BEGIN " + call + "; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN " + read
            + "; END; $$";
    }

    @Test
    public void anExceptionReadsAsTheHandlersReport() {
        assertCells(new String[][] {
            {procedure("jpthrow(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { public int "
                + "run(Session s, int a) { throw new IllegalArgumentException(\"jp bad \" + a); } }"),
                "Function JPTHROW successfully created."},
            {"CALL jpthrow(1)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: "
                + "jp bad 1 | \tat function_handler_0//H.run(InlineCode.java:1) |  in function JPTHROW with handler "
                + "H.run"},
            {caught("CALL jpthrow(1)", "SQLCODE || '|' || SQLSTATE || '|' || SQLERRM"),
                "100315|P0000|User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: jp bad 1 | "
                + "\tat function_handler_0//H.run(InlineCode.java:1) |  in function JPTHROW with handler H.run"},
            {procedure("jphelp(a INT)", "INT", "\nimport com.snowflake.snowpark_java.*;\nclass Helper {\n  static int "
                + "boom(int a) {\n    throw new IllegalStateException(\"deep \" + a);\n  }\n}\nclass H {\n  public int "
                + "run(Session s, int a) {\n    return Helper.boom(a);\n  }\n}\n"),
                "Function JPHELP successfully created."},
            {"CALL jphelp(2)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException: "
                + "deep 2 | \tat function_handler_0//Helper.boom(InlineCode.java:5) | \tat function_handler_0//H.run("
                + "InlineCode.java:10) |  in function JPHELP with handler H.run"},
            {procedure("jpcause(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { public int "
                + "run(Session s, int a) throws Exception { throw new Exception(\"outer\", new java.io.IOException("
                + "\"inner \" + a)); } }"), "Function JPCAUSE successfully created."},
            {"CALL jpcause(3)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.Exception: outer | \tat "
                + "function_handler_0//H.run(InlineCode.java:1) | Caused by: java.io.IOException: inner 3 | \t... 1 "
                + "more |  in function JPCAUSE with handler H.run"},
            {procedure("jpstatic(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { public static int "
                + "run(Session s, int a) { throw new UnsupportedOperationException(); } }"),
                "Function JPSTATIC successfully created."},
            {"CALL jpstatic(1)", "REFUSED User Error Report:  | Java Stack Trace: | "
                + "java.lang.UnsupportedOperationException | \tat function_handler_0//H.run(InlineCode.java:1) |  in "
                + "function JPSTATIC with handler H.run"},
            {procedure("jplines(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { public int "
                + "run(Session s, int a) { throw new RuntimeException(\"line1\\nline2\"); } }"),
                "Function JPLINES successfully created."},
            {"CALL jplines(1)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException: line1 | "
                + "line2 | \tat function_handler_0//H.run(InlineCode.java:1) |  in function JPLINES with handler "
                + "H.run"},
            {procedure("jpanon(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { public int "
                + "run(Session s, int a) { Runnable r = new Runnable() { public void run() { throw new "
                + "RuntimeException(\"anon \" + a); } }; r.run(); return a; } }"),
                "Function JPANON successfully created."},
            {"CALL jpanon(4)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException: anon 4 | "
                + "\tat function_handler_0//H$1.run(InlineCode.java:1) | \tat function_handler_0//H.run("
                + "InlineCode.java:1) |  in function JPANON with handler H.run"},
            {procedure("jptab()", "TABLE (a INT)", "import com.snowflake.snowpark_java.*; class H { public DataFrame "
                + "run(Session s) { throw new RuntimeException(\"tbl bad\"); } }"),
                "Function JPTAB successfully created."},
            {"CALL jptab()", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException: tbl bad | "
                + "\tat function_handler_0//H.run(InlineCode.java:1) |  in function JPTAB with handler H.run"},
            {procedure("jperr(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { public int "
                + "run(Session s, int a) { throw new AssertionError(\"asserted \" + a); } }"),
                "Function JPERR successfully created."},
            {"CALL jperr(5)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.AssertionError: asserted 5 "
                + "| \tat function_handler_0//H.run(InlineCode.java:1) |  in function JPERR with handler H.run"},
        });
    }

    @Test
    public void aNullPointerExceptionIsWordedAsTheRuntimeVersionsJava() {
        final String body = "import com.snowflake.snowpark_java.*; class H { public int run(Session s, int a) { "
            + "String t = null; return t.length() + a; } }";
        assertCells(new String[][] {
            {procedure("jpnpe(a INT)", "INT", body), "Function JPNPE successfully created."},
            {"CALL jpnpe(1)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H.run(InlineCode.java:1) |  in function JPNPE with handler H.run"},
            {String.format(HEAD, "jpnpe17(a INT)", "INT", "17", body), "Function JPNPE17 successfully created."},
            {"CALL jpnpe17(1)", "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException: "
                + "Cannot invoke \"String.length()\" because \"<local3>\" is null | \tat function_handler_0//H.run("
                + "InlineCode.java:1) |  in function JPNPE17 with handler H.run"},
        });
    }

    @Test
    public void aFailedStatementIsTheDriversException() {
        final String report = "STARTSWITH(SQLERRM, 'User Error Report: \\nJava Stack Trace:\\n"
            + "net.snowflake.client.jdbc.SnowflakeSQLException: ";
        final String collected = "CONTAINS(SQLERRM, '\\n\\tat function_handler_0//com.snowflake.snowpark_java."
            + "DataFrame.collect(DataFrame.java:')::VARCHAR";
        assertCells(new String[][] {
            {procedure("jpsql(a INT)", "VARCHAR", "import com.snowflake.snowpark_java.*; class H { public String "
                + "run(Session s, int a) { s.sql(\"SELECT * FROM no_such_tbl_jp\").collect(); return \"x\"; } }"),
                "Function JPSQL successfully created."},
            {caught("CALL jpsql(1)", "SQLCODE || '|' || SQLSTATE || '|' || " + report + "SQL compilation error:\\n"
                + "Object ''NO_SUCH_TBL_JP'' does not exist or not authorized. This executable runs with owner''s "
                + "rights.')::VARCHAR || '|' || " + collected + " || '|' || ENDSWITH(SQLERRM, ')\\n\\tat "
                + "function_handler_0//H.run(InlineCode.java:1)\\n in function JPSQL with handler H.run')::VARCHAR"),
                "100315|P0000|true|true|true"},
            {procedure("jpcatch(a INT)", "VARCHAR", "import com.snowflake.snowpark_java.*; class H { public String "
                + "run(Session s, int a) { try { s.sql(\"SELECT * FROM no_such_tbl_jp\").collect(); return \"none\"; "
                + "} catch (Exception e) { String t = e.toString(); int p = t.indexOf(\" This executable\"); "
                + "java.sql.SQLException q = (java.sql.SQLException) e; return (p < 0 ? t : t.substring(0, p)) + "
                + "\"|\" + q.getErrorCode() + \"|\" + q.getSQLState() + \"|\" + (e instanceof RuntimeException); } } "
                + "}"), "Function JPCATCH successfully created."},
            {"CALL jpcatch(1)", "net.snowflake.client.jdbc.SnowflakeSQLException: SQL compilation error: | Object "
                + "'NO_SUCH_TBL_JP' does not exist or not authorized.|2003|42S02|false"},
            {procedure("jpset(a INT)", "VARCHAR", "import com.snowflake.snowpark_java.*; class H { public String "
                + "run(Session s, int a) { s.sql(\"SET hv = 9\").collect(); return \"x\"; } }"),
                "Function JPSET successfully created."},
            {caught("CALL jpset(1)", report + "Stored procedure execution error: Unsupported statement type ''SET''."
                + "\\n')::VARCHAR || '|' || " + collected), "true|true"},
            {procedure("jpconv(a INT)", "NUMBER", "import com.snowflake.snowpark_java.*; class H { public long "
                + "run(Session s, int a) { return s.sql(\"SELECT 12345678901234567890 AS c\").collect()[0]"
                + ".getLong(0); } }"), "Function JPCONV successfully created."},
            {caught("CALL jpconv(1)", report + "Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=12345678901234567890.\\n')"), "true"},
        });
    }

    @Test
    public void aConstructorThatRaisesRefusesTheProcedure() {
        assertCells(new String[][] {
            {procedure("jpinit(a INT)", "INT", "import com.snowflake.snowpark_java.*; class H { int z = 1 / zero(); "
                + "static int zero() { return 0; } public int run(Session s, int a) { return a; } }"),
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ArithmeticException: / by zero | \tat "
                + "function_handler_0//H.<init>(InlineCode.java:1) |  in function JPINIT with handler H.run"},
            {"CALL jpinit(1)", "REFUSED SQL compilation error: | Unknown function JPINIT."},
        });
    }
}
