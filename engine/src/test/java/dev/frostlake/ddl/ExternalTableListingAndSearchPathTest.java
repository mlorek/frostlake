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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two statements the engine used to turn away: the external-table listing, which parses and answers its
 * own columns although nothing can ever be listed here, and a SEARCH_PATH assignment, whose schemas are
 * resolved as the ALTER runs rather than when a later listing reads the path.
 */
public class ExternalTableListingAndSearchPathTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE xt_t (a INT)");
    }

    /** The message of the refusal a statement raises, newlines flattened. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " | ");
    }

    /** A listing's row count and its column names. */
    private String listing(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        int rows = 0;
        while (rs.next()) {
            rows++;
        }
        final StringBuilder names = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(column.getName());
        }
        return rows + " rows [" + names + "]";
    }

    /** The listing parses in every scope form and answers its nineteen columns and no rows. */
    @Test
    public void theExternalTableListingAnswersItsColumns() {
        final String expected = "0 rows [created_on,name,database_name,schema_name,invalid,invalid_reason,"
            + "owner,comment,stage,location,file_format_name,file_format_type,cloud,region,"
            + "notification_channel,last_refreshed_on,table_format,last_refresh_details,owner_role_type]";
        assertEquals(expected, listing("SHOW EXTERNAL TABLES"));
        assertEquals(expected, listing("SHOW EXTERNAL TABLES LIKE 'V5Q%'"));
        assertEquals(expected, listing("SHOW EXTERNAL TABLES IN DATABASE"));
        assertEquals(expected, listing("SHOW EXTERNAL TABLES IN SCHEMA"));
        assertEquals(expected, listing("SHOW EXTERNAL TABLES IN ACCOUNT"));
        assertEquals(expected, listing("SHOW EXTERNAL TABLES STARTS WITH 'V' LIMIT 5"));
    }

    /** A relation scope is refused, with the kind spelled as the account spells it. */
    @Test
    public void aRelationScopeIsRefused() {
        assertTrue(refusal("SHOW EXTERNAL TABLES LIKE 'V5Q%' IN TABLE xt_t")
            .contains("Cannot show objects of type EXTERNAL TABLE in TABLE"));
    }

    /** A SEARCH_PATH entry naming no schema is refused as the ALTER runs. */
    @Test
    public void aMissingSchemaInTheSearchPathIsRefused() {
        assertTrue(refusal("ALTER SESSION SET SEARCH_PATH = '$current, nosuch, $public'")
            .contains("Schema 'NOSUCH' does not exist"));
    }

    /** A quoted entry resolves exactly, and is echoed with its quotes. */
    @Test
    public void aQuotedEntryResolvesExactly() {
        engine.execute("CREATE SCHEMA IF NOT EXISTS s1");
        assertTrue(refusal("ALTER SESSION SET SEARCH_PATH = '$current, \"s1\"'")
            .contains("Schema '\"s1\"' does not exist"));
    }

    /** An empty path is refused outright, whatever it would have named. */
    @Test
    public void anEmptySearchPathIsRefused() {
        assertTrue(refusal("ALTER SESSION SET SEARCH_PATH = ''").contains("Search path cannot be empty."));
    }

    /** A path naming schemas that exist is accepted, and so is unsetting it. */
    @Test
    public void anExistingPathIsAccepted() {
        engine.execute("CREATE SCHEMA IF NOT EXISTS s1");
        engine.execute("ALTER SESSION SET SEARCH_PATH = 'S1'");
        engine.execute("ALTER SESSION UNSET SEARCH_PATH");
    }
}
