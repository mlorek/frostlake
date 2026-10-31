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
 * A refusal raised in a SQL function's body is placed in the body, its frame's character counted, wherever the call
 * stands in its statement; the too-many-arguments sentence echoes a cast as the plan writes it, CAST(… AS …); and a
 * table function's positional argument after a named one is refused as illegal mixing at the call (all
 * live-verified).
 */
public class SqlFunctionCallRefusalsTest extends MeasuredCellsTestSupport {

    @Test
    public void callRefusals() {
        engine.execute("CREATE OR REPLACE TABLE people (name VARCHAR)");
        engine.execute("CREATE OR REPLACE FUNCTION c_grp2(g VARCHAR) RETURNS NUMBER AS 'SELECT COUNT(*) FROM (SELECT "
            + "name AS g FROM people GROUP BY g)'");
        engine.execute("CREATE OR REPLACE FUNCTION pick(d DATE) RETURNS DATE AS 'd'");
        engine.execute("CREATE OR REPLACE FUNCTION tov(a NUMBER, b VARCHAR) RETURNS TABLE (v VARCHAR) AS 'SELECT b'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | 'PEOPLE.NAME' in select clause is "
                + "neither an aggregate nor in the group by clause.",
            "SELECT c_grp2('zz')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | 'PEOPLE.NAME' in select clause is "
                + "neither an aggregate nor in the group by clause.",
            "SELECT 1, c_grp2('zz')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | 'PEOPLE.NAME' in select clause is "
                + "neither an aggregate nor in the group by clause.",
            "SELECT 1,\n  c_grp2('zz')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | 'PEOPLE.NAME' in select clause is "
                + "neither an aggregate nor in the group by clause.",
            "SELECT 1 + c_grp2('zz') AS x");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[PICK(CAST('2024-01-01' AS DATE), 1)] expected 1, got 2",
            "SELECT test_schema.pick('2024-01-01'::DATE, 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[PICK(CAST(1 AS NUMBER(5,2)), 2)] expected 1, got 2",
            "SELECT pick(1::NUMBER(5,2), 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[PICK(CAST('2024-01-01' AS DATE), 1)] expected 1, got 2",
            "SELECT pick(CAST('2024-01-01' AS DATE), 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[PICK(CAST('a' AS DATE), 1)] expected 1, got 2",
            "SELECT pick('a'::VARCHAR(3)::DATE, 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and "
                + "positional arguments for function TOV",
            "SELECT v FROM TABLE(tov(a => 1, 'x'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and "
                + "positional arguments for function TOV",
            "SELECT v FROM TABLE(tov(1, b => 'x'))");
        assertAnswer("x", "SELECT v FROM TABLE(tov(a => 1, b => 'x'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and "
                + "positional arguments for function TOV",
            "SELECT v FROM TABLE(tov(b => 'x', 2))");
    }
}
