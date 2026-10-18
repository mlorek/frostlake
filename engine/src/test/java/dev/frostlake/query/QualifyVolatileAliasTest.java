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
 * QUALIFY reads a select alias of a volatile item as the row shows it (live-verified): a sequence's NEXTVAL,
 * UNIFORM over RANDOM() and UUID_STRING() are computed once, so every row returned satisfies the predicate on its
 * own shown values.
 */
public class QualifyVolatileAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void aVolatileAliasIsReadAsShown() {
        engine.execute("CREATE OR REPLACE TABLE qt (a INT)");
        engine.execute("INSERT INTO qt VALUES (1), (2), (3), (4)");
        engine.execute("CREATE OR REPLACE TABLE qt2 AS SELECT SEQ4() AS a FROM TABLE(GENERATOR(ROWCOUNT => 20))");
        engine.execute("CREATE OR REPLACE SEQUENCE sq");
        assertCells(new String[][] {
            {"SELECT COUNT(*), BOOLAND_AGG(n <= 2) FROM (SELECT a, sq.NEXTVAL n FROM qt QUALIFY n <= 2 AND "
                + "ROW_NUMBER() OVER (ORDER BY a) > 0)",
                "2|true"},
            {"SELECT BOOLAND_AGG(r > 50) FROM (SELECT a, UNIFORM(1, 100, RANDOM()) r FROM qt2 QUALIFY r > 50 AND "
                + "ROW_NUMBER() OVER (ORDER BY a) > 0)",
                "true"},
            {"SELECT BOOLAND_AGG(LENGTH(u) = 36) FROM (SELECT a, UUID_STRING() u FROM qt QUALIFY u IS NOT NULL AND "
                + "ROW_NUMBER() OVER (ORDER BY a) > 0)",
                "true"},
        });
    }
}
