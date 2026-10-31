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
 * A Snowpark Variant built over a Java value that holds a Variant is refused with the way to it the account's
 * serializer names - each list's or map's class with the index or the key, an array's element by its own
 * class - while a top-level array of objects reads its members; an inline Java table function's class lasts
 * its call, over the rows a LATERAL call is made for (all live-verified).
 */
public class SnowparkVariantChainAndInlineClassTest extends MeasuredCellsTestSupport {

    @Test
    public void referenceChains() {
        engine.execute("CREATE OR REPLACE FUNCTION jv690(k INT) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
            + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\nimport "
            + "com.snowflake.snowpark_java.types.Variant;\nimport java.util.ArrayList;\nimport "
            + "java.util.Arrays;\nimport java.util.Collections;\nclass H {\n  public static String f(int k) {\n  "
            + "  try {\n      Object v;\n      switch (k) {\n        case 1: v = Collections.singletonMap(\"k\", "
            + "Arrays.asList(new Variant(1), new Variant(\"x\"))); break;\n        case 2: v = "
            + "Collections.singletonMap(\"k\", Arrays.asList(Arrays.asList(new Variant(1)))); break;\n        "
            + "case 3: v = Collections.singletonMap(\"k\", Collections.singletonMap(\"j\", new Variant(1))); "
            + "break;\n        case 4: v = Collections.singletonMap(\"k\", Arrays.asList(1, "
            + "Collections.singletonMap(\"j\", new Variant(1)))); break;\n        case 5: v = Arrays.asList(new "
            + "Variant(1)); break;\n        case 6: v = Arrays.asList(Arrays.asList(new Variant(1))); break;\n   "
            + "     case 7: v = new Object[] {new Variant(1)}; break;\n        case 8: v = "
            + "Collections.singletonMap(\"k\", new Object[] {new Variant(1)}); break;\n        case 9: v = "
            + "Collections.singletonMap(\"k\", new Variant[] {new Variant(1)}); break;\n        default: v = new "
            + "ArrayList<Object>(Arrays.asList(\"a\", Collections.singletonMap(\"q\", Arrays.asList(new "
            + "Variant(2)))));\n      }\n      return new Variant(v).toString();\n    } catch (Exception e) {\n  "
            + "    return \"E:\" + e.getClass().getName() + \": \" + e.getMessage();\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jti690(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = "
            + "'T690' AS $$\nimport java.util.stream.Stream;\npublic class T690 {\n    static int calls;\n    "
            + "public static class OutputRow {\n        public int n;\n        public OutputRow(int n) { this.n "
            + "= n; }\n    }\n    public static Class getOutputClass() { return OutputRow.class; }\n    public "
            + "Stream<OutputRow> process(int k) {\n        calls++;\n        return Stream.of(new "
            + "OutputRow(calls * 10 + k));\n    }\n}\n$$");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.Arrays$ArrayList[0])",
            "SELECT jv690(1)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.Arrays$ArrayList[0]->java.util.Arrays$ArrayList[0])",
            "SELECT jv690(2)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.Collections$SingletonMap[\"j\"])",
            "SELECT jv690(3)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.Arrays$ArrayList[1]->java.util.Collections$SingletonMap[\"j\"])",
            "SELECT jv690(4)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.Arrays$ArrayList[0])",
            "SELECT jv690(5)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.Arrays$ArrayList[0]->java.util.Arrays$ArrayList[0])",
            "SELECT jv690(6)");
        assertAnswer("[1]", "SELECT jv690(7)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "com.snowflake.snowpark_java.types.Variant[0])",
            "SELECT jv690(8)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "com.snowflake.snowpark_java.types.Variant[0])",
            "SELECT jv690(9)");
        assertAnswer("E:java.lang.IllegalArgumentException: No serializer found for class "
                + "com.snowflake.snowpark_java.types.Variant and no properties discovered to create BeanSerializer "
                + "(to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: "
                + "java.util.ArrayList[1]->java.util.Collections$SingletonMap[\"q\"]->java.util.Arrays$ArrayList[0])",
            "SELECT jv690(10)");
    }

    @Test
    public void inlineClassOverRows() {
        engine.execute("CREATE OR REPLACE FUNCTION jv690(k INT) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
            + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$\nimport "
            + "com.snowflake.snowpark_java.types.Variant;\nimport java.util.ArrayList;\nimport "
            + "java.util.Arrays;\nimport java.util.Collections;\nclass H {\n  public static String f(int k) {\n  "
            + "  try {\n      Object v;\n      switch (k) {\n        case 1: v = Collections.singletonMap(\"k\", "
            + "Arrays.asList(new Variant(1), new Variant(\"x\"))); break;\n        case 2: v = "
            + "Collections.singletonMap(\"k\", Arrays.asList(Arrays.asList(new Variant(1)))); break;\n        "
            + "case 3: v = Collections.singletonMap(\"k\", Collections.singletonMap(\"j\", new Variant(1))); "
            + "break;\n        case 4: v = Collections.singletonMap(\"k\", Arrays.asList(1, "
            + "Collections.singletonMap(\"j\", new Variant(1)))); break;\n        case 5: v = Arrays.asList(new "
            + "Variant(1)); break;\n        case 6: v = Arrays.asList(Arrays.asList(new Variant(1))); break;\n   "
            + "     case 7: v = new Object[] {new Variant(1)}; break;\n        case 8: v = "
            + "Collections.singletonMap(\"k\", new Object[] {new Variant(1)}); break;\n        case 9: v = "
            + "Collections.singletonMap(\"k\", new Variant[] {new Variant(1)}); break;\n        default: v = new "
            + "ArrayList<Object>(Arrays.asList(\"a\", Collections.singletonMap(\"q\", Arrays.asList(new "
            + "Variant(2)))));\n      }\n      return new Variant(v).toString();\n    } catch (Exception e) {\n  "
            + "    return \"E:\" + e.getClass().getName() + \": \" + e.getMessage();\n    }\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jti690(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = "
            + "'T690' AS $$\nimport java.util.stream.Stream;\npublic class T690 {\n    static int calls;\n    "
            + "public static class OutputRow {\n        public int n;\n        public OutputRow(int n) { this.n "
            + "= n; }\n    }\n    public static Class getOutputClass() { return OutputRow.class; }\n    public "
            + "Stream<OutputRow> process(int k) {\n        calls++;\n        return Stream.of(new "
            + "OutputRow(calls * 10 + k));\n    }\n}\n$$");
        assertAnswer("11", "SELECT * FROM TABLE(jti690(1))");
        assertAnswer("11", "SELECT * FROM TABLE(jti690(1))");
        assertAnswer("11|12", "SELECT * FROM TABLE(jti690(1)) a, TABLE(jti690(2)) b");
        assertAnswer("11 ;; 22",
            "SELECT t.n FROM (SELECT 1 AS k UNION ALL SELECT 2) v, TABLE(jti690(v.k)) t ORDER BY 1");
        assertAnswer("11 ;; 22 ;; 33",
            "SELECT t.n FROM (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3) v, TABLE(jti690(v.k)) t ORDER BY 1");
        assertAnswer("11 ;; 22",
            "SELECT t.n FROM (SELECT 1 AS k UNION ALL SELECT 2) v, LATERAL jti690(v.k) t ORDER BY 1");
    }
}
