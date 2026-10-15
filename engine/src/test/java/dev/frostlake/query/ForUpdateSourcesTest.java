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
 * A FOR UPDATE is refused as a feature over a query that reads a table, however deep — through a derived table, a
 * common table expression or a set operation's arm — and as no lock at all over one that reads none; a script's
 * RESULTSET and cursor take the clause and are refused for it when the RESULTSET is filled or the cursor opens
 * (live-verified).
 */
public class ForUpdateSourcesTest extends BaseDatabaseTest {

    private static final String NO_TABLE = "A 'FOR UPDATE' clause is not compatible with 'not selecting from a table'.";
    private static final String UNSUPPORTED = "Unsupported feature 'FOR UPDATE clause'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (1), (2)");
    }

    @Test
    public void aQueryThatReadsNoTableLocksNothing() {
        assertCells(engine, new String[][] {
            {"SELECT * FROM (VALUES (1)) v FOR UPDATE", NO_TABLE},
            {"SELECT * FROM VALUES (1) FOR UPDATE", NO_TABLE},
            {"SELECT 1 FROM (SELECT 1) FOR UPDATE", NO_TABLE},
            {"WITH c AS (SELECT 1 AS x) SELECT * FROM c FOR UPDATE", NO_TABLE},
            {"SELECT 1 UNION ALL SELECT 2 FOR UPDATE", NO_TABLE},
            {"SELECT 1/0 FOR UPDATE", NO_TABLE},
        });
    }

    @Test
    public void aQueryThatReadsATableAtAnyDepthIsRefused() {
        assertCells(engine, new String[][] {
            {"SELECT * FROM (SELECT id FROM fz) FOR UPDATE", UNSUPPORTED},
            {"WITH c AS (SELECT id FROM fz) SELECT * FROM c FOR UPDATE", UNSUPPORTED},
            {"SELECT * FROM (SELECT 1 AS x) d JOIN fz ON TRUE FOR UPDATE", UNSUPPORTED},
            {"SELECT 2 UNION ALL SELECT id FROM fz FOR UPDATE", UNSUPPORTED},
            {"SELECT nope FROM fz FOR UPDATE",
                "SQL compilation error: error line 1 at position 7|invalid identifier 'NOPE'"},
        });
    }

    @Test
    public void aResultSetsQueryIsRefusedWhenItIsFilled() {
        assertCells(engine, new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := (SELECT 1 FOR UPDATE); RETURN TABLE(r); END; $$",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 27 : " + NO_TABLE},
            {"EXECUTE IMMEDIATE $$ DECLARE r RESULTSET DEFAULT (SELECT id FROM fz FOR UPDATE); "
                + "BEGIN RETURN 1; END; $$",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : " + UNSUPPORTED},
            {"EXECUTE IMMEDIATE $$ BEGIN\nLET r RESULTSET := (SELECT id FROM fz FOR UPDATE);\nRETURN TABLE(r);\nEND; $$",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 20 : " + UNSUPPORTED},
        });
    }

    @Test
    public void aCursorsQueryIsRefusedWhenTheCursorOpens() {
        assertCells(engine, new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT id FROM fz FOR UPDATE; BEGIN OPEN c; RETURN 1; END; $$",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : " + UNSUPPORTED},
        });
    }
}
