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
 * What a Java handler procedure receives for a VARIANT parameter (live-verified): a Variant whose text and JSON are the
 * argument's own, numbers, texts, booleans, binaries, objects and arrays alike, a JSON null a Variant of null and a SQL
 * NULL no Variant at all; a DATE or a text the parameter does not take is refused at the call.
 */
public class JavaSemiStructuredParameterTest extends MeasuredCellsTestSupport {

    /** A Java procedure over each kind of VARIANT argument. */
    @Test
    public void aVariantParameterHoldsTheArgumentsJson() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pvjv(v VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
                + "com.snowflake.snowpark_java.Session;\nimport com.snowflake.snowpark_java.types.Variant;\npublic "
                + "class H {\n  public String run(Session session, Variant v) {\n    return v == null ? \"null\" : "
                + "v.toString() + \"|\" + v.asJsonString();\n  }\n}\n$$",
                "Function PVJV successfully created."},
            {"CALL pvjv(TO_VARIANT(1.5::FLOAT))",
                "1.5|1.5"},
            {"CALL pvjv(TO_VARIANT(1.50))",
                "1.5|1.5"},
            {"CALL pvjv(TO_VARIANT(123456789012345678))",
                "123456789012345678|123456789012345678"},
            {"CALL pvjv(TO_VARIANT(X'AB'))",
                "ab|\"ab\""},
            {"CALL pvjv(TO_VARIANT(TRUE))",
                "true|true"},
            {"CALL pvjv(TO_VARIANT('x'))",
                "x|\"x\""},
            {"CALL pvjv(PARSE_JSON('{\"a\": 1, \"d\": \"2024-01-02\"}'))",
                "{\"a\":1,\"d\":\"2024-01-02\"}|{\"a\":1,\"d\":\"2024-01-02\"}"},
            {"CALL pvjv(ARRAY_CONSTRUCT(1, '2024-01-02'::DATE))",
                "[1,\"2024-01-02\"]|[1,\"2024-01-02\"]"},
            {"CALL pvjv(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 'b', X'AB'))",
                "{\"b\":\"AB\",\"d\":\"2024-01-02\"}|{\"b\":\"AB\",\"d\":\"2024-01-02\"}"},
            {"CALL pvjv(PARSE_JSON('null'))",
                "null|null"},
            {"CALL pvjv(NULL)",
                "null"},
            {"CALL pvjv('2024-01-02'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PVJV': (DATE)"},
            {"CALL pvjv(5)",
                "5|5"},
            {"CALL pvjv('x')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PVJV': (VARCHAR(1))"},
        });
    }
}
