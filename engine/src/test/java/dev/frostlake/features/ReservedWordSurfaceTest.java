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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reserved-word surface, measured word by word over the whole grammar identifier pool: INTO,
 * INSERT, CURRENT, FOLLOWING, RLIKE, UNIQUE and UPDATE are RESERVED — refused as aliases and as
 * column names — while their special positions stay legal: INSERT and RLIKE as function calls, any
 * of them as a variant path key. The ANSI context names (CURRENT_DATE / CURRENT_TIME /
 * CURRENT_TIMESTAMP / CURRENT_USER) split by position: legal as aliases, refused as column
 * DEFINITIONS with the account's own dotted message.
 */
public class ReservedWordSurfaceTest extends BaseDatabaseTest {

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    @Test
    public void reservedWordsRefuseAsAliases() {
        for (final String word : new String[] {"into", "insert", "current", "following",
                "rlike", "unique", "update"}) {
            final String message = refusal("SELECT 1 AS " + word).getMessage();
            assertTrue(message.contains("unexpected '" + word + "'")
                    || message.contains("unexpected '"), word + ": " + message);
        }
    }

    @Test
    public void reservedWordsRefuseAsColumnNames() {
        for (final String word : new String[] {"into", "insert", "current", "following",
                "rlike", "update"}) {
            refusal("CREATE TABLE rw_" + word + " (" + word + " INTEGER)");
        }
    }

    @Test
    public void reservedFunctionNamesStillCall() {
        assertEquals(1, engine.executeQuery("SELECT INSERT('abcdef', 2, 3, 'zzz')").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT RLIKE('san francisco', 'san.*')").getRowCount());
    }

    @Test
    public void reservedWordsStayLegalAsVariantPathKeys() {
        assertEquals("1", String.valueOf(engine.executeQuery(
            "SELECT PARSE_JSON('{\"update\": 1}'):update").getRows().get(0).getValue(0)));
        assertEquals("2", String.valueOf(engine.executeQuery(
            "SELECT PARSE_JSON('{\"current\": 2}'):current").getRows().get(0).getValue(0)));
    }

    @Test
    public void ansiContextNamesSplitByPosition() {
        assertEquals(1, engine.executeQuery("SELECT 1 AS current_date").getRowCount());
        assertEquals("SQL compilation error: error line 1 at position 33\n"
                + ".invalid column definition name 'CURRENT_DATE' (ANSI reserved)",
            refusal("CREATE TABLE rw_ansi (a INTEGER, current_date DATE)").getMessage());
        assertEquals("SQL compilation error: error line 1 at position 22\n"
                + ".invalid column definition name 'CURRENT_USER' (ANSI reserved)",
            refusal("CREATE TABLE rw_ansi (current_user VARCHAR)").getMessage());
    }
}
