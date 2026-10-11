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
 * A correlated scalar subquery the account cannot evaluate, written as the argument of a JavaScript function: the
 * refusal is placed, as it is under a built-in call, where the same subquery inside a SQL function's arguments is
 * refused in that function's name with no place given. Every answer is live-verified.
 */
public class JavaScriptFunctionArgumentSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void theRefusalIsPlaced() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE FUNCTION fj(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return X + 1'");
        assertAnswer(unsupportedAt(10),
            "SELECT fj((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(unsupportedAt(14),
            "SELECT ABS(fj((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(unsupportedAt(27),
            "SELECT id FROM fz WHERE fj((SELECT MAX(fz.id + g.v) FROM g)) > 0");
    }
}
