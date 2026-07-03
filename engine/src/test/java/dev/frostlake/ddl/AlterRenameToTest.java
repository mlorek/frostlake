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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER … RENAME accepts the canonical Snowflake {@code RENAME TO <name>} syntax (the {@code TO} keyword),
 * and RENAME COLUMN accepts {@code RENAME COLUMN a TO b}, while the no-TO form still parses.
 */
public class AlterRenameToTest extends BaseDatabaseTest {

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void renameTableWithTo() {
        engine.execute("CREATE TABLE r1 (id INTEGER)");
        engine.execute("INSERT INTO r1 VALUES (7)");
        engine.execute("ALTER TABLE r1 RENAME TO r2");
        assertEquals(1L, count("r2"));
    }

    @Test
    public void renameColumnWithTo() {
        engine.execute("CREATE TABLE rc (a INTEGER)");
        engine.execute("INSERT INTO rc VALUES (5)");
        engine.execute("ALTER TABLE rc RENAME COLUMN a TO b");
        final ResultSet rs = engine.executeQuery("SELECT b FROM rc");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void renameWithoutToStillParses() {
        engine.execute("CREATE TABLE r3 (id INTEGER)");
        engine.execute("ALTER TABLE r3 RENAME r4");
        assertEquals(0L, count("r4"));
    }
}
