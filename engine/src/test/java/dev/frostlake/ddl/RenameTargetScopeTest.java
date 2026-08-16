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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rename's NEW name resolves as any name a statement creates: against the session's current database
 * and schema. An unqualified new name therefore MOVES the table - or the view - into the current schema,
 * a two-part one into the current database, and a name already taken there is refused as it is written.
 * Every cell is live-verified.
 */
public class RenameTargetScopeTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void aRenamesNewNameResolvesInTheSessionsSchema() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P439_A");
            engine.execute("CREATE OR REPLACE DATABASE P439_B");
            engine.execute("CREATE OR REPLACE SCHEMA P439_A.S1");
            engine.execute("CREATE OR REPLACE SCHEMA P439_B.S2");
            engine.execute("CREATE OR REPLACE TABLE P439_A.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE TABLE P439_A.PUBLIC.U (x INT)");
            engine.execute("CREATE OR REPLACE VIEW P439_A.PUBLIC.V AS SELECT 1 AS x");
            engine.execute("CREATE OR REPLACE TABLE P439_A.PUBLIC.W (x INT)");
            engine.execute("CREATE OR REPLACE TABLE P439_B.PUBLIC.W2 (x INT)");
            engine.execute("USE SCHEMA P439_B.PUBLIC");
            engine.execute("ALTER TABLE P439_A.PUBLIC.T RENAME TO T3");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P439_B.PUBLIC.T3"));
            assertRefused("SELECT COUNT(*) FROM P439_A.PUBLIC.T3",
                "SQL compilation error:\nObject 'P439_A.PUBLIC.T3' does not exist or not authorized.");
            engine.execute("ALTER TABLE P439_A.PUBLIC.U RENAME TO S2.U2");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P439_B.S2.U2"));
            assertRefused("SELECT COUNT(*) FROM P439_A.S2.U2",
                "SQL compilation error:\nSchema 'P439_A.S2' does not exist or not authorized.");
            engine.execute("ALTER VIEW P439_A.PUBLIC.V RENAME TO V2");
            assertEquals("1",
                rows("SELECT x FROM P439_B.PUBLIC.V2"));
            assertRefused("SELECT x FROM P439_A.PUBLIC.V2",
                "SQL compilation error:\nObject 'P439_A.PUBLIC.V2' does not exist or not authorized.");
            assertRefused("ALTER TABLE P439_A.PUBLIC.W RENAME TO W2",
                "SQL compilation error:\nObject 'W2' already exists.");
            engine.execute("USE SCHEMA P439_A.S1");
            engine.execute("CREATE OR REPLACE TABLE P439_A.PUBLIC.Z (x INT)");
            engine.execute("ALTER TABLE P439_A.PUBLIC.Z RENAME TO Z2");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P439_A.S1.Z2"));
            engine.execute("ALTER TABLE P439_A.S1.Z2 RENAME TO P439_A.PUBLIC.Z3");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P439_A.PUBLIC.Z3"));
            engine.execute("ALTER TABLE P439_A.PUBLIC.Z3 RENAME TO PUBLIC.Z4");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P439_A.PUBLIC.Z4"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P439_A");
            engine.execute("DROP DATABASE IF EXISTS P439_B");
        }
    }
}
