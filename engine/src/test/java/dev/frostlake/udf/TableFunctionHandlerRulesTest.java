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
 * A Java table function's getOutputClass is a static method, its process may be either, and an endPartition it
 * has is public, an instance's, and answers a Stream; a handler breaking a rule is refused as the function is
 * created, and no function is left (all live-verified).
 */
public class TableFunctionHandlerRulesTest extends MeasuredCellsTestSupport {

    @Test
    public void handlerMethods() {
        assertAnswer("REFUSED User Error Report:  | Java Stack Trace: | java.lang.NoSuchMethodError: getOutputClass | "
                + " | The getOutputClass() method must be static and return a java.lang.Class in function JT680A with "
                + "handler J680a",
            "CREATE OR REPLACE FUNCTION jt680a(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = 'J680a' AS "
                + "$$\nimport java.util.stream.Stream;\nclass R680 { public int n; public R680(int n) { this.n = n; "
                + "} }\npublic class J680a {\n  public Class getOutputClass() { return R680.class; }\n  public "
                + "Stream<R680> process(int k) { return Stream.of(new R680(k)); }\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function JT680A",
            "SELECT * FROM TABLE(jt680a(1))");
        assertAnswer("Function JT680B successfully created.",
            "CREATE OR REPLACE FUNCTION jt680b(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = 'J680b' AS "
                + "$$\nimport java.util.stream.Stream;\nclass R680 { public int n; public R680(int n) { this.n = n; "
                + "} }\npublic class J680b {\n  public static Class getOutputClass() { return R680.class; }\n  "
                + "public static Stream<R680> process(int k) { return Stream.of(new R680(k)); }\n}\n$$");
        assertAnswer("2", "SELECT * FROM TABLE(jt680b(2))");
        assertAnswer("REFUSED Optional endPartition() method must be public, non-static, and return a "
                + "java.util.stream.Stream in function JT680C with handler J680c",
            "CREATE OR REPLACE FUNCTION jt680c(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = 'J680c' AS "
                + "$$\nimport java.util.stream.Stream;\nclass R680 { public int n; public R680(int n) { this.n = n; "
                + "} }\npublic class J680c {\n  public static Class getOutputClass() { return R680.class; }\n  "
                + "public Stream<R680> process(int k) { return Stream.of(new R680(k)); }\n  public static "
                + "Stream<R680> endPartition() { return Stream.of(new R680(-1)); }\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function JT680C",
            "SELECT * FROM TABLE(jt680c(3)) ORDER BY 1");
        assertAnswer("Function JT680D successfully created.",
            "CREATE OR REPLACE FUNCTION jt680d(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = 'J680d' AS "
                + "$$\nimport java.util.stream.Stream;\nclass R680 { public int n; public R680(int n) { this.n = n; "
                + "} }\npublic class J680d {\n  public static Class getOutputClass() { return R680.class; }\n  "
                + "public Stream<R680> process(int k) { return Stream.of(new R680(k)); }\n  public Stream<R680> "
                + "endPartition() { return Stream.of(new R680(-1)); }\n}\n$$");
        assertAnswer("-1 ;; 4", "SELECT * FROM TABLE(jt680d(4)) ORDER BY 1");
        assertAnswer("REFUSED Optional endPartition() method must be public, non-static, and return a "
                + "java.util.stream.Stream in function JT680E with handler J680e",
            "CREATE OR REPLACE FUNCTION jt680e(k INT) RETURNS TABLE (n INT) LANGUAGE JAVA HANDLER = 'J680e' AS "
                + "$$\nimport java.util.stream.Stream;\nclass R680 { public int n; public R680(int n) { this.n = n; "
                + "} }\npublic class J680e {\n  public static Class getOutputClass() { return R680.class; }\n  "
                + "public Stream<R680> process(int k) { return Stream.of(new R680(k)); }\n  public void "
                + "endPartition() { }\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function JT680E",
            "SELECT * FROM TABLE(jt680e(5))");
    }
}
