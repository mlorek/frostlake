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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW COLUMNS' CONTAINER scopes: SCHEMA, DATABASE and ACCOUNT list every column of every relation
 * they hold, rather than resolving one object.
 *
 * <p>The SCHEMA scope demands the FULL search path, with the same sentence the VIEW spelling uses —
 * and demands it BEFORE looking the schema up, so a schema that does not exist AND is named without
 * its database earns the qualification complaint rather than the does-not-exist one.
 *
 * <p>Counts are asserted only where this test owns every object being counted. A DATABASE or ACCOUNT
 * total depends on what else the account holds — a real one carries INFORMATION_SCHEMA — so those are
 * asserted as "at least mine, and accepted".
 */
public class ShowColumnsScopeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sq_a (x NUMBER, y NUMBER)");
        engine.execute("CREATE VIEW sq_v AS SELECT x FROM sq_a");
    }

    private ResultSet columns(final String sql) {
        return engine.executeQuery(sql);
    }

    private String refusalOf(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                columns(sql);
            }
        });
        final String flat = e.getMessage().replace('\n', ' ');
        final int at = flat.indexOf("error: ");
        return at < 0 ? flat.trim() : flat.substring(at + "error: ".length()).trim();
    }

    /** The schema scope lists every relation the schema holds — the table's two, the view's one. */
    @Test
    public void theSchemaScopeListsEveryRelationInIt() {
        assertEquals(3, columns("SHOW COLUMNS IN SCHEMA test_db.test_schema").getRows().size());
    }

    /** It demands the full search path, with the sentence the VIEW spelling uses. */
    @Test
    public void theSchemaScopeDemandsTheFullSearchPath() {
        assertEquals("Must specify the full search path starting from database for TEST_SCHEMA",
            refusalOf("SHOW COLUMNS IN SCHEMA test_schema"));
    }

    /** And it demands it BEFORE looking the schema up. */
    @Test
    public void qualificationIsCheckedBeforeExistence() {
        assertEquals("Must specify the full search path starting from database for NO_SUCH_SCHEMA",
            refusalOf("SHOW COLUMNS IN SCHEMA no_such_schema"));
        assertEquals(hinted("Schema 'TEST_DB.NO_SUCH_SCHEMA' does not exist or not authorized."),
            refusalOf("SHOW COLUMNS IN SCHEMA test_db.no_such_schema"));
    }

    /** The database scope spans its schemas, and names a missing database its own way. */
    @Test
    public void theDatabaseScopeSpansItsSchemas() {
        assertTrue(columns("SHOW COLUMNS IN DATABASE test_db").getRows().size() >= 3,
            "the database scope should include this schema's three columns at least");
        assertEquals(hinted("Database 'NO_SUCH_DB' does not exist or not authorized."),
            refusalOf("SHOW COLUMNS IN DATABASE no_such_db"));
    }

    /** The account scope takes no name and spans everything. */
    @Test
    public void theAccountScopeTakesNoName() {
        assertTrue(columns("SHOW COLUMNS IN ACCOUNT").getRows().size() >= 3,
            "the account scope should include this schema's three columns at least");
    }
}
