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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A hybrid table holds its keys as an UPDATE or a DELETE writes it, row by row and before any row is written
 * (documented): an UPDATE may not give two rows one primary key or one UNIQUE key, nor point a row's foreign key at a
 * parent row that does not exist, and neither an UPDATE nor a DELETE may take away a key value a child row still
 * names, the keys taking RESTRICT and NO ACTION alone. A UNIQUE key over several columns is held whole. A block's
 * handler reads the documented codes. Engine only: hybrid tables are not available on every account.
 */
public class HybridTableWriteKeysTest {

    private static final String PARENT_KEY =
        "Foreign key constraint \"SYS_INDEX_PLAYER_FOREIGN_KEY_TEAM_ID_TEAM_TEAM_ID\" was violated.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE hw");
        engine.execute("USE SCHEMA hw.PUBLIC");
        engine.execute("CREATE HYBRID TABLE team (team_id INT PRIMARY KEY, team_name VARCHAR(40) UNIQUE)");
        engine.execute("CREATE HYBRID TABLE player (player_id INT PRIMARY KEY, last_name VARCHAR(40), team_id INT,"
            + " FOREIGN KEY (team_id) REFERENCES team (team_id))");
        engine.execute("INSERT INTO team VALUES (1, 'Bayern'), (2, 'Dortmund'), (3, 'Leipzig')");
        engine.execute("INSERT INTO player VALUES (100, 'Kane', 1), (200, 'Reus', 2)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void anUpdateMayNotRepeatAPrimaryKeyOrAUniqueKey() {
        assertEquals("Primary key already exists", refusal("UPDATE team SET team_id = 1 WHERE team_id = 3"));
        assertEquals("Primary key already exists", refusal("UPDATE team SET team_id = 9 WHERE team_id >= 2"));
        assertEquals("Duplicate key value violates unique constraint \"SYS_INDEX_TEAM_UNIQUE_TEAM_NAME\"",
            refusal("UPDATE team SET team_name = 'Bayern' WHERE team_id = 3"));
        assertEquals("1, Bayern | 2, Dortmund | 3, Leipzig", rows("SELECT * FROM team ORDER BY team_id"));
        // Keys that swap or stay distinct are taken.
        engine.execute("UPDATE team SET team_name = 'L' || team_name WHERE team_id = 3");
        engine.execute("UPDATE team SET team_id = team_id + 10 WHERE team_id = 3");
        assertEquals("1, Bayern | 2, Dortmund | 13, LLeipzig", rows("SELECT * FROM team ORDER BY team_id"));
    }

    @Test
    public void anUpdateMayNotPointAForeignKeyAtNoParent() {
        assertEquals(PARENT_KEY, refusal("UPDATE player SET team_id = 7 WHERE player_id = 100"));
        assertEquals(PARENT_KEY, refusal("UPDATE player SET team_id = NULL WHERE player_id = 100"));
        engine.execute("UPDATE player SET team_id = 3 WHERE player_id = 100");
        assertEquals("100, 3 | 200, 2", rows("SELECT player_id, team_id FROM player ORDER BY player_id"));
    }

    @Test
    public void aParentRowAChildNamesMayNotLeave() {
        assertEquals(PARENT_KEY, refusal("DELETE FROM team WHERE team_id = 1"));
        assertEquals(PARENT_KEY, refusal("UPDATE team SET team_id = 11 WHERE team_id = 1"));
        // A row no child names leaves, and so does a parent's other column.
        engine.execute("DELETE FROM team WHERE team_id = 3");
        engine.execute("UPDATE team SET team_name = 'FCB' WHERE team_id = 1");
        engine.execute("DELETE FROM player WHERE team_id = 2");
        engine.execute("DELETE FROM team WHERE team_id = 2");
        assertEquals("1, FCB", rows("SELECT * FROM team ORDER BY team_id"));
    }

    @Test
    public void theJoinedFormsHoldTheKeysToo() {
        engine.execute("CREATE TABLE moves (player_id INT, team_id INT)");
        engine.execute("INSERT INTO moves VALUES (100, 9), (200, 3)");
        assertEquals(PARENT_KEY,
            refusal("UPDATE player SET team_id = m.team_id FROM moves m WHERE player.player_id = m.player_id"));
        assertEquals(PARENT_KEY, refusal("DELETE FROM team USING moves m WHERE team.team_id = 1 AND m.team_id = 9"));
        assertEquals("100, 1 | 200, 2", rows("SELECT player_id, team_id FROM player ORDER BY player_id"));
    }

    @Test
    public void aTransactionsUpdateHoldsTheKeys() {
        engine.execute("BEGIN");
        assertEquals("Primary key already exists", refusal("UPDATE team SET team_id = 2 WHERE team_id = 3"));
        assertEquals(PARENT_KEY, refusal("DELETE FROM team WHERE team_id = 2"));
        engine.execute("COMMIT");
        assertEquals("1, 2, 3", rows("SELECT LISTAGG(team_id, ', ') WITHIN GROUP (ORDER BY team_id) FROM team"));
    }

    @Test
    public void aUniqueKeyOverSeveralColumnsIsHeldWhole() {
        engine.execute("CREATE HYBRID TABLE pair (id INT PRIMARY KEY, a INT, b INT, UNIQUE (a, b))");
        engine.execute("INSERT INTO pair VALUES (1, 1, 1), (2, 1, 2), (3, NULL, 1), (4, NULL, 1)");
        assertEquals("Duplicate key value violates unique constraint \"SYS_INDEX_PAIR_UNIQUE_A_B\"",
            refusal("INSERT INTO pair VALUES (5, 1, 2)"));
        assertEquals("Duplicate key value violates unique constraint \"SYS_INDEX_PAIR_UNIQUE_A_B\"",
            refusal("UPDATE pair SET b = 1 WHERE id = 2"));
        assertEquals("4", rows("SELECT COUNT(*) FROM pair"));
    }

    @Test
    public void aHandlerReadsTheDocumentedCodes() {
        assertEquals("200001 22000", rows("""
            EXECUTE IMMEDIATE $$
            BEGIN
              UPDATE team SET team_id = 1 WHERE team_id = 2;
              RETURN 'written';
            EXCEPTION
              WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE;
            END;
            $$"""));
        assertEquals("200009 22000", rows("""
            EXECUTE IMMEDIATE $$
            BEGIN
              DELETE FROM team WHERE team_id = 1;
              RETURN 'written';
            EXCEPTION
              WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE;
            END;
            $$"""));
    }

    @Test
    public void anOrdinaryTableKeepsTakingRepeatedKeys() {
        engine.execute("CREATE TABLE plain (id INT PRIMARY KEY, v INT)");
        engine.execute("INSERT INTO plain VALUES (1, 1), (2, 2)");
        engine.execute("UPDATE plain SET id = 1");
        assertEquals("2", rows("SELECT COUNT(*) FROM plain WHERE id = 1"));
    }
}
