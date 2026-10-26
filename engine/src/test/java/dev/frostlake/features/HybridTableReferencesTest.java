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
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a hybrid table's foreign keys hold beyond a statement's own rows (documented): a table another hybrid table's
 * foreign key references is not truncated while the reference stands; a self-referencing table takes a row that names
 * itself; a transaction's lookups read the parent and child rows as the transaction has left them, its pending updates
 * and deletes counted; and a COPY holds the keys as an INSERT does. Engine only: hybrid tables are not available on
 * every account.
 */
public class HybridTableReferencesTest {

    private static final String PARENT_KEY =
        "Foreign key constraint \"SYS_INDEX_PLAYER_FOREIGN_KEY_TEAM_ID_TEAM_TEAM_ID\" was violated.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_TRANSACTION_DEFERRED_APPLY, "true");
        engine = new DatabaseEngine(config);
        engine.execute("CREATE DATABASE hr");
        engine.execute("USE SCHEMA hr.PUBLIC");
        engine.execute("CREATE HYBRID TABLE team (team_id INT PRIMARY KEY, team_name VARCHAR(40))");
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
    public void aReferencedTableIsNotTruncatedWhileTheReferenceStands() {
        assertEquals("Table 'TEAM' cannot be truncated as long as a FOREIGN KEY constraint references it.",
            refusal("TRUNCATE TABLE team"));
        assertEquals("1 | 2 | 3", rows("SELECT team_id FROM team ORDER BY team_id"));
        // The referencing table truncates, and so does the parent once the reference has gone.
        engine.execute("TRUNCATE TABLE player");
        assertEquals("Table 'TEAM' cannot be truncated as long as a FOREIGN KEY constraint references it.",
            refusal("TRUNCATE TABLE team"));
        engine.execute("DROP TABLE player");
        engine.execute("TRUNCATE TABLE team");
        assertEquals("", rows("SELECT team_id FROM team"));
    }

    @Test
    public void aSelfReferencingTableTakesARowThatNamesItself() {
        engine.execute("CREATE HYBRID TABLE staff (id INT PRIMARY KEY, boss INT REFERENCES staff (id))");
        engine.execute("INSERT INTO staff VALUES (1, 1)");
        engine.execute("INSERT INTO staff VALUES (2, 1), (3, 2)");
        final String key = "Foreign key constraint \"SYS_INDEX_STAFF_FOREIGN_KEY_BOSS_STAFF_ID\" was violated.";
        assertEquals(key, refusal("INSERT INTO staff VALUES (4, 9)"));
        assertEquals(key, refusal("INSERT INTO staff VALUES (5, NULL)"));
        assertEquals("1, 1 | 2, 1 | 3, 2", rows("SELECT id, boss FROM staff ORDER BY id"));
        // A self-referencing table holds nothing back from its own TRUNCATE.
        engine.execute("TRUNCATE TABLE staff");
        assertEquals("", rows("SELECT id FROM staff"));
    }

    @Test
    public void aTransactionsLookupsReadItsOwnPendingChanges() {
        engine.execute("BEGIN");
        engine.execute("DELETE FROM player WHERE team_id = 2");
        // No row the transaction still sees names team 2, so it leaves; and a new player can name it no more.
        engine.execute("DELETE FROM team WHERE team_id = 2");
        assertEquals(PARENT_KEY, refusal("INSERT INTO player VALUES (300, 'Brandt', 2)"));
        // A parent's key the transaction changed is the key a child names.
        engine.execute("UPDATE team SET team_id = 30 WHERE team_id = 3");
        engine.execute("INSERT INTO player VALUES (400, 'Openda', 30)");
        assertEquals(PARENT_KEY, refusal("INSERT INTO player VALUES (500, 'Simons', 3)"));
        engine.execute("COMMIT");
        assertEquals("1 | 30", rows("SELECT team_id FROM team ORDER BY team_id"));
        assertEquals("100, 1 | 400, 30", rows("SELECT player_id, team_id FROM player ORDER BY player_id"));
    }

    @Test
    public void aCopyHoldsTheForeignKeys() throws IOException {
        final Path dir = Files.createTempDirectory("hybrid_copy_");
        final Path file = Files.writeString(dir.resolve("players.csv"), "300,Brandt,9\n");
        try {
            engine.execute("CREATE STAGE hst");
            engine.execute("PUT 'file://" + file + "' @hst AUTO_COMPRESS = FALSE");
            final String refused = refusal("COPY INTO player FROM @hst FILE_FORMAT = (TYPE = CSV)");
            assertTrue(refused.contains(PARENT_KEY), refused);
            assertEquals("100 | 200", rows("SELECT player_id FROM player ORDER BY player_id"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
