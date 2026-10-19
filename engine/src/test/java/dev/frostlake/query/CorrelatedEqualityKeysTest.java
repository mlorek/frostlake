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

import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A correlated EXISTS, NOT EXISTS or scalar aggregate that reads the outer row through one equality of its WHERE is
 * computed once for every key, so its cost grows with its relations rather than with their product; its answers are
 * the account's whatever shape it takes, the shapes computed row by row included (live-verified).
 */
public class CorrelatedEqualityKeysTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE ca AS SELECT IFF(SEQ4() = 7, NULL, SEQ4()) AS id, TO_VARCHAR(SEQ4()) AS sid,
            SEQ4() % 7 AS ok FROM TABLE(GENERATOR(ROWCOUNT => 100))""");
        engine.execute("""
            CREATE TABLE cb AS SELECT IFF(SEQ4() % 11 = 0, NULL, SEQ4() % 60) AS id, SEQ4() % 7 AS k,
            TO_VARCHAR(SEQ4() % 60) AS sid FROM TABLE(GENERATOR(ROWCOUNT => 150))""");
    }

    @Test
    public void existenceOverOneEquality() {
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE NOT EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id)", "41"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE ca.id = cb.id AND cb.k > 3)", "50"},
            {"SELECT COUNT(*) FROM ca WHERE NOT EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id AND cb.k > 3)", "50"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS"
                + " (SELECT 1 FROM cb WHERE (cb.id = ca.id) AND (cb.k > 1 AND cb.k < 5))", "50"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb AS x WHERE x.id = ca.id + 1)", "58"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE id = ca.id)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.k = ok)", "100"},
            {"SELECT COUNT(*) FROM ca WHERE NOT EXISTS (SELECT 1 FROM cb WHERE cb.k = ok AND cb.id > 55)", "0"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.sid = ca.sid)", "60"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE CAST(cb.id AS FLOAT) = ca.id)", "59"},
            {"SELECT ca.id, EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id) AS e FROM ca ORDER BY 1 NULLS FIRST LIMIT 4",
                "null, false | 0, true | 1, true | 2, true"},
        });
    }

    @Test
    public void existenceOverOtherShapes() {
        engine.execute("CREATE VIEW cbv AS SELECT id, k FROM cb WHERE k <> 5");
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.sid = ca.id)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.sid)", "60"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE UPPER(cb.sid) = ca.sid)", "60"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS"
                + " (SELECT 1 FROM cb WHERE cb.sid = COLLATE(ca.sid || ' ', 'en-ci-rtrim'))", "60"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS"
                + " (SELECT 1 FROM cb WHERE '2020-01-01'::DATE + cb.id = '2020-01-01'::DATE + ca.id)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id AND cb.k = ca.ok)", "53"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id OR cb.k = 9)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cbv WHERE cbv.id = ca.id)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS"
                + " (SELECT 1 FROM cb JOIN ca AS a2 ON a2.id = cb.k WHERE cb.id = ca.id)", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id"
                + " AND EXISTS (SELECT 1 FROM cb AS c2 WHERE c2.k = cb.k AND c2.id > 50))", "59"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS"
                + " (SELECT 1 FROM cb WHERE cb.id = (SELECT MAX(id) FROM cb) AND ca.id > 90)", "9"},
        });
    }

    @Test
    public void anAggregateOverOneEquality() {
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM ca WHERE (SELECT MAX(k) FROM cb WHERE cb.id = ca.id) > 3", "50"},
            {"SELECT SUM((SELECT COUNT(*) FROM cb WHERE cb.id = ca.id)) FROM ca", "133"},
            {"SELECT SUM((SELECT COUNT(*) + 1 FROM cb WHERE cb.id = ca.id)) FROM ca", "233"},
            {"SELECT COUNT(*) FROM ca WHERE (SELECT COUNT(*) FROM cb WHERE cb.id = ca.id) = 0", "41"},
            {"SELECT COUNT_IF((SELECT MAX(k) FROM cb WHERE cb.id = ca.id) IS NULL) FROM ca", "41"},
            {"SELECT SUM(COALESCE((SELECT MAX(k) FROM cb WHERE cb.id = ca.id), -1)) FROM ca", "235"},
            {"SELECT SUM((SELECT COUNT(DISTINCT k) FROM cb WHERE cb.id = ca.id)) FROM ca", "133"},
            {"SELECT COUNT(*) FROM ca WHERE (SELECT SUM(k) FROM cb WHERE cb.id = ca.id AND cb.k <> 2) >= 6", "34"},
            {"SELECT (SELECT SUM(k) FROM cb WHERE cb.id = ca.id) AS s FROM ca WHERE ca.id = 1", "6"},
            {"SELECT ca.id, (SELECT MAX(k) FROM cb WHERE cb.id = ca.id) AS m FROM ca ORDER BY 1 NULLS FIRST LIMIT 5",
                "null, null | 0, 4 | 1, 5 | 2, 6 | 3, 4"},
        });
    }

    @Test
    public void theCostGrowsWithTheRelations() {
        assumeFalse(isLiveSnowflake(), "the account's own plan decides its cost");
        engine.execute("CREATE TABLE big_a AS SELECT SEQ4() AS id FROM TABLE(GENERATOR(ROWCOUNT => 20000))");
        engine.execute("CREATE TABLE big_b AS SELECT SEQ4() AS id, SEQ4() % 7 AS k"
            + " FROM TABLE(GENERATOR(ROWCOUNT => 20000))");
        // Row by row each of these runs its subquery 20,000 times over 20,000 rows, minutes of work; computed once
        // for every key each takes a fraction of a second, so the bound only separates the two.
        assertTimeout(Duration.ofSeconds(20), new Executable() {
            @Override
            public void execute() {
                assertCells(engine, new String[][] {
                    {"SELECT COUNT(*) FROM big_a WHERE EXISTS (SELECT 1 FROM big_b WHERE big_b.id = big_a.id)",
                        "20000"},
                    {"SELECT COUNT(*) FROM big_a WHERE NOT EXISTS (SELECT 1 FROM big_b WHERE big_b.id = big_a.id)",
                        "0"},
                    {"SELECT COUNT(*) FROM big_a WHERE (SELECT MAX(k) FROM big_b WHERE big_b.id = big_a.id) > 3",
                        "8571"},
                });
            }
        });
    }
}
