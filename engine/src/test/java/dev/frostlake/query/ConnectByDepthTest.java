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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * CONNECT BY walks a hierarchy as deep as its data goes (live-verified); only a real cycle, a row reached again
 * below itself on its own path from the root, is refused, where the account runs it until the statement times out.
 */
public class ConnectByDepthTest extends BaseDatabaseTest {

    private static final String CYCLE = "CONNECT BY reached a row again below itself: the hierarchy contains a cycle.";

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE chain AS SELECT SEQ4() AS id, IFF(SEQ4() = 0, NULL, SEQ4() - 1) AS parent
            FROM TABLE(GENERATOR(ROWCOUNT => 1100))""");
    }

    @Test
    public void aDeepAcyclicChainIsWalkedToItsEnd() {
        assertCells(engine, new String[][] {
            {"SELECT MAX(lv), COUNT(*) FROM (SELECT LEVEL AS lv FROM chain START WITH parent IS NULL"
                + " CONNECT BY parent = PRIOR id)", "1100, 1100"},
            {"SELECT MAX(lv) FROM (SELECT LEVEL AS lv FROM chain START WITH id = 100 CONNECT BY parent = PRIOR id)",
                "1000"},
        });
    }

    @Test
    public void aRowReachedOnTwoSeparatePathsIsNoCycle() {
        engine.execute("CREATE TABLE edges (child INT, parent INT)");
        engine.execute("INSERT INTO edges VALUES (1, NULL), (2, 1), (3, 1), (4, 2), (4, 3)");
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*), MAX(lv) FROM (SELECT LEVEL AS lv FROM edges START WITH parent IS NULL"
                + " CONNECT BY parent = PRIOR child)", "5, 3"},
        });
    }

    @Test
    public void aRealCycleIsRefused() {
        assumeFalse(isLiveSnowflake(), "the account runs a cycle until the statement times out");
        engine.execute("CREATE TABLE ring (id INT, parent INT)");
        engine.execute("INSERT INTO ring VALUES (1, 3), (2, 1), (3, 2)");
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) FROM ring START WITH id = 1 CONNECT BY parent = PRIOR id", CYCLE},
            {"SELECT COUNT(*) FROM ring START WITH id = 1 CONNECT BY id = PRIOR id", CYCLE},
        });
    }
}
