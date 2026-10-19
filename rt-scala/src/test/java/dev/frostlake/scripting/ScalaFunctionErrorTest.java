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
 * What a Scala function's errors read as. An exception its handler raises reaches SQL as {@code User Error
 * Report:} with the exception, its frames in {@code ScalaUDF} - an object's method under its forwarder - and
 * its cause. A body that does not compile is refused when the function is created, with the compiler's
 * complaint, the line and a mark under the place; so is a handler method the body lacks. A NULL for an
 * {@code Int}, a {@code Long}, a {@code Double} or a {@code Boolean} parameter is refused by name, and a
 * boxed or a text parameter takes it. A Scala procedure follows the same rules at CREATE. Every answer is
 * live-verified.
 */
public class ScalaFunctionErrorTest extends MeasuredCellsTestSupport {

    @Test
    public void anExceptionReadsAsTheHandlersReport() {
        assertAnswer(
            "Function STHROW successfully created.",
            "CREATE OR REPLACE FUNCTION sthrow(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = throw new IllegalArgumentException(\"bad "
                + "input \" + a) } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: bad input 1 | "
                + "\tat function_handler_0//H$.f(ScalaUDF:1) | \tat function_handler_0//H.f(ScalaUDF) |  in function "
                + "STHROW with handler H.f",
            "SELECT sthrow(1)");
        assertAnswer(
            "Function SNOMSG successfully created.",
            "CREATE OR REPLACE FUNCTION snomsg(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = throw new IllegalStateException() } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException | \tat "
                + "function_handler_0//H$.f(ScalaUDF:1) | \tat function_handler_0//H.f(ScalaUDF) |  in function "
                + "SNOMSG with handler H.f",
            "SELECT snomsg(1)");
        assertAnswer(
            "Function SCAUSE successfully created.",
            "CREATE OR REPLACE FUNCTION scause(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = throw new RuntimeException(\"outer\", new "
                + "IllegalArgumentException(\"inner\")) } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.RuntimeException: outer | \tat "
                + "function_handler_0//H$.f(ScalaUDF:1) | \tat function_handler_0//H.f(ScalaUDF) | Caused by: "
                + "java.lang.IllegalArgumentException: inner | \t... 2 more |  in function SCAUSE with handler H.f",
            "SELECT scause(1)");
        assertAnswer(
            "Function SDIV successfully created.",
            "CREATE OR REPLACE FUNCTION sdiv(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = 1 / (a - a) } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ArithmeticException: / by zero | \tat "
                + "function_handler_0//H$.f(ScalaUDF:1) | \tat function_handler_0//H.f(ScalaUDF) |  in function SDIV "
                + "with handler H.f",
            "SELECT sdiv(1)");
        assertAnswer(
            "Function SMATCH successfully created.",
            "CREATE OR REPLACE FUNCTION smatch(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = (a: Any) match { case s: String => 1 } } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | scala.MatchError: 1 (of class java.lang.Integer) | "
                + "\tat function_handler_0//H$.f(ScalaUDF:1) | \tat function_handler_0//H.f(ScalaUDF) |  in function "
                + "SMATCH with handler H.f",
            "SELECT smatch(1)");
        assertAnswer(
            "Function SNPE successfully created.",
            "CREATE OR REPLACE FUNCTION snpe(a VARCHAR) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: String): Int = a.length } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H$.f(ScalaUDF:1) | \tat function_handler_0//H.f(ScalaUDF) |  in function SNPE "
                + "with handler H.f",
            "SELECT snpe(NULL)");
        assertAnswer(
            "2",
            "SELECT snpe('ab')");
    }

    @Test
    public void aBodyThatDoesNotCompileAndAMissingHandler() {
        assertAnswer(
            "REFUSED ScalaUDF:1: error: type mismatch; |  found   : String |  required: Int |  object H { def "
                + "f(a: Int): Int = a + \"x\" }  |                                    ^ |  in function SBAD with "
                + "handler H.f",
            "CREATE OR REPLACE FUNCTION sbad(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = a + \"x\" } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SBAD.",
            "SELECT sbad(1)");
        assertAnswer(
            "REFUSED ScalaUDF:1: error: '}' expected but eof found. |  object H { def f(a: Int): Int = { a + }  | "
                + "                                        ^ |  in function SSYN with handler H.f",
            "CREATE OR REPLACE FUNCTION ssyn(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = { a + } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SSYN.",
            "SELECT ssyn(1)");
        assertAnswer(
            "REFUSED ScalaUDF:1: error: not found: value nope |  object H { def f(a: Int): Int = nope(a) }  |     "
                + "                             ^ |  in function SUNK with handler H.f",
            "CREATE OR REPLACE FUNCTION sunk(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = nope(a) } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SUNK.",
            "SELECT sunk(1)");
        assertAnswer(
            "REFUSED ScalaUDF:1: error: type mismatch; |  found   : String(\"s\") |  required: Int |  object H { "
                + "def f(a: Int): Int = { val x: Int = \"s\"; nope(a) } }  |                                         "
                + "        ^ | ScalaUDF:1: error: not found: value nope |  object H { def f(a: Int): Int = { val x: "
                + "Int = \"s\"; nope(a) } }  |                                                      ^ |  in function "
                + "STWO with handler H.f",
            "CREATE OR REPLACE FUNCTION stwo(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = { val x: Int = \"s\"; nope(a) } } $$");
        assertAnswer(
            "REFUSED Failed to find a public method named \"g\" with 1 arguments in function SNOH with handler H.g",
            "CREATE OR REPLACE FUNCTION snoh(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.g' AS $$ object H { def f(a: Int): Int = a + 1 } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SNOH.",
            "SELECT snoh(1)");
        answer(
            "CREATE OR REPLACE FUNCTION snoc(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'G.f' AS $$ object H { def f(a: Int): Int = a + 1 } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SNOC.",
            "SELECT snoc(1)");
        assertAnswer(
            "REFUSED Failed to find a public method named \"f\" with 1 arguments in function SAR with handler H.f",
            "CREATE OR REPLACE FUNCTION sar(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int, b: Int): Int = a + b } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SAR.",
            "SELECT sar(1)");
    }

    @Test
    public void aNullForAPrimitiveParameterAndAClassHandler() {
        assertAnswer(
            "Function SF successfully created.",
            "CREATE OR REPLACE FUNCTION sf(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = a + 1 } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of int in function SF with handler H.f",
            "SELECT sf(NULL)");
        assertAnswer(
            "2",
            "SELECT sf(1)");
        assertAnswer(
            "Function SL successfully created.",
            "CREATE OR REPLACE FUNCTION sl(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Long): Long = a + 1 } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of long in function SL with handler H.f",
            "SELECT sl(NULL)");
        assertAnswer(
            "Function SD successfully created.",
            "CREATE OR REPLACE FUNCTION sd(a FLOAT) RETURNS FLOAT LANGUAGE SCALA RUNTIME_VERSION = '2.12' HANDLER "
                + "= 'H.f' AS $$ object H { def f(a: Double): Double = a + 1 } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of double in function SD with handler H.f",
            "SELECT sd(NULL)");
        assertAnswer(
            "Function SB successfully created.",
            "CREATE OR REPLACE FUNCTION sb(a BOOLEAN) RETURNS BOOLEAN LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Boolean): Boolean = !a } $$");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of boolean in function SB with handler H.f",
            "SELECT sb(NULL)");
        assertAnswer(
            "Function SBOX successfully created.",
            "CREATE OR REPLACE FUNCTION sbox(a NUMBER) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: java.lang.Integer): String = String.valueOf(a) } $$");
        assertAnswer(
            "null",
            "SELECT sbox(NULL)");
        assertAnswer(
            "5",
            "SELECT sbox(5)");
        assertAnswer(
            "Function SSTR successfully created.",
            "CREATE OR REPLACE FUNCTION sstr(a VARCHAR) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: String): String = if (a == null) \"was null\" else a } "
                + "$$");
        assertAnswer(
            "was null",
            "SELECT sstr(NULL)");
        assertAnswer(
            "Function SCLS successfully created.",
            "CREATE OR REPLACE FUNCTION scls(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ class H { def f(a: Int): Int = throw new IllegalArgumentException(\"in "
                + "class \" + a) } $$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalArgumentException: in class 1 | "
                + "\tat function_handler_0//H.f(ScalaUDF:1) |  in function SCLS with handler H.f",
            "SELECT scls(1)");
    }

    @Test
    public void aScalaProcedureFollowsAtCreate() {
        assertAnswer(
            "Function SJOK successfully created.",
            "CREATE OR REPLACE PROCEDURE sjok(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; object H { def run(s: Session, a: Int): Int = a + 1 } $$");
        assertAnswer(
            "2",
            "CALL sjok(1)");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of int in function SJOK with handler H.run",
            "CALL sjok(NULL)");
        assertAnswer(
            "Function SJTHROW successfully created.",
            "CREATE OR REPLACE PROCEDURE sjthrow(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; object H { def run(s: Session, a: Int): Int = throw new "
                + "IllegalArgumentException(\"proc bad \" + a) } $$");
        answer(
            "CALL sjthrow(1)");
        assertAnswer(
            "REFUSED ScalaUDF:1: error: type mismatch; |  found   : String |  required: Int |  import "
                + "com.snowflake.snowpark_java.Session; object H { def run(s: Session, a: Int): Int = a + \"x\" }  | "
                + "                                                                                             ^ |  "
                + "in function SJBAD with handler H.run",
            "CREATE OR REPLACE PROCEDURE sjbad(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; object H { def run(s: Session, a: Int): Int = a + \"x\" } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SJBAD.",
            "CALL sjbad(1)");
        assertAnswer(
            "REFUSED Failed to find a public method named \"run\" with 2 arguments in function SJNOH with handler "
                + "H.run",
            "CREATE OR REPLACE PROCEDURE sjnoh(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; object H { def go(s: Session, a: Int): Int = a + 1 } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SJNOH.",
            "CALL sjnoh(1)");
        assertAnswer(
            "REFUSED ScalaUDF:4: error: type mismatch; |  found   : String(\"s\") |  required: Int |     val b: "
                + "Int = \"s\" |                  ^ |  in function SCTWO with handler H.f",
            "CREATE OR REPLACE FUNCTION sctwo(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$\nobject H {\n  def f(a: Int): Int = {\n    val b: Int = \"s\"\n    a\n  "
                + "}\n}\n$$");
    }
}
