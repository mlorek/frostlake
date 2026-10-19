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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every dropped version of a name can be undropped in turn, the newest first. Only the latest drop of a name was
 * kept, so the version dropped before it was gone, and an UNDROP refused because the name was taken lost the version
 * it had already taken.
 */
public class UndropVersionsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE u1");
        engine.execute("USE SCHEMA u1.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<String> column(final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue(name)));
        }
        return out;
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void eachDroppedVersionIsUndroppedInTurn() {
        engine.execute("CREATE TABLE d (v INT)");
        engine.execute("INSERT INTO d VALUES (1)");
        engine.execute("DROP TABLE d");
        engine.execute("CREATE TABLE d (v INT)");
        engine.execute("INSERT INTO d VALUES (2)");
        engine.execute("DROP TABLE d");

        engine.execute("UNDROP TABLE d");
        assertEquals(List.of("2"), column("SELECT v FROM d", "V"), "the newest first");
        engine.execute("ALTER TABLE d RENAME TO d_v2");
        engine.execute("UNDROP TABLE d");
        assertEquals(List.of("1"), column("SELECT v FROM d", "V"), "then the one dropped before it");
        // A table holding the name is the refusal, before the versions are looked at.
        assertEquals("SQL compilation error:\nObject 'D' already exists.", refusal("UNDROP TABLE d"));
        engine.execute("ALTER TABLE d RENAME TO d_v1");
        assertEquals("Table D did not exist or was purged.", refusal("UNDROP TABLE d"));
    }

    @Test
    public void anUndropRefusedForATakenNameKeepsTheVersion() {
        engine.execute("CREATE TABLE e (v INT)");
        engine.execute("INSERT INTO e VALUES (1)");
        engine.execute("DROP TABLE e");
        engine.execute("CREATE TABLE e (v INT)");
        assertEquals("SQL compilation error:\nObject 'E' already exists.", refusal("UNDROP TABLE e"));
        engine.execute("DROP TABLE e");
        engine.execute("UNDROP TABLE e");
        assertEquals(List.of(), column("SELECT v FROM e", "V"), "the newer, empty table first");
        engine.execute("DROP TABLE e");
        engine.execute("UNDROP TABLE e");
        engine.execute("ALTER TABLE e RENAME TO e_empty");
        engine.execute("UNDROP TABLE e");
        assertEquals(List.of("1"), column("SELECT v FROM e", "V"), "the refused UNDROP kept the first version");
    }

    @Test
    public void historyListsEveryDroppedVersion() {
        engine.execute("CREATE SCHEMA u1.h");
        engine.execute("DROP SCHEMA u1.h");
        engine.execute("CREATE SCHEMA u1.h");
        engine.execute("DROP SCHEMA u1.h");
        assertEquals(List.of("H", "H"), column("SHOW SCHEMAS HISTORY LIKE 'H' IN DATABASE u1", "name"));
        engine.execute("UNDROP SCHEMA u1.h");
        engine.execute("ALTER SCHEMA u1.h RENAME TO h2");
        engine.execute("UNDROP SCHEMA u1.h");
        assertEquals(List.of("H", "H2"), column("SHOW SCHEMAS LIKE 'H%' IN DATABASE u1", "name"));
    }
}
