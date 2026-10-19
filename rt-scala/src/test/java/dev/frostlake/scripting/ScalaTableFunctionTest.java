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
 * A Scala table function is held to the rules of a Java one's handler: a class without
 * {@code getOutputClass} is refused when the function is created, and no function is left. Every answer is
 * live-verified.
 */
public class ScalaTableFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void aScalaTableFunctionIsHeldToTheJavaRules() {
        answer(
            "CREATE OR REPLACE FUNCTION st2(n NUMBER) RETURNS TABLE (v NUMBER) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'Gen.process' AS $$ class Gen { def process(n: Long): Iterator[Long] = "
                + "Iterator(n, n + 1) } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function ST2",
            "SELECT * FROM TABLE(st2(5)) ORDER BY 1");
        assertAnswer(
            "REFUSED Failed to find a public method named \"getOutputClass\" with 0 arguments | The "
                + "getOutputClass() method is required for Java UDTFs in function ST3 with handler Gen",
            "CREATE OR REPLACE FUNCTION st3(n NUMBER) RETURNS TABLE (v NUMBER) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'Gen' AS $$ class Gen { def process(n: Long): Iterator[Long] = Iterator(n, n + "
                + "1) } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function ST3",
            "SELECT * FROM TABLE(st3(5)) ORDER BY 1");
    }
}
