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
 * Two Scala sources that differ only in texts sharing a hash are two classes: each function runs its own
 * code. Every answer is live-verified.
 */
public class ScalaSourceCacheTest extends MeasuredCellsTestSupport {

    @Test
    public void twoSourcesThatShareAHashRunTheirOwnCode() {
        assertAnswer(
            "Function SC1 successfully created.",
            "CREATE OR REPLACE FUNCTION sc1(x NUMBER) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$object H { def f(x: Int): String = \"Aa\" }$$");
        assertAnswer(
            "Function SC2 successfully created.",
            "CREATE OR REPLACE FUNCTION sc2(x NUMBER) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$object H { def f(x: Int): String = \"BB\" }$$");
        assertAnswer(
            "Aa|BB",
            "SELECT sc1(1), sc2(1)");
        assertAnswer(
            "BB|Aa",
            "SELECT sc2(1), sc1(1)");
    }
}
