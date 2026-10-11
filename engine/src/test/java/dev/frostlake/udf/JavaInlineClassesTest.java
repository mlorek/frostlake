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
 * An inline Java handler's source may declare more than the handler's class: a nested class, an enum, an
 * anonymous class, an interface with a class that implements it, a generic class, an exception of its own and
 * another top-level class beside the handler's - before it or after it, the handler's class public or not.
 * Every one of them is there when the handler runs, for a function called once, twice in a statement and for
 * every row, and for a procedure. Every answer is live-verified.
 */
public class JavaInlineClassesTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlerUsesTheClassesItsSourceDeclares() {
        assertAnswer(
            "Function FNEST successfully created.",
            "CREATE OR REPLACE FUNCTION fnest(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { static class Acc { long v; Acc(long v) { this.v = v; } } public static long f(long x) { "
                + "return new Acc(x).v * 2; } } $$");
        assertAnswer(
            "42",
            "SELECT fnest(21)");
        assertAnswer(
            "Function FHELPER successfully created.",
            "CREATE OR REPLACE FUNCTION fhelper(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class Helper { static String up(String s) { return s.toUpperCase(); } } class H { public static "
                + "String f(String x) { return Helper.up(x); } } $$");
        assertAnswer(
            "ABC",
            "SELECT fhelper('abc')");
        assertAnswer(
            "Function FANON successfully created.",
            "CREATE OR REPLACE FUNCTION fanon(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "import java.util.*; class H { public static String f(String x) { List<String> l = new "
                + "ArrayList<>(Arrays.asList(x.split(\",\"))); Collections.sort(l, new Comparator<String>() { public "
                + "int compare(String a, String b) { return b.compareTo(a); } }); return String.join(\",\", l); } } "
                + "$$");
        assertAnswer(
            "c,b,a",
            "SELECT fanon('a,c,b')");
        assertAnswer(
            "Function FENUM successfully created.",
            "CREATE OR REPLACE FUNCTION fenum(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { enum Color { RED, GREEN } public static String f(String x) { return "
                + "Color.valueOf(x).ordinal() + \":\" + Color.values().length; } } $$");
        assertAnswer(
            "1:2",
            "SELECT fenum('GREEN')");
        assertAnswer(
            "Function FINNER successfully created.",
            "CREATE OR REPLACE FUNCTION finner(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { class Inner { long twice(long v) { return v * 2; } } public long f(long x) { return new "
                + "Inner().twice(x); } } $$");
        answer(
            "SELECT finner(4)");
        assertAnswer(
            "Function FIFACE successfully created.",
            "CREATE OR REPLACE FUNCTION fiface(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "interface Shape { String name(); } class Square implements Shape { public String name() { return "
                + "\"square\"; } } class H { public static String f(String x) { Shape s = new Square(); return x + "
                + "\"=\" + s.name(); } } $$");
        assertAnswer(
            "a=square",
            "SELECT fiface('a')");
        assertAnswer(
            "Function FLAMBDA successfully created.",
            "CREATE OR REPLACE FUNCTION flambda(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "import java.util.function.Function; class H { public static String f(String x) { Function<String, "
                + "String> g = s -> s + \"!\"; return g.apply(x); } } $$");
        assertAnswer(
            "a!",
            "SELECT flambda('a')");
        assertAnswer(
            "Function FPUBLIC successfully created.",
            "CREATE OR REPLACE FUNCTION fpublic(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'Main.f' AS $$ "
                + "class Util { static int len(String s) { return s.length(); } } public class Main { public static "
                + "String f(String x) { return \"n\" + Util.len(x); } } $$");
        assertAnswer(
            "n4",
            "SELECT fpublic('abcd')");
        assertAnswer(
            "Function FSTATIC successfully created.",
            "CREATE OR REPLACE FUNCTION fstatic(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class Counter { static long n = 0; static long next() { return ++n; } } class H { public static "
                + "long f(long x) { return Counter.next() * 0 + x; } } $$");
        assertAnswer(
            "5",
            "SELECT fstatic(5)");
        assertAnswer(
            "Function FGENERIC successfully created.",
            "CREATE OR REPLACE FUNCTION fgeneric(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class Box<T> { final T v; Box(T v) { this.v = v; } T get() { return v; } } class H { public "
                + "static String f(String x) { return new Box<String>(x).get() + new Box<Integer>(1).get(); } } $$");
        assertAnswer(
            "b1",
            "SELECT fgeneric('b')");
        assertAnswer(
            "Function FEXC successfully created.",
            "CREATE OR REPLACE FUNCTION fexc(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "Oops extends RuntimeException { Oops(String m) { super(m); } } class H { public static String "
                + "f(String x) { try { throw new Oops(x); } catch (Oops o) { return \"caught \" + o.getMessage(); } "
                + "} } $$");
        assertAnswer(
            "caught it",
            "SELECT fexc('it')");
        assertAnswer(
            "Function PNEST successfully created.",
            "CREATE OR REPLACE PROCEDURE pnest() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; class H { static class Acc { long v = 7; } public long "
                + "run(Session s) { return new Acc().v; } } $$");
        assertAnswer(
            "7",
            "CALL pnest()");
        assertAnswer(
            "6|X|0:2",
            "SELECT fnest(1) + fnest(2), fhelper(fhelper('x')), fenum('RED')");
        assertAnswer(
            "2 ;; 4",
            "SELECT fnest(x) FROM (SELECT 1 AS x UNION ALL SELECT 2) ORDER BY 1");
    }
}
