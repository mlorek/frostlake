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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake applies a column's DEFAULT (and AUTOINCREMENT) only when the column is OMITTED from an
 * INSERT — a column explicitly listed keeps its value, including an explicit NULL. The builder used to
 * treat "value is null" and "column not supplied" identically, so an explicit NULL was silently
 * replaced by the default (surfaced by a loader inserting {@code provider_name = NULL} into a column
 * with {@code DEFAULT 'NA'}: every comparison against the expected NULL failed).
 */
public class InsertExplicitNullVsDefaultTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR DEFAULT 'NA', score NUMBER(5,3) DEFAULT 0)");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    @Test
    public void anExplicitNullIsNotReplacedByTheDefault() {
        engine.execute("INSERT INTO t (id, name) VALUES (1, NULL)");
        final ResultSet rs = q("SELECT name, score FROM t");
        assertNull(rs.getRows().get(0).getValue(0));                             // explicit NULL kept
        assertEquals(0.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue());   // omitted → default
    }

    @Test
    public void anExplicitNullFromASelectSourceIsKeptToo() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, NULL)");
        engine.execute("INSERT INTO t (id, name) SELECT id, name FROM src");
        assertNull(q("SELECT name FROM t").getRows().get(0).getValue(0));
    }

    @Test
    public void aPositionalNullIsExplicitAndTrailingColumnsDefault() {
        engine.execute("INSERT INTO t VALUES (1, NULL, 0.5)");
        assertNull(q("SELECT name FROM t").getRows().get(0).getValue(0));
        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR DEFAULT 'NA')");
        engine.execute("INSERT INTO t2 (id) VALUES (7)");
        assertEquals("NA", String.valueOf(q("SELECT name FROM t2").getRows().get(0).getValue(0)));
    }

    @Test
    public void mergeInsertKeepsAnExplicitNull() {
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, NULL)");
        engine.execute("""
            MERGE INTO t USING src ON t.id = src.id
            WHEN NOT MATCHED THEN INSERT (id, name) VALUES (src.id, src.name)""");
        assertNull(q("SELECT name FROM t").getRows().get(0).getValue(0));
        assertEquals(0.0, ((Number) q("SELECT score FROM t").getRows().get(0).getValue(0)).doubleValue());
    }

    @Test
    public void omittedColumnsStillTakeDefaultsEverywhere() {
        engine.execute("INSERT INTO t (id) VALUES (1)");
        final ResultSet rs = q("SELECT name, score FROM t");
        assertEquals("NA", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals(0.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue());
    }
}
