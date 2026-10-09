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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER VIEW … SET SECURE and UNSET SECURE: the view becomes secure, or plain again, and its listing and its DDL
 * say so. The action stands alone, takes no IF EXISTS, and a table that takes the statement stays what it is.
 */
public class AlterViewSecureTest extends BaseDatabaseTest {

    private static final String SYNTAX = "SQL compilation error:\nsyntax error line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE VIEW nv AS SELECT 1 AS c");
        engine.execute("CREATE OR REPLACE TABLE nt (x INT)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String isSecure() {
        return scalar("SELECT is_secure FROM information_schema.views WHERE table_name = 'NV'");
    }

    @Test
    public void aViewIsMadeSecureAndPlainAgain() {
        assertEquals("NO", isSecure());
        engine.execute("ALTER VIEW nv SET SECURE");
        assertEquals("YES", isSecure());
        assertEquals("create or replace secure view NV(\n\tC\n) as SELECT 1 AS c;", scalar("SELECT GET_DDL('VIEW', 'NV')"));
        engine.execute("ALTER VIEW nv SET SECURE");
        assertEquals("YES", isSecure());
        engine.execute("ALTER VIEW nv UNSET SECURE");
        assertEquals("NO", isSecure());
        engine.execute("ALTER VIEW nv UNSET SECURE");
        assertEquals("NO", isSecure());
        assertEquals("1", scalar("SELECT c FROM nv"));
    }

    @Test
    public void theActionStandsAloneAndTakesNoIfExists() {
        assertEquals(SYNTAX + "25 unexpected 'COMMENT'.", refusalOf("ALTER VIEW nv SET SECURE COMMENT = 'x'"));
        assertEquals(SYNTAX + "26 unexpected ','.", refusalOf("ALTER VIEW nv UNSET SECURE, COMMENT"));
        assertEquals("Unsupported feature 'IF EXISTS in ALTER VIEW SET/UNSET SECURE'.",
            refusalOf("ALTER VIEW IF EXISTS nosuch_v SET SECURE"));
        assertEquals(hinted("SQL compilation error:\nView 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or not"
            + " authorized."), refusalOf("ALTER VIEW nosuch_v SET SECURE"));
        assertEquals("NO", isSecure());
    }

    @Test
    public void aTableTakesTheStatementAndStaysATable() {
        engine.execute("ALTER VIEW nt SET SECURE");
        engine.execute("ALTER VIEW nt UNSET SECURE");
        assertEquals("BASE TABLE", scalar("SELECT table_type FROM information_schema.tables WHERE table_name = 'NT'"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM nt"));
    }
}
