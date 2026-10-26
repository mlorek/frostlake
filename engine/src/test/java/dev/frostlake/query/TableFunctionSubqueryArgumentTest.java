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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A table function's first argument that opens with a query in brackets is that query alone: an operator, a cast,
 * a path, a bracket or a predicate after it is a syntax error, inside TABLE( ... ) and after LATERAL, named or
 * not, with the account's second line - the second bracket after the fault, the call's own after LATERAL, or the
 * text past a comma read as the FROM list's next tables. A later argument, a bracketed value holding the query and
 * a scalar function's argument take any expression (all live-verified).
 */
public class TableFunctionSubqueryArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void subqueryArguments() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION tfn(x NUMBER) RETURNS TABLE (y NUMBER) AS 'SELECT x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '*'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) * 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '::'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2)::INT))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '-'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) - 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '/'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) / 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '||'. | syntax "
                + "error line 1 at position 80 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) || '1'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '='. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) = 3))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'IS'. | syntax "
                + "error line 1 at position 81 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) IS NULL))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 81 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1 + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 85 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + (SELECT 1)))");
        assertAnswer("4", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ((SELECT MAX(n) FROM g2) + 1)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 88 unexpected '=>'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1, TIMELIMIT => 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '::'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2)::INT + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '::'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) :: INT))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '['. | syntax "
                + "error line 1 at position 76 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2)[0]))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)) WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 2 at position 57 unexpected '+'. | syntax "
                + "error line 2 at position 61 unexpected ')'.",
            "SELECT COUNT(*)\nFROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 69 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', ','))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b'), ','))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(',' || (SELECT 'a,b'), ','))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '::'.",
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '::'.",
            "SELECT COUNT(*) FROM TABLE(FLATTEN((SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected '::'. | syntax "
                + "error line 1 at position 89 unexpected ')'.",
            "SELECT COUNT(*) FROM g1, LATERAL FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT)");
        assertAnswer("4", "SELECT ABS((SELECT MAX(n) FROM g2) + 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '+'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "SELECT * FROM TABLE(tfn((SELECT MAX(n) FROM g2) + 1))");
        assertAnswer("3", "SELECT * FROM TABLE(tfn((SELECT MAX(n) FROM g2)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 88 unexpected '=>'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1, TIMELIMIT => (SELECT "
                + "MAX(n) FROM g2) + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c' || ',d', ','))");
    }

    @Test
    public void recoveryLines() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION tfn(x NUMBER) RETURNS TABLE (y NUMBER) AS 'SELECT x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 81 unexpected '||'.",
            "SELECT COUNT(*) FROM g1, LATERAL FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2)) || '')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 66 unexpected '5'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', 5))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 67 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', x))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + ABS(1)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE('a,b', (SELECT ',') || ''))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '||'. | syntax "
                + "error line 1 at position 85 unexpected '=>'.",
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1)) || '', OUTER => TRUE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected '+'. | syntax "
                + "error line 1 at position 91 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 1 UNION ALL SELECT 2 LIMIT 1) + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected '+'. | syntax "
                + "error line 1 at position 97 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (WITH c AS (SELECT 1 AS n) SELECT n FROM c) + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'NOT'. | syntax "
                + "error line 1 at position 93 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) NOT BETWEEN 1 AND 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'IN'. | syntax "
                + "error line 1 at position 79 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) IN (1)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)) t, g1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)) AS t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected '::'. | syntax "
                + "error line 1 at position 80 unexpected ')'.",
            "SELECT COUNT(*) FROM g1, LATERAL FLATTEN((SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected '::'. | syntax "
                + "error line 1 at position 97 unexpected '=>'.",
            "SELECT COUNT(*) FROM g1, LATERAL FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT, OUTER => "
                + "TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '::'. | syntax "
                + "error line 1 at position 91 unexpected '=>'.",
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT, OUTER => TRUE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '::'. | syntax "
                + "error line 1 at position 89 unexpected '=>'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2)::INT, TIMELIMIT => 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '+'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "SELECT * FROM TABLE(tfn((SELECT MAX(n) FROM g2) + 1)) WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'COLLATE'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) COLLATE 'en'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected ':'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2):x))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '['. | syntax "
                + "error line 1 at position 78 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))[0]))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)) JOIN g1 ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)), g1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 69 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', ',')), g1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 87 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1, TIMELIMIT))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '+'. | syntax "
                + "error line 1 at position 65 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR((SELECT MAX(n) FROM g2) + 1))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 88 unexpected '=>'. | syntax error line 1 at position 103 unexpected '=>'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1, TIMELIMIT => 1, ROWCOUNT "
                + "=> 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 74 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', ',', 'x'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 70 unexpected '||'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', ',' || ''))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 71 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', '@st'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '||'. | syntax "
                + "error line 1 at position 70 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE((SELECT 'a,b') || ',c', ',' ))  WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax "
                + "error line 1 at position 77 unexpected ')'.",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1)) WHERE n = (SELECT 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '-'. | syntax "
                + "error line 1 at position 53 unexpected '2'.",
            "SELECT * FROM TABLE(tfn((SELECT MAX(n) FROM g2) - 1, 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '::'.",
            "SELECT COUNT(*) FROM g1, TABLE(FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))::VARIANT)) f");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected '['.",
            "SELECT COUNT(*) FROM g1, LATERAL FLATTEN(INPUT => (SELECT ARRAY_CONSTRUCT(1, 2))[0])");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected '||'. | syntax "
                + "error line 1 at position 75 unexpected ')'.",
            "SELECT COUNT(*) FROM g1, LATERAL SPLIT_TO_TABLE((SELECT 'a,b') || ',c', ',')");
    }
}
