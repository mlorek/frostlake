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
 * A Java handler's exception as the account reports it (live-verified): a User Error Report of the exception's class
 * and message - a NullPointerException with no sentence of its own - and the handler's frame, for a function and a
 * procedure alike. A CALL converts its arguments as the parameters' Java types take them: a NUMBER rounded half away
 * from zero into an int, a text read as the cast reads it, a NULL refused for a primitive and kept for its box, and a
 * whole number past an int refused.
 */
public class JavaHandlerFaultReportsTest extends MeasuredCellsTestSupport {

    /** Faults of a function's and a procedure's handler, and CALL arguments for primitive and boxed parameters. */
    @Test
    public void handlerFaultsAndCallConversions() {
        engine.execute("CREATE OR REPLACE FUNCTION jnf800(s VARCHAR) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = "
            + "'11' HANDLER = 'H.f' AS $$\nclass H {\n  public static int f(String s) { String t = s.equals(\"null\") "
            + "? null : s; return t.length(); }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jnp800(s VARCHAR) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = "
            + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static int run(Session session, String s) {\n    if "
            + "(s.equals(\"boom\")) { throw new RuntimeException(\"boom\"); }\n    if (s.equals(\"state\")) { throw "
            + "new IllegalStateException(\"bad state\"); }\n    if (s.equals(\"plain\")) { throw new "
            + "RuntimeException(); }\n    String t = s.equals(\"null\") ? null : s;\n    return t.length();\n  "
            + "}\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jip800(n INT, d DOUBLE, b BOOLEAN) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session session, int n, double d, "
            + "boolean b) { return n + \"/\" + d + \"/\" + b; }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jop800(n INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session session, Integer n) { "
            + "return String.valueOf(n); }\n}\n$$");
        assertCells(new String[][] {
            {"SELECT jnf800('abc')",
                "3"},
            {"SELECT jnf800('null')",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H.f(InlineCode.java:3) |  in function JNF800 with handler H.f"},
            {"SELECT jnf800(NULL)",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H.f(InlineCode.java:3) |  in function JNF800 with handler H.f"},
            {"CALL jnp800('abc')",
                "3"},
            {"CALL jnp800('null')",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H.run(InlineCode.java:9) |  in function JNP800 with handler H.run"},
            {"CALL jnp800('boom')",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException: boom | \tat "
                + "function_handler_0//H.run(InlineCode.java:5) |  in function JNP800 with handler H.run"},
            {"CALL jnp800('state')",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException: bad state | \tat "
                + "function_handler_0//H.run(InlineCode.java:6) |  in function JNP800 with handler H.run"},
            {"CALL jnp800('plain')",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException | \tat "
                + "function_handler_0//H.run(InlineCode.java:7) |  in function JNP800 with handler H.run"},
            {"CALL jnp800(NULL)",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H.run(InlineCode.java:5) |  in function JNP800 with handler H.run"},
            {"CALL jip800(1, 1.5, TRUE)",
                "1/1.5/true"},
            {"CALL jip800(1.6, 2, FALSE)",
                "2/2.0/false"},
            {"CALL jip800('7', '2.5', 'yes')",
                "7/2.5/true"},
            {"CALL jip800(NULL, 1, TRUE)",
                "REFUSED Cannot pass a NULL value as input to a Java type of int in function JIP800 with handler "
                + "H.run"},
            {"CALL jip800(1, NULL, TRUE)",
                "REFUSED Cannot pass a NULL value as input to a Java type of double in function JIP800 with handler "
                + "H.run"},
            {"CALL jip800(1, 1, NULL)",
                "REFUSED Cannot pass a NULL value as input to a Java type of boolean in function JIP800 with handler "
                + "H.run"},
            {"CALL jip800(2147483648, 1, TRUE)",
                "REFUSED Number out of representable range: type int32, value 2147483648 in function JIP800 with "
                + "handler H.run"},
            {"CALL jip800(-2.5, 1e300, TRUE)",
                "-3/1.0E300/true"},
            {"CALL jip800('x', 1, TRUE)",
                "REFUSED Numeric value 'x' is not recognized"},
            {"CALL jop800(NULL)",
                "null"},
            {"CALL jop800(5)",
                "5"},
            {"CALL jop800(2147483648)",
                "REFUSED Number out of representable range: type int32, value 2147483648 in function JOP800 with "
                + "handler H.run"},
        });
    }
}
