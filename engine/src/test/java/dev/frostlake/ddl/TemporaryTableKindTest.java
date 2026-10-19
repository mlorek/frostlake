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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What kind of table a CLONE or a LIKE makes over a temporary or a transient source, and how
 * INFORMATION_SCHEMA.TABLES reports a temporary table: neither transient nor not, with no retention. A clone is of
 * the kind its statement writes, a permanent clone of a temporary or transient source is refused, and LIKE never
 * takes the source's kind (live-verified).
 */
public class TemporaryTableKindTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE kp (a INT)");
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE kt (b VARCHAR)");
        engine.execute("CREATE OR REPLACE TRANSIENT TABLE kr (c INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String kind(final String table) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        assertTrue(rs.next(), table);
        return String.valueOf(rs.getValue("kind"));
    }

    private ResultSet information(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT table_type, is_transient, retention_time"
            + " FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name = '" + table + "'");
        assertTrue(rs.next(), table);
        return rs;
    }

    @Test
    public void aTemporaryTableIsNeitherTransientNorNotAndKeepsNoHistory() {
        final ResultSet temporary = information("KT");
        assertEquals("LOCAL TEMPORARY", temporary.getValue(0));
        assertNull(temporary.getValue(1));
        assertEquals(0, ((Number) temporary.getValue(2)).intValue());
        final ResultSet transientTable = information("KR");
        assertEquals("BASE TABLE", transientTable.getValue(0));
        assertEquals("YES", transientTable.getValue(1));
        assertEquals(1, ((Number) transientTable.getValue(2)).intValue());
        final ResultSet permanent = information("KP");
        assertEquals("BASE TABLE", permanent.getValue(0));
        assertEquals("NO", permanent.getValue(1));
        final ResultSet ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'kt')");
        assertTrue(ddl.next());
        assertTrue(String.valueOf(ddl.getValue(0)).startsWith("create or replace TEMPORARY TABLE KT ("),
            String.valueOf(ddl.getValue(0)));
    }

    @Test
    public void aPermanentCloneOfATemporaryOrTransientTableIsRefused() {
        assertEquals("SQL compilation error: Temp table cannot be cloned to a permanent table; clone to a transient"
            + " table instead.", refusal("CREATE TABLE kc2 CLONE kt"));
        assertEquals("SQL compilation error: Temp table cannot be cloned to a permanent table; clone to a transient"
            + " table instead.", refusal("CREATE OR REPLACE TABLE kc2 CLONE kt"));
        assertEquals("SQL compilation error: Transient object cannot be cloned to a permanent object.",
            refusal("CREATE TABLE kc5 CLONE kr"));
    }

    @Test
    public void aCloneIsOfTheKindItsStatementWrites() {
        engine.execute("CREATE TRANSIENT TABLE kc3 CLONE kt");
        engine.execute("CREATE TEMPORARY TABLE kc4 CLONE kt");
        engine.execute("CREATE TRANSIENT TABLE kc6 CLONE kr");
        engine.execute("CREATE TEMPORARY TABLE kc7 CLONE kr");
        engine.execute("CREATE TEMPORARY TABLE kc8 CLONE kp");
        engine.execute("CREATE TRANSIENT TABLE kc9 CLONE kp");
        assertEquals("TRANSIENT", kind("KC3"));
        assertEquals("TEMPORARY", kind("KC4"));
        assertEquals("TRANSIENT", kind("KC6"));
        assertEquals("TEMPORARY", kind("KC7"));
        assertEquals("TEMPORARY", kind("KC8"));
        assertEquals("TRANSIENT", kind("KC9"));
        assertEquals("YES", information("KC3").getValue(1));
        assertNull(information("KC7").getValue(1));
        assertEquals(0, ((Number) information("KC7").getValue(2)).intValue());
    }

    @Test
    public void likeNeverTakesTheSourcesKind() {
        engine.execute("CREATE TABLE kl1 LIKE kt");
        engine.execute("CREATE TABLE kl2 LIKE kr");
        engine.execute("CREATE TEMPORARY TABLE kl3 LIKE kp");
        engine.execute("CREATE TABLE ks1 AS SELECT * FROM kt");
        assertEquals("TABLE", kind("KL1"));
        assertEquals("TABLE", kind("KL2"));
        assertEquals("TEMPORARY", kind("KL3"));
        assertEquals("TABLE", kind("KS1"));
        assertEquals("NO", information("KL2").getValue(1));
    }
}
