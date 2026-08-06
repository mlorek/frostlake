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
 * Snowflake spells a temporary table five ways, and reports all of them as one kind. Measured by
 * creating each on a real account and reading {@code SHOW TABLES} back:
 *
 * <pre>
 *   CREATE TEMPORARY TABLE          kind = TEMPORARY
 *   CREATE TEMP TABLE               kind = TEMPORARY
 *   CREATE LOCAL TEMPORARY TABLE    kind = TEMPORARY
 *   CREATE GLOBAL TEMPORARY TABLE   accepted
 *   CREATE VOLATILE TABLE           kind = TEMPORARY
 *   CREATE TRANSIENT TABLE          kind = TRANSIENT   (a different thing)
 * </pre>
 *
 * <p>Frostlake used to parse only the first two, so the other three were syntax errors — plain
 * under-acceptance. It went unnoticed inside stored procedures because the owner's rights guard was a
 * text check that ran BEFORE the parser and refused them for an unrelated reason.
 *
 * <p>LOCAL and GLOBAL are accepted and carry no meaning: Snowflake has no cross-session temporary
 * table for GLOBAL to denote.
 */
public class TemporaryTableSpellingsTest extends BaseDatabaseTest {

    /** The kind SHOW TABLES reports for {@code name}. */
    private String kindOf(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES");
        final int nameIndex = rs.getColumnIndex("name");
        final int kindIndex = rs.getColumnIndex("kind");
        for (int i = 0; i < rs.getRows().size(); i++) {
            if (name.equalsIgnoreCase(String.valueOf(rs.getRows().get(i).getValue(nameIndex)))) {
                return String.valueOf(rs.getRows().get(i).getValue(kindIndex));
            }
        }
        return "<not found>";
    }

    @Test
    public void everySpellingOfATemporaryTableIsTemporary() {
        engine.execute("CREATE TEMPORARY TABLE sp_temporary (a INTEGER)");
        engine.execute("CREATE TEMP TABLE sp_temp (a INTEGER)");
        engine.execute("CREATE LOCAL TEMPORARY TABLE sp_local (a INTEGER)");
        engine.execute("CREATE GLOBAL TEMPORARY TABLE sp_global (a INTEGER)");
        engine.execute("CREATE VOLATILE TABLE sp_volatile (a INTEGER)");

        assertEquals("TEMPORARY", kindOf("SP_TEMPORARY"));
        assertEquals("TEMPORARY", kindOf("SP_TEMP"));
        assertEquals("TEMPORARY", kindOf("SP_LOCAL"));
        assertEquals("TEMPORARY", kindOf("SP_GLOBAL"));
        assertEquals("TEMPORARY", kindOf("SP_VOLATILE"));
    }

    /** TRANSIENT is a separate durability class, not a spelling of temporary. */
    @Test
    public void transientIsNotTemporary() {
        engine.execute("CREATE TRANSIENT TABLE sp_transient (a INTEGER)");
        assertEquals("TRANSIENT", kindOf("SP_TRANSIENT"));
    }

    @Test
    public void aPlainTableIsNeither() {
        engine.execute("CREATE TABLE sp_plain (a INTEGER)");
        assertEquals("TABLE", kindOf("SP_PLAIN"));
    }

    /** The new spellings behave like tables, not just like parse trees. */
    @Test
    public void aVolatileTableHoldsRows() {
        engine.execute("CREATE VOLATILE TABLE sp_rows (a INTEGER)");
        engine.execute("INSERT INTO sp_rows VALUES (1), (2)");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM sp_rows");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
