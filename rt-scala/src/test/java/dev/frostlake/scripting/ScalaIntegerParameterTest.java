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
 * A Scala function's {@code Int} and {@code Long} parameters take a NUMBER as a Java handler's do: a fraction
 * is rounded away by the parameter's SQL type before the handler runs, and a value outside the type's range
 * is refused, never wrapped - {@code Number out of representable range: type int32, value 3000000000}.
 * Every answer is live-verified.
 */
public class ScalaIntegerParameterTest extends MeasuredCellsTestSupport {

    @Test
    public void anIntOrLongParameterIsNeverWrappedOrTruncated() {
        assertAnswer(
            "Function SF successfully created.",
            "CREATE OR REPLACE FUNCTION sf(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Int): Int = a + 1 } $$");
        assertAnswer(
            "2",
            "SELECT sf(1)");
        assertAnswer(
            "REFUSED Number out of representable range: type int32, value 3000000000 in function SF with handler "
                + "H.f",
            "SELECT sf(3000000000)");
        assertAnswer(
            "3",
            "SELECT sf(1.99)");
        assertAnswer(
            "Function SL successfully created.",
            "CREATE OR REPLACE FUNCTION sl(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(a: Long): Long = a + 1 } $$");
        assertAnswer(
            "2",
            "SELECT sl(1)");
        assertAnswer(
            "REFUSED Number out of representable range: type int64, value 99999999999999999999999 in function SL "
                + "with handler H.f",
            "SELECT sl(99999999999999999999999)");
        assertAnswer(
            "3",
            "SELECT sl(1.5)");
    }
}
