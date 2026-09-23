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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER SCHEMA … RENAME TO renames a schema with its members, and a new name that places it in a database —
 * written, or the session's for a bare one — moves it there: its tables and their rows are read under the new
 * name, and the old name is gone. A third part names nothing, and the target's database must exist and hold no
 * schema of the name. Live-verified.
 */
public class AlterSchemaRenameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE DATABASE P549_DB");
        engine.execute("CREATE OR REPLACE DATABASE P549_DB2");
        engine.execute("CREATE SCHEMA P549_DB.S1");
        engine.execute("CREATE TABLE P549_DB.S1.T (x INT)");
        engine.execute("INSERT INTO P549_DB.S1.T VALUES (7)");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP DATABASE IF EXISTS P549_DB");
        engine.execute("DROP DATABASE IF EXISTS P549_DB2");
    }

    /** The schema names SHOW lists in a database, a comma between them. */
    private String schemas(final String database, final String like) {
        final ResultSet rs = engine.executeQuery("SHOW TERSE SCHEMAS LIKE '" + like + "' IN DATABASE " + database);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(row.getValue(rs.getColumnIndex("name")));
        }
        return out.toString();
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aRenamedSchemaKeepsItsTables() {
        engine.execute("USE DATABASE P549_DB");
        engine.execute("ALTER SCHEMA P549_DB.S1 RENAME TO S8");
        assertEquals("S8", schemas("P549_DB", "S%"));
        assertEquals("7", scalar("SELECT x FROM P549_DB.S8.T"));
        assertEquals(hinted("SQL compilation error:\nSchema 'P549_DB.S1' does not exist or not authorized."),
            refusal("SELECT x FROM P549_DB.S1.T"));
        engine.execute("INSERT INTO P549_DB.S8.T VALUES (8)");
        assertEquals("2", scalar("SELECT COUNT(*) FROM P549_DB.S8.T"));
        engine.execute("ALTER SCHEMA P549_DB.S8 RENAME TO P549_DB.S9");
        assertEquals("S9", schemas("P549_DB", "S%"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM P549_DB.S9.T"));
    }

    @Test
    public void aNewNameInAnotherDatabaseMovesTheSchema() {
        engine.execute("ALTER SCHEMA P549_DB.S1 RENAME TO P549_DB2.S7");
        assertEquals("", schemas("P549_DB", "S%"));
        assertEquals("S7", schemas("P549_DB2", "S%"));
        assertEquals("7", scalar("SELECT x FROM P549_DB2.S7.T"));
        engine.execute("ALTER SCHEMA IF EXISTS P549_DB2.S7 RENAME TO P549_DB2.\"s6\"");
        assertEquals("s6", schemas("P549_DB2", "s%"));
        engine.execute("USE DATABASE P549_DB");
        engine.execute("ALTER SCHEMA P549_DB2.\"s6\" RENAME TO S5");
        assertEquals("S5", schemas("P549_DB", "S5"));
        assertEquals("7", scalar("SELECT x FROM P549_DB.S5.T"));
        engine.execute("ALTER SCHEMA P549_DB.PUBLIC RENAME TO P549_DB.PUB2");
        assertEquals("PUB2", schemas("P549_DB", "PUB%"));
    }

    @Test
    public void aTargetThatNamesNothingIsRefused() {
        engine.execute("CREATE SCHEMA P549_DB.EXISTING");
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("ALTER SCHEMA P549_DB.S1 RENAME TO X.P549_DB.S6"));
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("ALTER SCHEMA P549_DB.S1 RENAME TO P549_DB..S4"));
        assertEquals(hinted("SQL compilation error:\nDatabase 'NOSUCH_DB' does not exist or not authorized."),
            refusal("ALTER SCHEMA P549_DB.S1 RENAME TO NOSUCH_DB.S6"));
        assertEquals("SQL compilation error:\nObject 'P549_DB.EXISTING' already exists.",
            refusal("ALTER SCHEMA P549_DB.S1 RENAME TO P549_DB.EXISTING"));
        assertEquals(hinted("SQL compilation error:\nSchema 'P549_DB.NOSUCH' does not exist or not authorized."),
            refusal("ALTER SCHEMA P549_DB.NOSUCH RENAME TO P549_DB.S3"));
        engine.execute("ALTER SCHEMA IF EXISTS P549_DB.NOSUCH RENAME TO P549_DB.S3");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on schema 'INFORMATION_SCHEMA'.",
            refusal("ALTER SCHEMA P549_DB.INFORMATION_SCHEMA RENAME TO P549_DB.IS2"));
        assertEquals("S1", schemas("P549_DB", "S1"));
    }
}
