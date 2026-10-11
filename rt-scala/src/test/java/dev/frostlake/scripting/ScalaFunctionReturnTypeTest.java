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
 * What a Scala function answers by its declared RETURNS type, as a Java function does: a text read as JSON
 * for an ARRAY, an {@code Array[String]} an ARRAY of texts, a {@code Map[String, String]} an OBJECT and an
 * {@code Int} a whole number. Every answer is live-verified.
 */
public class ScalaFunctionReturnTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void anAnswerBecomesTheDeclaredType() {
        assertAnswer(
            "Function SAS successfully created.",
            "CREATE OR REPLACE FUNCTION sas(x VARCHAR) RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(x: String): String = x } $$");
        assertAnswer(
            "{\"k\":[1,2]}|ARRAY",
            "SELECT OBJECT_CONSTRUCT('k', sas('[1, 2]')), TYPEOF(sas('[1, 2]'))");
        assertAnswer(
            "Function SARR successfully created.",
            "CREATE OR REPLACE FUNCTION sarr(x VARCHAR) RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(x: String): Array[String] = Array(x, \"b\") } $$");
        assertAnswer(
            "[\"a\",\"b\"]|2",
            "SELECT sarr('a'), ARRAY_SIZE(sarr('a'))");
        assertAnswer(
            "Function SMAP successfully created.",
            "CREATE OR REPLACE FUNCTION smap(x VARCHAR) RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(x: String): Map[String, String] = Map(\"k\" -> x) } $$");
        assertAnswer(
            "{\"k\":\"v\"}",
            "SELECT smap('v')");
        assertAnswer(
            "Function SINT successfully created.",
            "CREATE OR REPLACE FUNCTION sint(x VARCHAR) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(x: String): Int = x.length } $$");
        assertAnswer(
            "3|INTEGER",
            "SELECT sint('abc'), TYPEOF(sint('abc')::VARIANT)");
    }
}
