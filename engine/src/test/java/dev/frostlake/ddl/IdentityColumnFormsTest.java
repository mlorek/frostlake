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
 * AUTOINCREMENT / IDENTITY accept both the {@code (start, step)} and {@code START n INCREMENT n} seed forms
 * plus an optional {@code ORDER}/{@code NOORDER} (parsed, no effect on values) — as in Snowflake DDL.
 */
public class IdentityColumnFormsTest extends BaseDatabaseTest {

    private long id(final String table, final int row) {
        final ResultSet rs = engine.executeQuery("SELECT id FROM " + table + " ORDER BY id");
        return ((Number) rs.getRows().get(row).getValue(0)).longValue();
    }

    @Test
    public void autoincrementWordFormsInAnyOrderAndAlone() {
        engine.execute("CREATE TABLE w1 (id INT AUTOINCREMENT START 10, n VARCHAR)");
        engine.execute("INSERT INTO w1 (n) VALUES ('a'), ('b')");
        assertEquals(10L, id("w1", 0));
        assertEquals(11L, id("w1", 1));

        engine.execute("CREATE TABLE w2 (id INT AUTOINCREMENT INCREMENT 2, n VARCHAR)");
        engine.execute("INSERT INTO w2 (n) VALUES ('a'), ('b')");
        assertEquals(1L, id("w2", 0));
        assertEquals(3L, id("w2", 1));

        engine.execute("CREATE TABLE w3 (id INT AUTOINCREMENT INCREMENT 2 START 10 NOORDER, n VARCHAR)");
        engine.execute("INSERT INTO w3 (n) VALUES ('a'), ('b')");
        assertEquals(10L, id("w3", 0));
        assertEquals(12L, id("w3", 1));

        engine.execute("CREATE TABLE w4 (id INT AUTOINCREMENT START 10 ORDER, n VARCHAR)");
        engine.execute("INSERT INTO w4 (n) VALUES ('a')");
        assertEquals(10L, id("w4", 0));
    }

    @Test
    public void autoincrementNegativeIncrementCountsDown() {
        engine.execute("CREATE TABLE wneg (id BIGINT AUTOINCREMENT INCREMENT -1, n VARCHAR)");
        engine.execute("INSERT INTO wneg (n) VALUES ('a'), ('b')");
        final ResultSet rs = engine.executeQuery("SELECT id FROM wneg ORDER BY id DESC");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(0L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void autoincrementWithParenSeedAndStep() {
        engine.execute("CREATE TABLE i1 (id NUMBER AUTOINCREMENT(100,5), n VARCHAR)");
        engine.execute("INSERT INTO i1 (n) VALUES ('a')");
        engine.execute("INSERT INTO i1 (n) VALUES ('b')");
        assertEquals(100L, id("i1", 0));
        assertEquals(105L, id("i1", 1));
    }

    @Test
    public void identityStartIncrementForm() {
        engine.execute("CREATE TABLE i2 (id NUMBER IDENTITY START 50 INCREMENT 10, n VARCHAR)");
        engine.execute("INSERT INTO i2 (n) VALUES ('a')");
        engine.execute("INSERT INTO i2 (n) VALUES ('b')");
        assertEquals(50L, id("i2", 0));
        assertEquals(60L, id("i2", 1));
    }

    @Test
    public void autoincrementWithOrderKeyword() {
        engine.execute("CREATE TABLE i3 (id NUMBER AUTOINCREMENT(1,1) ORDER, n VARCHAR)");
        engine.execute("INSERT INTO i3 (n) VALUES ('a')");
        assertEquals(1L, id("i3", 0));
    }

    @Test
    public void plainAutoincrementStillDefaultsToOneOne() {
        engine.execute("CREATE TABLE i4 (id NUMBER AUTOINCREMENT, n VARCHAR)");
        engine.execute("INSERT INTO i4 (n) VALUES ('a')");
        assertEquals(1L, id("i4", 0));
    }
}
