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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IDENTITY column functionality (auto-increment with custom start/increment), asserted through
 * the SQL surface — the {@code DESCRIBE TABLE} default cell spells the generator
 * ({@code IDENTITY START n INCREMENT m}) and the generated values are read back with ordered
 * queries — so every check runs against whichever engine executed the DDL/DML, embedded or live.
 *
 * <p>Snowflake guarantees identity values are unique and ascending, NOT that they are gap-free:
 * separate INSERT statements may draw from a fresh range (live-measured: single-row inserts read
 * 1, 2 and the next statement jumped to 101). Exact values are therefore asserted only WITHIN one
 * multi-row INSERT, which is contiguous; across statements the tests assert order and the seed.
 */
public class IdentityTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(IdentityTest.class);

    /** Asserts every generated id is distinct and ascending — all Snowflake guarantees. */
    private void assertAscendingDistinct(final ResultSet rs) {
        long previous = Long.MIN_VALUE;
        for (final Row row : rs.getRows()) {
            final long id = Long.parseLong(cell(rs, row, "ID"));
            assertTrue(id > previous, "identity values must ascend, saw " + id + " after " + previous);
            previous = id;
        }
    }

    /** Asserts the column's DESCRIBE default cell spells this identity generator. */
    private void assertIdentity(final String table, final String column, final long start, final long increment) {
        final String cellText = describeCell(table, column, "default");
        assertNotNull(cellText, column + " should carry an identity default");
        assertTrue(cellText.contains("IDENTITY START " + start + " INCREMENT " + increment),
            column + "'s default cell reads: " + cellText);
    }

    @Test
    public void testSimpleIdentity() {
        logger.info("Testing simple IDENTITY");
        engine.execute("CREATE TABLE test_identity (id INTEGER IDENTITY, name VARCHAR)");

        assertIdentity("test_identity", "ID", 1, 1);
    }

    @Test
    public void testIdentityWithStartAndIncrement() {
        logger.info("Testing IDENTITY with custom start and increment");
        engine.execute("CREATE TABLE test_identity_custom (id INTEGER IDENTITY(100, 5), name VARCHAR)");

        assertIdentity("test_identity_custom", "ID", 100, 5);
    }

    @Test
    public void testIdentityAutoInsert() {
        logger.info("Testing IDENTITY auto-generates values on INSERT");
        engine.execute("CREATE TABLE test_identity (id INTEGER IDENTITY, name VARCHAR)");
        engine.execute("INSERT INTO test_identity (name) VALUES ('Alice')");
        engine.execute("INSERT INTO test_identity (name) VALUES ('Bob')");
        engine.execute("INSERT INTO test_identity (name) VALUES ('Charlie')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_identity ORDER BY id");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());

        // The seed is exact, so the first row is pinned — but identity values across LATER
        // single-row INSERTs are only unique and ascending-per-statement, not statement-ordered
        // (a real account can give the third INSERT a smaller value than the second), so the
        // remaining names are asserted as a set, not by position.
        assertEquals("1", cell(rs, rs.getRows().get(0), "ID"));
        assertEquals("Alice", cell(rs, rs.getRows().get(0), "NAME"));
        final Set<String> laterNames = new HashSet<String>();
        laterNames.add(cell(rs, rs.getRows().get(1), "NAME"));
        laterNames.add(cell(rs, rs.getRows().get(2), "NAME"));
        assertEquals(new HashSet<String>(Arrays.asList("Bob", "Charlie")), laterNames);
        assertAscendingDistinct(rs);
    }

    @Test
    public void testIdentityCustomAutoInsert() {
        logger.info("Testing IDENTITY with custom start/increment auto-generates values");
        engine.execute("CREATE TABLE test_identity_custom (id INTEGER IDENTITY(100, 5), name VARCHAR)");
        engine.execute("INSERT INTO test_identity_custom (name) VALUES ('Alice')");
        engine.execute("INSERT INTO test_identity_custom (name) VALUES ('Bob')");
        engine.execute("INSERT INTO test_identity_custom (name) VALUES ('Charlie')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_custom ORDER BY id");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());

        assertEquals("100", cell(rs, rs.getRows().get(0), "ID"));
        assertAscendingDistinct(rs);
    }

    @Test
    public void testIdentityWithExplicitValue() {
        logger.info("Testing IDENTITY with explicit value insertion");
        engine.execute("CREATE TABLE test_identity_explicit (id INTEGER IDENTITY, name VARCHAR)");
        engine.execute("INSERT INTO test_identity_explicit (id, name) VALUES (50, 'Explicit')");
        engine.execute("INSERT INTO test_identity_explicit (name) VALUES ('Auto')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_explicit ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        // ORDER BY id sorts the generated 1 before the explicit 50: the generator is not advanced
        // by an explicitly inserted value.
        assertEquals("1", cell(rs, rs.getRows().get(0), "ID"));
        assertEquals("Auto", cell(rs, rs.getRows().get(0), "NAME"));

        assertEquals("50", cell(rs, rs.getRows().get(1), "ID"));
        assertEquals("Explicit", cell(rs, rs.getRows().get(1), "NAME"));
    }

    @Test
    public void testIdentityLargeIncrement() {
        logger.info("Testing IDENTITY with large increment");
        engine.execute("CREATE TABLE test_identity_large (id INTEGER IDENTITY(1000, 1000), name VARCHAR)");

        engine.execute("INSERT INTO test_identity_large (name) VALUES ('A')");
        engine.execute("INSERT INTO test_identity_large (name) VALUES ('B')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_large ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        assertEquals("1000", cell(rs, rs.getRows().get(0), "ID"));
        assertAscendingDistinct(rs);
    }

    @Test
    public void testAutoIncrementBackwardCompatibility() {
        logger.info("Testing AUTOINCREMENT spelling works the same way");
        engine.execute("CREATE TABLE test_autoincrement (id INTEGER AUTOINCREMENT, name VARCHAR)");

        engine.execute("INSERT INTO test_autoincrement (name) VALUES ('Test1')");
        engine.execute("INSERT INTO test_autoincrement (name) VALUES ('Test2')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_autoincrement ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        assertEquals("1", cell(rs, rs.getRows().get(0), "ID"));
        assertAscendingDistinct(rs);
    }

    @Test
    public void testIdentityWithMultipleInserts() {
        logger.info("Testing IDENTITY with multiple VALUES in single INSERT");
        engine.execute("CREATE TABLE test_identity_multi (id INTEGER IDENTITY(10, 2), name VARCHAR)");

        engine.execute("INSERT INTO test_identity_multi (name) VALUES ('A'), ('B'), ('C')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_multi ORDER BY id");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());

        assertEquals("10", cell(rs, rs.getRows().get(0), "ID"));
        assertEquals("12", cell(rs, rs.getRows().get(1), "ID"));
        assertEquals("14", cell(rs, rs.getRows().get(2), "ID"));
    }

    @Test
    public void testIdentityPersistsAcrossQueries() {
        logger.info("Testing IDENTITY counter persists across queries");
        engine.execute("CREATE TABLE test_identity_persist (id INTEGER IDENTITY(1, 1), value INTEGER)");

        engine.execute("INSERT INTO test_identity_persist (value) VALUES (100)");
        engine.execute("INSERT INTO test_identity_persist (value) VALUES (200)");

        // Execute a different query in between
        engine.executeQuery("SELECT 1");

        // Continue inserting
        engine.execute("INSERT INTO test_identity_persist (value) VALUES (300)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_persist ORDER BY id");
        assertEquals(3, rs.getRowCount());

        // The generator keeps counting across the intervening query: three distinct ascending ids.
        assertEquals("1", cell(rs, rs.getRows().get(0), "ID"));
        assertAscendingDistinct(rs);
    }
}
