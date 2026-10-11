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
 * An inline Java function's handler may be an instance method. The class is made with its zero-argument
 * constructor, initialisers included, and the method is called on that instance; what the instance holds does
 * not reach a later statement. A method the class inherits is the handler too. A class that cannot be made is
 * refused when the function is created: one without a zero-argument constructor that is not private -
 * {@code The provided method is non-static, but the enclosing class does not have a public zero-argument
 * constructor} - an abstract class, and one whose constructor throws, which reads as a handler's own
 * exception does. So is a handler that is not public, one of two methods of the name, one that answers
 * nothing and one the class does not have. Every answer is live-verified.
 */
public class JavaInstanceHandlerTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlerMethodMayBeAnInstanceMethod() {
        assertAnswer(
            "Function FINST successfully created.",
            "CREATE OR REPLACE FUNCTION finst(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { private final int base; public H() { base = 100; } public int f(int x) { return base + x; } } "
                + "$$");
        assertAnswer(
            "101",
            "SELECT finst(1)");
        assertAnswer(
            "Function FSTAT successfully created.",
            "CREATE OR REPLACE FUNCTION fstat(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static int f(int x) { return 100 + x; } } $$");
        assertAnswer(
            "101",
            "SELECT fstat(1)");
        assertAnswer(
            "Function FCOUNT successfully created.",
            "CREATE OR REPLACE FUNCTION fcount(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { private int n = 0; public int f(int x) { n = n + 1; return n; } } $$");
        assertAnswer(
            "1",
            "SELECT fcount(1)");
        assertAnswer(
            "1",
            "SELECT fcount(1)");
        assertAnswer(
            "REFUSED The provided method is non-static, but the enclosing class does not have a public "
                + "zero-argument constructor in function FNOARG with handler H.f",
            "CREATE OR REPLACE FUNCTION fnoarg(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { private final int base; public H(int base) { this.base = base; } public int f(int x) { return "
                + "base + x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FNOARG.",
            "SELECT fnoarg(1)");
        assertAnswer(
            "REFUSED The provided method is non-static, but the enclosing class does not have a public "
                + "zero-argument constructor in function FPRIVATE with handler H.f",
            "CREATE OR REPLACE FUNCTION fprivate(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { private H() { } public int f(int x) { return 7 + x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FPRIVATE.",
            "SELECT fprivate(1)");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException: no instance | "
                + "\tat function_handler_0//H.<init>(InlineCode.java:1) |  in function FTHROWS with handler H.f",
            "CREATE OR REPLACE FUNCTION fthrows(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public H() { throw new IllegalStateException(\"no instance\"); } public int f(int x) { "
                + "return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FTHROWS.",
            "SELECT fthrows(1)");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.InstantiationException: H |  in function "
                + "FABSTRACT with handler H.f",
            "CREATE OR REPLACE FUNCTION fabstract(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "abstract class H { public int f(int x) { return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FABSTRACT.",
            "SELECT fabstract(1)");
        assertAnswer(
            "REFUSED Cannot determine which implementation of handler \"f\" to invoke since there are multiple "
                + "definitions with 1 arguments in function FBOTH with handler H.f",
            "CREATE OR REPLACE FUNCTION fboth(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public int f(int x) { return 1; } public static int f(long x) { return 2; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FBOTH.",
            "SELECT fboth(1)");
        assertAnswer(
            "REFUSED Failed to find a public method named \"f\" with 1 arguments in function FNONPUB with handler "
                + "H.f",
            "CREATE OR REPLACE FUNCTION fnonpub(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { int f(int x) { return x + 5; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FNONPUB.",
            "SELECT fnonpub(1)");
        assertAnswer(
            "REFUSED Failed to find a public method named \"f\" with 1 arguments in function FPRIVM with handler "
                + "H.f",
            "CREATE OR REPLACE FUNCTION fprivm(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { private static int f(int x) { return x + 6; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FPRIVM.",
            "SELECT fprivm(1)");
        assertAnswer(
            "Function FINNER successfully created.",
            "CREATE OR REPLACE FUNCTION finner(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { class Inner { long twice(long v) { return v * 2; } } public long f(long x) { return new "
                + "Inner().twice(x); } } $$");
        assertAnswer(
            "8",
            "SELECT finner(4)");
        assertAnswer(
            "Function FSUPER successfully created.",
            "CREATE OR REPLACE FUNCTION fsuper(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "Base { public int f(int x) { return x + 9; } } class H extends Base { } $$");
        assertAnswer(
            "10",
            "SELECT fsuper(1)");
        assertAnswer(
            "Function FINIT successfully created.",
            "CREATE OR REPLACE FUNCTION finit(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { static int loaded = 40; int made; { made = 2; } public int f(int x) { return loaded + made + "
                + "x; } } $$");
        assertAnswer(
            "42",
            "SELECT finit(0)");
        assertAnswer(
            "REFUSED Unsupported return type: void in function FVOID with handler H.f",
            "CREATE OR REPLACE FUNCTION fvoid(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public void f(int x) { } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FVOID.",
            "SELECT fvoid(1)");
        assertAnswer(
            "REFUSED Failed to find a public method named \"g\" with 1 arguments in function FNOMETHOD with "
                + "handler H.g",
            "CREATE OR REPLACE FUNCTION fnomethod(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.g' AS $$ "
                + "class H { public int f(int x) { return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FNOMETHOD.",
            "SELECT fnomethod(1)");
        answer(
            "CREATE OR REPLACE FUNCTION fnoclass(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'G.f' AS $$ "
                + "class H { public int f(int x) { return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FNOCLASS.",
            "SELECT fnoclass(1)");
        assertAnswer(
            "101 ;; 102",
            "SELECT finst(x) FROM (SELECT 1 AS x UNION ALL SELECT 2) ORDER BY 1");
        assertAnswer(
            "203",
            "SELECT finst(1) + finst(2)");
    }
}
