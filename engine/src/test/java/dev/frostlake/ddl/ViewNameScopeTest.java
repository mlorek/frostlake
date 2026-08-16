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
 * A view's bare names resolve where the VIEW lives - its own database and schema - whatever the reading
 * session's context, a secure view, a materialized view and a view over a view alike; CURRENT_DATABASE()
 * and CURRENT_SCHEMA() inside the body answer the view's, and the session's context is unchanged after.
 * The body is compiled there at CREATE too, with no current database at all. Every cell is live-verified.
 */
public class ViewNameScopeTest extends BaseDatabaseTest {

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
    public void aViewsNamesResolveWhereTheViewLives() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P438_A");
            engine.execute("CREATE OR REPLACE DATABASE P438_B");
            engine.execute("CREATE OR REPLACE TABLE P438_A.PUBLIC.T (x INT)");
            engine.execute("INSERT INTO P438_A.PUBLIC.T VALUES (1)");
            engine.execute("CREATE OR REPLACE TABLE P438_B.PUBLIC.T (x INT)");
            engine.execute("INSERT INTO P438_B.PUBLIC.T VALUES (1), (2)");
            engine.execute("CREATE OR REPLACE SCHEMA P438_A.S1");
            engine.execute("CREATE OR REPLACE TABLE P438_A.S1.T (x INT)");
            engine.execute("CREATE OR REPLACE FUNCTION P438_A.PUBLIC.P438F() RETURNS INT AS '7'");
            engine.execute("CREATE OR REPLACE VIEW P438_A.PUBLIC.VC AS SELECT COUNT(*) AS n FROM t");
            engine.execute("CREATE OR REPLACE VIEW P438_A.PUBLIC.VV AS SELECT n FROM vc");
            engine.execute("CREATE OR REPLACE SECURE VIEW P438_A.PUBLIC.VS AS SELECT COUNT(*) AS n FROM t");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW P438_A.PUBLIC.MATV AS SELECT x FROM t");
            engine.execute("CREATE OR REPLACE VIEW P438_A.PUBLIC.VF AS SELECT p438f() AS f");
            engine.execute("USE SCHEMA P438_B.PUBLIC");
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VC"));
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VV"));
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VS"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM P438_A.PUBLIC.MATV"));
            assertEquals("7",
                rows("SELECT f FROM P438_A.PUBLIC.VF"));
            engine.execute("USE SCHEMA P438_A.S1");
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VC"));
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VV"));
            engine.execute("SHOW VIEWS LIKE 'VC' IN SCHEMA P438_A.PUBLIC");
            assertEquals("CREATE OR REPLACE VIEW P438_A.PUBLIC.VC AS SELECT COUNT(*) AS n FROM t",
                rows("SELECT \"text\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("CREATE OR REPLACE DATABASE P438_IDLE");
            engine.execute("DROP DATABASE P438_IDLE");
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VC"));
            engine.execute("CREATE OR REPLACE VIEW P438_A.PUBLIC.W2 AS SELECT * FROM t");
            assertEquals("1",
                rows("SELECT COUNT(*) FROM P438_A.PUBLIC.W2"));
            assertRefused("CREATE OR REPLACE VIEW P438_A.PUBLIC.W4 AS SELECT * FROM nosuch",
                "SQL compilation error:\nObject 'P438_A.PUBLIC.NOSUCH' does not exist or not authorized.");
            engine.execute("CREATE OR REPLACE VIEW P438_A.PUBLIC.W5 AS SELECT * FROM S1.t");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P438_A.PUBLIC.W5"));
            engine.execute("CREATE OR REPLACE VIEW P438_A.PUBLIC.W6 AS SELECT * FROM P438_B.PUBLIC.t");
            assertEquals("2",
                rows("SELECT COUNT(*) FROM P438_A.PUBLIC.W6"));
            assertEquals("1",
                rows("SELECT n FROM P438_A.PUBLIC.VV"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P438_A");
            engine.execute("DROP DATABASE IF EXISTS P438_B");
        }
    }

    @Test
    public void currentDatabaseInsideAViewIsTheViews() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P438C_A");
            engine.execute("CREATE OR REPLACE DATABASE P438C_B");
            engine.execute("CREATE OR REPLACE SCHEMA P438C_A.S1");
            engine.execute("CREATE OR REPLACE TABLE P438C_A.S1.T (x INT)");
            engine.execute("INSERT INTO P438C_A.S1.T VALUES (1), (2), (3)");
            engine.execute("CREATE OR REPLACE VIEW P438C_A.S1.VCTX AS SELECT CURRENT_DATABASE() AS d, CURRENT_SCHEMA() AS s");
            engine.execute("CREATE OR REPLACE VIEW P438C_A.S1.VCNT AS SELECT COUNT(*) AS n FROM t");
            engine.execute("USE SCHEMA P438C_B.PUBLIC");
            assertEquals("P438C_A, S1",
                rows("SELECT d, s FROM P438C_A.S1.VCTX"));
            assertEquals("P438C_B, PUBLIC",
                rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
            assertEquals("3",
                rows("SELECT n FROM P438C_A.S1.VCNT"));
            assertEquals("P438C_B, PUBLIC",
                rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P438C_A");
            engine.execute("DROP DATABASE IF EXISTS P438C_B");
        }
    }
}
