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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER SCHEMA a SWAP WITH b: the two schemas trade names, each keeping its objects and their rows; a schema swapped
 * with itself stays as it was; the schema to swap with is looked for first, IF EXISTS or not (live-verified). ALTER
 * DATABASE ... SWAP WITH does the same for two databases (documented; the account used for measuring creates no
 * database).
 */
public class SchemaSwapTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String first(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    /** The table names a SHOW TABLES lists, sorted and joined. */
    private String tables(final String show) {
        engine.execute(show);
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1")
                .getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void twoSchemasTradeTheirObjects() {
        engine.execute("CREATE SCHEMA s_a");
        engine.execute("CREATE SCHEMA s_b");
        engine.execute("CREATE TABLE s_a.t1 (a INT)");
        engine.execute("CREATE TABLE s_b.t2 (b INT)");
        engine.execute("INSERT INTO s_a.t1 VALUES (1)");
        engine.execute("INSERT INTO s_b.t2 VALUES (2)");
        engine.execute("ALTER SCHEMA s_a SWAP WITH s_b");
        assertEquals("T1", tables("SHOW TABLES IN SCHEMA s_b"));
        assertEquals("T2", tables("SHOW TABLES IN SCHEMA s_a"));
        assertEquals("1", first("SELECT a FROM s_b.t1"));
        assertEquals("2", first("SELECT b FROM s_a.t2"));
        engine.execute("ALTER SCHEMA s_a SWAP WITH s_a");
        assertEquals("T2", tables("SHOW TABLES IN SCHEMA s_a"));
    }

    @Test
    public void theSchemaToSwapWithIsLookedForFirst() {
        engine.execute("CREATE SCHEMA s_a");
        engine.execute("ALTER SCHEMA IF EXISTS nope_s SWAP WITH s_a");
        assertTrue(refusal("ALTER SCHEMA s_a SWAP WITH nope_s").contains("NOPE_S' does not exist"));
        assertTrue(refusal("ALTER SCHEMA IF EXISTS nope_s SWAP WITH nope_t").contains("NOPE_T' does not exist"));
    }

    @Test
    public void aSessionInASwappedSchemaFollowsTheName() {
        final String database = first("SELECT CURRENT_DATABASE()");
        engine.execute("CREATE SCHEMA s_a");
        engine.execute("CREATE SCHEMA s_b");
        engine.execute("CREATE TABLE s_b.t2 (b INT)");
        engine.execute("USE SCHEMA " + database + ".s_a");
        engine.execute("ALTER SCHEMA s_a SWAP WITH s_b");
        assertEquals("S_A", first("SELECT CURRENT_SCHEMA()"));
        assertEquals("T2", tables("SHOW TABLES"));
    }

    @Test
    public void twoDatabasesTradeTheirSchemas() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account used for measuring creates no database");
        engine.execute("CREATE DATABASE swap_d1");
        engine.execute("CREATE DATABASE swap_d2");
        engine.execute("CREATE TABLE swap_d1.PUBLIC.t1 (a INT)");
        engine.execute("INSERT INTO swap_d1.PUBLIC.t1 VALUES (1)");
        engine.execute("ALTER DATABASE swap_d1 SWAP WITH swap_d2");
        assertEquals("1", first("SELECT a FROM swap_d2.PUBLIC.t1"));
        assertEquals("", tables("SHOW TABLES IN SCHEMA swap_d1.PUBLIC"));
        engine.execute("ALTER DATABASE swap_d1 SWAP WITH swap_d1");
        assertEquals("T1", tables("SHOW TABLES IN SCHEMA swap_d2.PUBLIC"));
    }
}
