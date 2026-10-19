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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A CONNECT BY query's columns keep their declared types and tags, LEVEL is a NUMBER(38,0), and expressions over
 * them are typed as without the hierarchy (live-verified).
 */
public class ConnectByTypesTest extends BaseDatabaseTest {

    private static final String HIERARCHY = " FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE e (id INT, mgr INT, w NUMBER(6,2))");
        engine.execute("INSERT INTO e VALUES (1, NULL, 1.25), (2, 1, 2.50), (3, 1, 3.00)");
    }

    @Test
    public void theHierarchysColumnsKeepTheirTypes() {
        assertCells(engine, new String[][] {
            {"SELECT SYSTEM$TYPEOF(id), SYSTEM$TYPEOF(w), SYSTEM$TYPEOF(LEVEL)" + HIERARCHY + " ORDER BY id LIMIT 1",
                "NUMBER(38,0)[SB1], NUMBER(6,2)[SB2], NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(LEVEL * 2)" + HIERARCHY + " LIMIT 1", "NUMBER(38,0)[SB16]"},
            {"SELECT id, w * 2 AS w2, LEVEL + 1 AS l1" + HIERARCHY + " ORDER BY id",
                "1, 2.50, 2 | 2, 5.00, 3 | 3, 6.00, 3"},
            {"SELECT AVG(w)" + HIERARCHY, "2.25000000"},
        });
    }
}
