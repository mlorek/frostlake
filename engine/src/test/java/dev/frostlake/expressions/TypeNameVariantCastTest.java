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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spelled-out type-name aliases in casts: NVARCHAR (incl. empty parens), NCHAR/CHARACTER/CHAR
 * VARYING, TIMESTAMPLTZ / TIMESTAMPTZ (no underscore), and TIMESTAMP WITH LOCAL TIME ZONE — all
 * folding onto their canonical types.
 */
public class TypeNameVariantCastTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void stringAliases() {
        assertEquals("42", scalar("SELECT 42::nvarchar()"));
        assertEquals("42", scalar("SELECT 42::NVARCHAR"));
        assertEquals("x", scalar("SELECT CAST('x' AS CHAR VARYING)"));
        assertEquals("x", scalar("SELECT CAST('x' AS CHARACTER VARYING)"));
        assertEquals("x", scalar("SELECT CAST('x' AS NCHAR VARYING)"));
        assertEquals("x", scalar("SELECT CAST('x' AS CHARACTER)"));
    }

    @Test
    public void decfloatMapsToDouble() {
        // DECFLOAT (decimal floating point) is approximated by DOUBLE.
        assertEquals(1.5, ((Number) scalar("SELECT CAST(1.5 AS DECFLOAT)")).doubleValue(), 0.0);
        engine.execute("CREATE TABLE dfl (x DECFLOAT)");
        engine.execute("INSERT INTO dfl VALUES (2.5)");
        assertEquals(2.5, ((Number) scalar("SELECT x FROM dfl")).doubleValue(), 0.0);
    }

    @Test
    public void timestampAliases() {
        assertTrue(String.valueOf(scalar("SELECT '2024-04-08 10:00:00'::TIMESTAMPLTZ")).startsWith("2024-04-08"));
        assertTrue(String.valueOf(scalar("SELECT '2024-04-08 10:00:00'::timestamptz")).startsWith("2024-04-08"));
        assertTrue(String.valueOf(scalar("SELECT '2024-04-08 10:00:00'::TIMESTAMP WITH LOCAL TIME ZONE"))
            .startsWith("2024-04-08"));
    }
}
