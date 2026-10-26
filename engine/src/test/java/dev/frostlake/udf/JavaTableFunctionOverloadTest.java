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
 * A Java table function whose handler has two process methods of the function's argument count is refused as it
 * is created, as a scalar handler with two such methods is; overloads of another count are taken. A handler
 * class with neither getOutputClass nor process is refused for getOutputClass first, and a VARIANT, an ARRAY and
 * an OBJECT are a process method's argument or an output field as the account maps them (all live-verified).
 */
public class JavaTableFunctionOverloadTest extends MeasuredCellsTestSupport {

    @Test
    public void handlerShapes() {
        assertAnswer("REFUSED Failed to find a public method named \"getOutputClass\" with 0 arguments | The "
                + "getOutputClass() method is required for Java UDTFs in function JX655 with handler H",
            "CREATE OR REPLACE FUNCTION jx655(a INT) RETURNS TABLE (b INT) LANGUAGE JAVA HANDLER = 'H' AS $$\nclass "
                + "H {\n}\n$$");
        assertAnswer("REFUSED Cannot determine which implementation of handler \"process\" to invoke since there are "
                + "multiple definitions with 1 arguments in function JO655 with handler H",
            "CREATE OR REPLACE FUNCTION jo655(a INT) RETURNS TABLE (b INT) LANGUAGE JAVA HANDLER = 'H' AS $$\nclass "
                + "OutRow {\n  public int b;\n  public OutRow(int b) { this.b = b; }\n}\nclass H {\n  public static "
                + "Class getOutputClass() { return OutRow.class; }\n  public java.util.stream.Stream<OutRow> "
                + "process(int a) { return java.util.stream.Stream.of(new OutRow(a)); }\n  public "
                + "java.util.stream.Stream<OutRow> process(String a) { return java.util.stream.Stream.of(new "
                + "OutRow(-1)); }\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function JO655", "SELECT * FROM TABLE(jo655(7))");
        assertAnswer("Function JV655 successfully created.",
            "CREATE OR REPLACE FUNCTION jv655(a VARIANT) RETURNS TABLE (v VARIANT, s VARCHAR) LANGUAGE JAVA PACKAGES "
                + "= ('com.snowflake:snowpark:latest') HANDLER = 'H' AS $$\nimport "
                + "com.snowflake.snowpark_java.types.Variant;\nclass OutRow {\n  public Variant v;\n  public String "
                + "s;\n  public OutRow(Variant v, String s) { this.v = v; this.s = s; }\n}\nclass H {\n  public "
                + "static Class getOutputClass() { return OutRow.class; }\n  public java.util.stream.Stream<OutRow> "
                + "process(Variant a) { return java.util.stream.Stream.of(new OutRow(a, a.toString())); }\n}\n$$");
        assertAnswer("{\"k\":[1,2]}|{\"k\":[1,2]}", "SELECT v, s FROM TABLE(jv655(PARSE_JSON('{\"k\":[1,2]}')))");
        assertAnswer("Function JA655 successfully created.",
            "CREATE OR REPLACE FUNCTION ja655(a ARRAY) RETURNS TABLE (b INT) LANGUAGE JAVA HANDLER = 'H' AS "
                + "$$\nclass OutRow {\n  public int b;\n  public OutRow(int b) { this.b = b; }\n}\nclass H {\n  "
                + "public static Class getOutputClass() { return OutRow.class; }\n  public "
                + "java.util.stream.Stream<OutRow> process(String[] a) { return java.util.stream.Stream.of(new "
                + "OutRow(a.length)); }\n}\n$$");
        assertAnswer("3", "SELECT * FROM TABLE(ja655(ARRAY_CONSTRUCT(1, 2, 3)))");
        assertAnswer("Function JQ655 successfully created.",
            "CREATE OR REPLACE FUNCTION jq655(a OBJECT) RETURNS TABLE (b INT) LANGUAGE JAVA HANDLER = 'H' AS "
                + "$$\nclass OutRow {\n  public int b;\n  public OutRow(int b) { this.b = b; }\n}\nclass H {\n  "
                + "public static Class getOutputClass() { return OutRow.class; }\n  public "
                + "java.util.stream.Stream<OutRow> process(java.util.Map<String, String> a) { return "
                + "java.util.stream.Stream.of(new OutRow(a.size())); }\n}\n$$");
        assertAnswer("2", "SELECT * FROM TABLE(jq655(OBJECT_CONSTRUCT('a', 1, 'b', 2)))");
        assertAnswer("Function JR655 successfully created.",
            "CREATE OR REPLACE FUNCTION jr655(n INT) RETURNS TABLE (arr ARRAY) LANGUAGE JAVA HANDLER = 'H' AS "
                + "$$\nclass OutRow {\n  public String[] arr;\n  public OutRow(String[] arr) { this.arr = arr; "
                + "}\n}\nclass H {\n  public static Class getOutputClass() { return OutRow.class; }\n  public "
                + "java.util.stream.Stream<OutRow> process(int n) { return java.util.stream.Stream.of(new OutRow(new "
                + "String[] {\"p\", \"q\"})); }\n}\n$$");
        assertAnswer("[\"p\",\"q\"]", "SELECT * FROM TABLE(jr655(2))");
    }

    @Test
    public void overloads() {
        assertAnswer("REFUSED Cannot determine which implementation of handler \"f\" to invoke since there are "
                + "multiple definitions with 1 arguments in function JO656 with handler H.f",
            "CREATE OR REPLACE FUNCTION jo656(a INT) RETURNS INT LANGUAGE JAVA HANDLER = 'H.f' AS $$\nclass H {\n  "
                + "public static int f(int a) { return a; }\n  public static int f(String a) { return -1; }\n}\n$$");
        assertAnswer("Function JO656B successfully created.",
            "CREATE OR REPLACE FUNCTION jo656b(a INT) RETURNS TABLE (b INT) LANGUAGE JAVA HANDLER = 'H' AS $$\nclass "
                + "OutRow {\n  public int b;\n  public OutRow(int b) { this.b = b; }\n}\nclass H {\n  public static "
                + "Class getOutputClass() { return OutRow.class; }\n  public java.util.stream.Stream<OutRow> "
                + "process(int a) { return java.util.stream.Stream.of(new OutRow(a)); }\n  public "
                + "java.util.stream.Stream<OutRow> process(int a, int c) { return java.util.stream.Stream.of(new "
                + "OutRow(-1)); }\n}\n$$");
        assertAnswer("7", "SELECT * FROM TABLE(jo656b(7))");
    }
}
