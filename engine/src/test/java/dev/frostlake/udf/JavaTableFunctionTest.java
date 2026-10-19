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
 * A table function written in Java. Its HANDLER names a class: {@code process} answers a Stream of output
 * objects for each input row, {@code endPartition}, when the class has one, answers more once a partition's
 * rows are through, and {@code getOutputClass} names the output class, whose public fields are the declared
 * columns by name whatever their case and order. A function is refused when it is created for a class
 * without {@code getOutputClass}, without a {@code process} of its arguments' count, with a
 * {@code process} that answers no Stream, or with a declared column the output class has no public field
 * for. A null where a Stream is due and what the handler raises are the call's refusals. Called OVER a
 * partition, one instance reads the partition's rows in the order asked for, and the rows it writes at the
 * end are joined to the partition's own columns. Every answer is live-verified.
 */
public class JavaTableFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlerClassAnswersRowsForEachInputRow() {
        assertAnswer(
            "Table EV successfully created.",
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        assertAnswer(
            "3",
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10)");
        assertAnswer(
            "Function JT successfully created.",
            "CREATE OR REPLACE FUNCTION jt(n NUMBER) RETURNS TABLE (v NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass OutputRow { public long v; public OutputRow(long v) { "
                + "this.v = v; } }\nclass Gen {\n  public static Class getOutputClass() { return OutputRow.class; "
                + "}\n  public Stream<OutputRow> process(long n) { return Stream.of(new OutputRow(n), new "
                + "OutputRow(n + 1)); }\n  public Stream<OutputRow> endPartition() { return Stream.empty(); }\n}\n$$");
        assertAnswer(
            "5 ;; 6",
            "SELECT * FROM TABLE(jt(5)) ORDER BY 1");
        assertShape(
            "V:NUMBER(38,0)",
            "SELECT * FROM TABLE(jt(5))");
        assertAnswer(
            "1|1 ;; 1|2 ;; 2|2 ;; 2|3 ;; 10|10 ;; 10|11",
            "SELECT ev.v, f.v FROM ev, TABLE(jt(ev.v)) f ORDER BY 1, 2");
        assertAnswer(
            "Function JT2 successfully created.",
            "CREATE OR REPLACE FUNCTION jt2(a VARCHAR, n NUMBER) RETURNS TABLE (name VARCHAR, total NUMBER, last "
                + "BOOLEAN) LANGUAGE JAVA HANDLER = 'Agg' AS $$\nimport java.util.stream.Stream;\nclass Out { public "
                + "String name; public long total; public boolean last; public Out(String name, long total, boolean "
                + "last) { this.name = name; this.total = total; this.last = last; } }\nclass Agg {\n  private long "
                + "sum = 0; private int rows = 0;\n  public static Class getOutputClass() { return Out.class; }\n  "
                + "public Stream<Out> process(String a, long n) { sum += n; rows++; return Stream.of(new Out(a, n, "
                + "false)); }\n  public Stream<Out> endPartition() { return Stream.of(new Out(\"end\" + rows, sum, "
                + "true)); }\n}\n$$");
        assertAnswer(
            "x|5|false ;; end1|5|true",
            "SELECT * FROM TABLE(jt2('x', 5)) ORDER BY 3, 1");
        assertShape(
            "NAME:VARCHAR(134217728), TOTAL:NUMBER(38,0), LAST:BOOLEAN",
            "SELECT * FROM TABLE(jt2('x', 5))");
        assertAnswer(
            "REFUSED Failed to find a public method named \"getOutputClass\" with 0 arguments | The "
                + "getOutputClass() method is required for Java UDTFs in function JTNO with handler Gen",
            "CREATE OR REPLACE FUNCTION jtno(n NUMBER) RETURNS TABLE (v NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass OutputRow { public long v; public OutputRow(long v) { "
                + "this.v = v; } }\nclass Gen {\n  public Stream<OutputRow> process(long n) { return Stream.of(new "
                + "OutputRow(n)); }\n}\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function JTNO",
            "SELECT * FROM TABLE(jtno(5))");
        assertAnswer(
            "Function JTEX successfully created.",
            "CREATE OR REPLACE FUNCTION jtex(n NUMBER) RETURNS TABLE (v NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass OutputRow { public long v; public OutputRow(long v) { "
                + "this.v = v; } }\nclass Gen {\n  public static Class getOutputClass() { return OutputRow.class; "
                + "}\n  public Stream<OutputRow> process(long n) { if (n > 1) throw new IllegalStateException(\"too "
                + "big \" + n); return Stream.of(new OutputRow(n)); }\n}\n$$");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(jtex(1))");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException: too big 5 | \tat "
                + "function_handler_0//Gen.process(InlineCode.java:6) |  in function JTEX with handler Gen",
            "SELECT * FROM TABLE(jtex(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function JT.",
            "SELECT jt(5)");
    }

    @Test
    public void theOutputClassAndWhatCreateRefuses() {
        answer(
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        answer(
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10), (NULL, 5)");
        assertAnswer(
            "Function JU1 successfully created.",
            "CREATE OR REPLACE FUNCTION ju1(n NUMBER) RETURNS TABLE (a NUMBER, b VARCHAR) LANGUAGE JAVA HANDLER = "
                + "'Gen' AS $$\nimport java.util.stream.Stream;\nclass Out { public String b; public long a; public "
                + "Out(long a, String b) { this.a = a; this.b = b; } }\nclass Gen { public static Class "
                + "getOutputClass() { return Out.class; } public Stream<Out> process(long n) { return Stream.of(new "
                + "Out(n, \"s\" + n)); } }\n$$");
        assertAnswer(
            "5|s5",
            "SELECT * FROM TABLE(ju1(5))");
        assertShape(
            "A:NUMBER(38,0), B:VARCHAR(134217728)",
            "SELECT * FROM TABLE(ju1(5))");
        assertAnswer(
            "Function JU2 successfully created.",
            "CREATE OR REPLACE FUNCTION ju2(n NUMBER) RETURNS TABLE (total NUMBER, name VARCHAR) LANGUAGE JAVA "
                + "HANDLER = 'Gen' AS $$\nimport java.util.stream.Stream;\nclass Out { public long Total; public "
                + "String NAME; public String extra = \"x\"; public Out(long t) { this.Total = t; this.NAME = \"n\"; "
                + "} }\nclass Gen { public static Class getOutputClass() { return Out.class; } public Stream<Out> "
                + "process(long n) { return Stream.of(new Out(n)); } }\n$$");
        assertAnswer(
            "5|n",
            "SELECT * FROM TABLE(ju2(5))");
        assertAnswer(
            "REFUSED The output class \"Out\" is missing fields with the following names: MISSING. Make sure the "
                + "member fields are public in function JU3 with handler Gen",
            "CREATE OR REPLACE FUNCTION ju3(n NUMBER) RETURNS TABLE (a NUMBER, missing VARCHAR) LANGUAGE JAVA "
                + "HANDLER = 'Gen' AS $$\nimport java.util.stream.Stream;\nclass Out { public long a; public "
                + "Out(long a) { this.a = a; } }\nclass Gen { public static Class getOutputClass() { return "
                + "Out.class; } public Stream<Out> process(long n) { return Stream.of(new Out(n)); } }\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function JU3",
            "SELECT * FROM TABLE(ju3(5))");
        assertAnswer(
            "Function JU4 successfully created.",
            "CREATE OR REPLACE FUNCTION ju4(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { public long a; public Out(long a) { this.a = a; "
                + "} }\nclass Gen { public static Class getOutputClass() { return Out.class; } public Stream<Out> "
                + "process(long n) { return n > 1 ? null : Stream.empty(); } public Stream<Out> endPartition() { "
                + "return null; } }\n$$");
        assertAnswer(
            "REFUSED Return value from UDTF \"process\" method is null instead of a java.util.stream.Stream "
                + "object in function JU4 with handler Gen",
            "SELECT COUNT(*) FROM TABLE(ju4(5))");
        assertAnswer(
            "REFUSED Return value from UDTF \"endPartition\" method is null instead of a java.util.stream.Stream "
                + "object in function JU4 with handler Gen",
            "SELECT COUNT(*) FROM TABLE(ju4(1))");
        assertAnswer(
            "Function JU5 successfully created.",
            "CREATE OR REPLACE FUNCTION ju5(n NUMBER, s VARCHAR, f FLOAT, b BOOLEAN) RETURNS TABLE (d FLOAT, t "
                + "VARCHAR, ok BOOLEAN, dec NUMBER(10,2), dt DATE, big NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { public double d; public String t; public boolean "
                + "ok; public java.math.BigDecimal dec; public java.sql.Date dt; public Long big; public Out(double "
                + "d, String t, boolean ok) { this.d = d; this.t = t; this.ok = ok; this.dec = new "
                + "java.math.BigDecimal(\"1.239\"); this.dt = new java.sql.Date(86400000L); this.big = null; } "
                + "}\nclass Gen { public static Class getOutputClass() { return Out.class; } public Stream<Out> "
                + "process(int n, String s, double f, boolean b) { return Stream.of(new Out(f + n, s + b, !b)); } "
                + "}\n$$");
        assertAnswer(
            "2.5|xtrue|false|1.24|1970-01-02|null",
            "SELECT * FROM TABLE(ju5(1, 'x', 1.5::FLOAT, TRUE))");
        shape(
            "SELECT * FROM TABLE(ju5(1, 'x', 1.5::FLOAT, TRUE))");
        assertAnswer(
            "REFUSED Cannot pass a NULL value as input to a Java type of int in function JU5 with handler Gen",
            "SELECT * FROM TABLE(ju5(NULL::NUMBER, 'x', 1.5::FLOAT, TRUE))");
        assertAnswer(
            "2.5|nulltrue|false|1.24|1970-01-02|null",
            "SELECT * FROM TABLE(ju5(1, NULL::VARCHAR, 1.5::FLOAT, TRUE))");
        assertAnswer(
            "REFUSED Failed to find a public method named \"process\" with 1 arguments in function JU6 with "
                + "handler Gen",
            "CREATE OR REPLACE FUNCTION ju6(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { public long a; public Out(long a) { this.a = a; "
                + "} }\nclass Gen { public static Class getOutputClass() { return Out.class; } }\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function JU6",
            "SELECT * FROM TABLE(ju6(5))");
        answer(
            "CREATE OR REPLACE FUNCTION ju7(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = "
                + "'Gen.process' AS $$\nimport java.util.stream.Stream;\nclass Out { public long a; public Out(long "
                + "a) { this.a = a; } }\nclass Gen { public static Class getOutputClass() { return Out.class; } "
                + "public Stream<Out> process(long n) { return Stream.of(new Out(n)); } }\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function JU7",
            "SELECT * FROM TABLE(ju7(5))");
        assertAnswer(
            "Function JU8 successfully created.",
            "CREATE OR REPLACE FUNCTION ju8(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { public long a; public Out(long a) { this.a = a; "
                + "} }\nclass Gen { int seen = 0; public Gen() { seen = 100; } public static Class getOutputClass() "
                + "{ return Out.class; } public Stream<Out> process(long n) { seen++; return Stream.of(new "
                + "Out(seen)); } public Stream<Out> endPartition() { throw new IllegalStateException(\"end fails \" "
                + "+ seen); } }\n$$");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.IllegalStateException: end fails 101 | "
                + "\tat function_handler_0//Gen.endPartition(InlineCode.java:4) |  in function JU8 with handler Gen",
            "SELECT * FROM TABLE(ju8(5))");
        assertAnswer(
            "REFUSED The return type of the \"process\" method is \"java.util.List\" but must be "
                + "java.util.stream.Stream in function JU9 with handler Gen",
            "CREATE OR REPLACE FUNCTION ju9(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { public long a; public Out(long a) { this.a = a; "
                + "} }\nclass Gen { public static Class getOutputClass() { return Out.class; } public "
                + "java.util.List<Out> process(long n) { return java.util.Arrays.asList(new Out(n)); } }\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function JU9",
            "SELECT * FROM TABLE(ju9(5))");
        assertAnswer(
            "Function JU10 successfully created.",
            "CREATE OR REPLACE FUNCTION ju10(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { public long a; public Out(long a) { this.a = a; "
                + "} }\nclass Gen { public static Class getOutputClass() { return Out.class; } public static "
                + "Stream<Out> process(long n) { return Stream.of(new Out(n)); } }\n$$");
        assertAnswer(
            "5",
            "SELECT * FROM TABLE(ju10(5))");
        assertAnswer(
            "REFUSED The output class \"Out\" is missing fields with the following names: A. Make sure the member "
                + "fields are public in function JU11 with handler Gen",
            "CREATE OR REPLACE FUNCTION ju11(n NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE JAVA HANDLER = 'Gen' AS "
                + "$$\nimport java.util.stream.Stream;\nclass Out { private long a; public Out(long a) { this.a = a; "
                + "} }\nclass Gen { public static Class getOutputClass() { return Out.class; } public Stream<Out> "
                + "process(long n) { return Stream.of(new Out(n)); } }\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function JU11",
            "SELECT * FROM TABLE(ju11(5))");
    }

    @Test
    public void aPartitionIsReadByOneInstance() {
        answer(
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        answer(
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10)");
        assertAnswer(
            "Function JT2 successfully created.",
            "CREATE OR REPLACE FUNCTION jt2(a VARCHAR, n NUMBER) RETURNS TABLE (name VARCHAR, total NUMBER, last "
                + "BOOLEAN) LANGUAGE JAVA HANDLER = 'Agg' AS $$\nimport java.util.stream.Stream;\nclass Out { public "
                + "String name; public long total; public boolean last; public Out(String name, long total, boolean "
                + "last) { this.name = name; this.total = total; this.last = last; } }\nclass Agg {\n  private long "
                + "sum = 0; private int rows = 0;\n  public static Class getOutputClass() { return Out.class; }\n  "
                + "public Stream<Out> process(String a, long n) { sum += n; rows++; return Stream.of(new Out(a, n, "
                + "false)); }\n  public Stream<Out> endPartition() { return Stream.of(new Out(\"end\" + rows, sum, "
                + "true)); }\n}\n$$");
        assertAnswer(
            "a|1|false ;; a|2|false ;; b|10|false ;; end1|10|true ;; end2|3|true",
            "SELECT f.name, f.total, f.last FROM ev, TABLE(jt2(ev.g, ev.v) OVER (PARTITION BY ev.g ORDER BY "
                + "ev.v)) f ORDER BY 3, 1, 2");
        assertAnswer(
            "a|1|false ;; a|2|false ;; b|10|false ;; end3|13|true",
            "SELECT f.name, f.total, f.last FROM ev, TABLE(jt2(ev.g, ev.v) OVER ()) f ORDER BY 3, 2");
        assertAnswer(
            "a|1|a|1 ;; a|2|a|2 ;; b|10|b|10 ;; b|null|end1|10 ;; a|null|end2|3",
            "SELECT ev.g, ev.v, f.name, f.total FROM ev, TABLE(jt2(ev.g, ev.v) OVER (PARTITION BY ev.g ORDER BY "
                + "ev.v)) f ORDER BY f.last, 3, 4");
    }
}
