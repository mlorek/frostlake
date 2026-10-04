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

package dev.frostlake.types;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UUID is a type of its own, though the engine models it as a text. Overload resolution ranked a UUID argument
 * as a VARCHAR, a text folded beside a UUID was handed back unconverted under the UUID type, and refusals spelled
 * the type UUID(36) or VARCHAR(36).
 */
public class UuidIsItsOwnTypeTest {

    private static final String U = "'11111111-2222-3333-4444-555555555555'::UUID";

    private static final String INVALID = "is invalid, expected format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx "
        + "(8-4-4-4-12 hexadecimal digits with hyphens)";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE uu_db");
        engine.execute("USE SCHEMA uu_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void theUuidOverloadWinsForAUuidInEitherDeclarationOrder() {
        engine.execute("CREATE FUNCTION u4(x UUID) RETURNS VARCHAR AS $$ 'uuid' $$");
        engine.execute("CREATE FUNCTION u4(x VARCHAR) RETURNS VARCHAR AS $$ 'varchar' $$");
        engine.execute("CREATE FUNCTION u5(x VARCHAR) RETURNS VARCHAR AS $$ 'varchar' $$");
        engine.execute("CREATE FUNCTION u5(x UUID) RETURNS VARCHAR AS $$ 'uuid' $$");
        assertEquals("uuid", first("SELECT u4(" + U + ")"));
        assertEquals("uuid", first("SELECT u5(" + U + ")"));
        assertEquals("varchar", first("SELECT u4('abc')"), "a text still reaches the VARCHAR overload");
    }

    @Test
    public void aTextFoldedIntoAUuidIsConverted() {
        // Live-verified on qa-develop: the VALUES row and the COALESCE branch are refused in the UUID's sentence.
        assertEquals("UUID 'abc' " + INVALID,
            refusal("SELECT column1, SYSTEM$TYPEOF(column1) FROM VALUES (" + U + "), ('abc')"));
        assertEquals("UUID 'defgh' " + INVALID, refusal("SELECT COALESCE('defgh', " + U + ")"));
        assertEquals("UUID 'defgh' " + INVALID, refusal("SELECT IFF(TRUE, 'defgh', " + U + ")"));
        assertEquals("UUID 'defgh' " + INVALID,
            refusal("SELECT CASE WHEN TRUE THEN 'defgh' ELSE " + U + " END"));
        final ResultSet values = engine.executeQuery(
            "SELECT column1, SYSTEM$TYPEOF(column1) FROM VALUES (" + U + "), ('AAAAAAAA-2222-3333-4444-555555555555')");
        assertEquals("aaaaaaaa-2222-3333-4444-555555555555", values.getRows().get(1).getValue(0),
            "a UUID is held canonical and lower-case");
        assertEquals("UUID[SB16]", values.getRows().get(1).getValue(1));
        assertEquals("aaaaaaaa-2222-3333-4444-555555555555",
            first("SELECT NVL('AAAAAAAA-2222-3333-4444-555555555555', " + U + ")"));
    }

    @Test
    public void aCtasOverAUuidFoldHoldsUuids() {
        assertTrue(refusal("CREATE TABLE bad AS SELECT COALESCE('defgh', " + U + ") AS c").contains("UUID 'defgh'"));
        engine.execute("CREATE TABLE good AS SELECT COALESCE(NULL, 'AAAAAAAA-2222-3333-4444-555555555555', "
            + U + ") AS c");
        assertEquals("aaaaaaaa-2222-3333-4444-555555555555", first("SELECT c FROM good"));
        assertEquals("UUID[SB16]", first("SELECT SYSTEM$TYPEOF(c) FROM good"));
    }

    @Test
    public void refusalsSpellTheTypeBareUuid() {
        engine.execute("CREATE TABLE t (u UUID)");
        assertTrue(refusal("INSERT INTO t (u) SELECT [1]")
            .contains("expecting UUID but got ARRAY for column U"));
        assertTrue(refusal("ALTER TABLE t ALTER COLUMN u SET DATA TYPE VARCHAR(36)")
            .contains("cannot change column U from type UUID to VARCHAR(36)"));
        assertEquals("Declared return type 'UUID' is incompatible with actual return type 'NUMBER(1,0)'",
            refusal("CREATE FUNCTION sf() RETURNS UUID AS $$ 1 $$"));
        assertEquals("Declared return type 'UUID' for column 'C' is incompatible with actual return type"
            + " 'NUMBER(1,0)'", refusal("CREATE FUNCTION tf() RETURNS TABLE (c UUID) AS $$ SELECT 1 $$"));
    }

    @Test
    public void aSetOperationOverAUuidAndATextIsAUuid() {
        // Live-verified on qa-develop: both rows are UUIDs whichever arm leads, and a text arm is converted.
        final ResultSet union = engine.executeQuery("SELECT x, SYSTEM$TYPEOF(x) FROM (SELECT " + U
            + " x UNION ALL SELECT 'AAAAAAAA-2222-3333-4444-555555555555')");
        assertEquals("aaaaaaaa-2222-3333-4444-555555555555", union.getRows().get(1).getValue(0));
        assertEquals("UUID[SB16]", union.getRows().get(1).getValue(1));
        assertEquals("UUID[SB16]", first("SELECT SYSTEM$TYPEOF(x) FROM (SELECT " + U
            + " x UNION ALL SELECT 'AAAAAAAA-2222-3333-4444-555555555555'::VARCHAR(36))"));
        assertEquals("UUID 'abc' " + INVALID, refusal("SELECT x FROM (SELECT " + U + " x UNION ALL SELECT 'abc')"));
        assertEquals("UUID 'abc' " + INVALID, refusal("SELECT x FROM (SELECT 'abc' x UNION ALL SELECT " + U + ")"));
    }

    @Test
    public void aStructuredUuidFieldHoldsAUuid() {
        assertEquals("{\"u\":\"aaaaaaaa-2222-3333-4444-555555555555\"}",
            first("SELECT {'u': 'AAAAAAAA-2222-3333-4444-555555555555'}::OBJECT(u UUID)"));
        assertEquals("Typed object schema mismatch in conversion", refusal("SELECT {'u': 'abc'}::OBJECT(u UUID)"));
    }
}
