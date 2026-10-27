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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UNDROP finds a dropped object by its exact canonical name and its exact containers, as SQL finds a live one: a
 * schema created as {@code "s1"} and one created as {@code S1} are two (live-verified). The catalog upper-cased the
 * names it kept dropped objects under, so {@code UNDROP TABLE S1.t} restored the table dropped from {@code "s1"} into
 * {@code S1}, and SHOW TABLES HISTORY listed it there. An UNDROP of a name a live object holds is refused and keeps the
 * dropped version, where a database or a tag used to replace the live one and a schema lost the version.
 */
public class UndropMatchesExactNamesTest {

    private DatabaseEngine engine;
    private final List<DatabaseEngine> copies = new ArrayList<>();
    private Path dir;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE u");
        engine.execute("USE SCHEMA u.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        for (final DatabaseEngine copy : copies) {
            copy.shutdown();
        }
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    private static List<String> column(final DatabaseEngine on, final String sql, final String name) {
        final ResultSet rs = on.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue(name)));
        }
        return out;
    }

    private static String refusal(final DatabaseEngine on, final String sql) {
        try {
            on.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aTableComesBackOnlyIntoTheSchemaAndDatabaseItWasDroppedFrom() {
        engine.execute("CREATE SCHEMA \"s1\"");
        engine.execute("CREATE SCHEMA S1");
        engine.execute("CREATE TABLE \"s1\".t (a INT)");
        engine.execute("INSERT INTO \"s1\".t VALUES (1)");
        engine.execute("DROP TABLE \"s1\".t");
        assertEquals(List.of(), column(engine, "SHOW TABLES HISTORY IN SCHEMA u.S1", "name"));
        assertEquals(List.of("T"), column(engine, "SHOW TABLES HISTORY IN SCHEMA u.\"s1\"", "name"));
        assertEquals("Table T did not exist or was purged.", refusal(engine, "UNDROP TABLE S1.t"));
        engine.execute("UNDROP TABLE \"s1\".t");
        assertEquals(List.of("1"), column(engine, "SELECT a FROM \"s1\".t", "A"));

        engine.execute("CREATE DATABASE \"d1\"");
        engine.execute("CREATE DATABASE D1");
        engine.execute("CREATE TABLE \"d1\".PUBLIC.x (a INT)");
        engine.execute("DROP TABLE \"d1\".PUBLIC.x");
        assertEquals("Table X did not exist or was purged.", refusal(engine, "UNDROP TABLE D1.PUBLIC.x"));
        engine.execute("UNDROP TABLE \"d1\".PUBLIC.x");
        assertEquals(List.of("X"), column(engine, "SHOW TABLES IN SCHEMA \"d1\".PUBLIC", "name"));
    }

    @Test
    public void aContainerIsMatchedExactlyToo() {
        engine.execute("CREATE SCHEMA \"s2\"");
        engine.execute("CREATE TABLE \"s2\".t (a INT)");
        engine.execute("DROP TABLE \"s2\".t");
        final String refused = refusal(engine, "UNDROP TABLE u.S2.t");
        assertTrue(refused.contains("Schema 'U.S2' does not exist or not authorized."), refused);
        engine.execute("UNDROP TABLE u.\"s2\".t");
    }

    @Test
    public void schemasDatabasesTagsAndDynamicTablesAreMatchedExactly() {
        engine.execute("CREATE SCHEMA \"sc\"");
        engine.execute("CREATE SCHEMA SC");
        engine.execute("DROP SCHEMA \"sc\"");
        assertEquals("Schema SC did not exist or was purged.", refusal(engine, "UNDROP SCHEMA SC"));
        engine.execute("UNDROP SCHEMA \"sc\"");

        engine.execute("CREATE DATABASE \"dd\"");
        engine.execute("DROP DATABASE \"dd\"");
        assertEquals("Database DD did not exist or was purged.", refusal(engine, "UNDROP DATABASE DD"));
        engine.execute("UNDROP DATABASE \"dd\"");

        engine.execute("USE SCHEMA u.PUBLIC");
        engine.execute("CREATE TAG \"tg\"");
        engine.execute("DROP TAG \"tg\"");
        assertEquals("Tag TG did not exist or was purged.", refusal(engine, "UNDROP TAG TG"));
        engine.execute("UNDROP TAG \"tg\"");

        // A dynamic table's own name is folded when it is created, so only its containers tell two apart.
        engine.execute("CREATE SCHEMA \"s4\"");
        engine.execute("CREATE SCHEMA S4");
        engine.execute("CREATE TABLE \"s4\".src (a INT)");
        engine.execute("CREATE DYNAMIC TABLE \"s4\".dt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH"
            + " AS SELECT a FROM \"s4\".src");
        engine.execute("DROP DYNAMIC TABLE \"s4\".dt");
        assertEquals("Dynamic_table DT did not exist or was purged.", refusal(engine, "UNDROP DYNAMIC TABLE S4.dt"));
        engine.execute("UNDROP DYNAMIC TABLE \"s4\".dt");
        assertEquals(List.of("DT"), column(engine, "SHOW DYNAMIC TABLES IN SCHEMA u.\"s4\"", "name"));
    }

    @Test
    public void anUndropOfANameALiveObjectHoldsIsRefusedAndKeepsTheVersion() {
        engine.execute("CREATE DATABASE ow");
        engine.execute("CREATE TABLE ow.PUBLIC.old_t (a INT)");
        engine.execute("DROP DATABASE ow");
        engine.execute("CREATE DATABASE ow");
        engine.execute("CREATE TABLE ow.PUBLIC.new_t (a INT)");
        assertEquals("SQL compilation error:\nObject 'OW' already exists.", refusal(engine, "UNDROP DATABASE ow"));
        assertEquals(List.of("NEW_T"), column(engine, "SHOW TABLES IN DATABASE ow", "name"),
            "the live database is left as it was");
        engine.execute("ALTER DATABASE ow RENAME TO ow_new");
        engine.execute("UNDROP DATABASE ow");
        assertEquals(List.of("OLD_T"), column(engine, "SHOW TABLES IN DATABASE ow", "name"));

        engine.execute("USE SCHEMA u.PUBLIC");
        engine.execute("CREATE TAG tg COMMENT = 'old'");
        engine.execute("DROP TAG tg");
        engine.execute("CREATE TAG tg COMMENT = 'new'");
        assertEquals("SQL compilation error:\nObject 'TG' already exists.", refusal(engine, "UNDROP TAG tg"));
        assertEquals(List.of("new"), column(engine, "SHOW TAGS LIKE 'TG'", "comment"));
        engine.execute("DROP TAG tg");
        engine.execute("UNDROP TAG tg");
        assertEquals(List.of("new"), column(engine, "SHOW TAGS LIKE 'TG'", "comment"), "the newest drop first");
        engine.execute("ALTER TAG tg RENAME TO tg_new");
        engine.execute("UNDROP TAG tg");
        assertEquals(List.of("old"), column(engine, "SHOW TAGS LIKE 'TG'", "comment"), "then the one kept");

        engine.execute("CREATE SCHEMA sx COMMENT = 'old'");
        engine.execute("DROP SCHEMA sx");
        engine.execute("CREATE SCHEMA sx COMMENT = 'new'");
        assertEquals("SQL compilation error:\nObject 'SX' already exists.", refusal(engine, "UNDROP SCHEMA u.sx"));
        engine.execute("ALTER SCHEMA u.sx RENAME TO sx_new");
        engine.execute("UNDROP SCHEMA u.sx");
        assertEquals(List.of("old"), column(engine, "SHOW SCHEMAS LIKE 'SX' IN DATABASE u", "comment"));
    }

    @Test
    public void exactNamesSurviveACloneAndARestore() throws Exception {
        engine.execute("CREATE SCHEMA \"s1\"");
        engine.execute("CREATE SCHEMA S1");
        engine.execute("CREATE TABLE \"s1\".t (a INT)");
        engine.execute("INSERT INTO \"s1\".t VALUES (5)");
        engine.execute("DROP TABLE \"s1\".t");
        engine.execute("CREATE SCHEMA \"q\"");
        engine.execute("DROP SCHEMA \"q\"");
        final DatabaseEngine clone = engine.cloneInstance();
        copies.add(clone);
        dir = Files.createTempDirectory("undrop_exact_");
        engine.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        copies.add(restored);
        restored.restoreStateFrom(dir);
        for (final DatabaseEngine copy : copies) {
            copy.execute("USE SCHEMA u.PUBLIC");
            assertEquals("Table T did not exist or was purged.", refusal(copy, "UNDROP TABLE S1.t"));
            copy.execute("UNDROP TABLE \"s1\".t");
            assertEquals(List.of("5"), column(copy, "SELECT a FROM \"s1\".t", "A"));
            assertEquals("Schema Q did not exist or was purged.", refusal(copy, "UNDROP SCHEMA Q"));
            copy.execute("UNDROP SCHEMA \"q\"");
        }
    }

    @Test
    public void aVersionKeptForNoTimeIsPurgedUnderItsExactName() {
        engine.execute("CREATE SCHEMA \"s3\"");
        engine.execute("CREATE TABLE \"s3\".t (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("DROP TABLE \"s3\".t");
        assertEquals("Table T did not exist or was purged.", refusal(engine, "UNDROP TABLE \"s3\".t"));
        assertEquals(List.of("T"), column(engine, "SHOW TABLES HISTORY IN SCHEMA u.\"s3\"", "name"));
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
