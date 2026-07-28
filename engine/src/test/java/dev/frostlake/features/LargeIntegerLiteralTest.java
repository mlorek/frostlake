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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integer literals wider than a Java {@code long} (Snowflake {@code NUMBER(38,0)} allows up to 38 digits).
 * Such literals — e.g. 20–22-digit synthetic row IDs — must parse as exact
 * BigDecimals rather than overflowing with "For input string".
 */
public class LargeIntegerLiteralTest {

    /** 9000000000000000000001 — 22 digits, well beyond Long.MAX_VALUE (9223372036854775807, 19 digits). */
    private static final String BIG = "9000000000000000000001";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testOversizedIntegerLiteralInSelect() {
        final ResultSet rs = engine.executeQuery("SELECT " + BIG + " AS n");
        assertEquals(new BigDecimal(BIG), new BigDecimal(String.valueOf(rs.getRows().get(0).getValue(0))));
    }

    @Test
    public void testOversizedIntegerArithmetic() {
        final ResultSet rs = engine.executeQuery("SELECT " + BIG + " + 1 AS n");
        assertEquals(new BigDecimal("9000000000000000000002"),
            new BigDecimal(String.valueOf(rs.getRows().get(0).getValue(0))));
    }

    @Test
    public void testOversizedIntegerInsertAndCompare() {
        engine.execute("CREATE TABLE t (row_id NUMBER(38,0), c INTEGER)");
        engine.execute("INSERT INTO t VALUES (" + BIG + ", 5)");
        engine.execute("INSERT INTO t VALUES (9000000000000000000002, 6)");

        final ResultSet back = engine.executeQuery("SELECT row_id FROM t WHERE c = 5");
        assertEquals(new BigDecimal(BIG), new BigDecimal(String.valueOf(back.getRows().get(0).getValue(0))));

        final ResultSet cmp = engine.executeQuery("SELECT c FROM t WHERE row_id = 9000000000000000000002");
        assertEquals(6L, ((Number) cmp.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testOrdinaryIntegerLiteralUnaffected() {
        final ResultSet rs = engine.executeQuery("SELECT 42 + 8 AS n");
        assertEquals(50L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
