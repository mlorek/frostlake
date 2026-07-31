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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAP_CONTAINS_KEY(key, map) — the one member of the family whose MAP is the SECOND argument. Every
 * expectation here was measured live.
 */
public class MapContainsKeyTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void findsAPresentKey() {
        assertEquals("true", String.valueOf(scalar(
            "SELECT MAP_CONTAINS_KEY('a', OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT))")));
    }

    @Test
    public void aMissingKeyIsFalseNotNull() {
        assertEquals("false", String.valueOf(scalar(
            "SELECT MAP_CONTAINS_KEY('z', OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT))")));
        assertEquals("false", String.valueOf(scalar(
            "SELECT MAP_CONTAINS_KEY('a', OBJECT_CONSTRUCT()::MAP(VARCHAR,INT))")));
    }

    @Test
    public void aNullOnEitherSideIsNullNotFalse() {
        assertNull(scalar("SELECT MAP_CONTAINS_KEY(NULL, OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT))"));
        assertNull(scalar("SELECT MAP_CONTAINS_KEY('a', NULL::MAP(VARCHAR,INT))"));
    }

    @Test
    public void readsAMapColumn() {
        engine.execute("CREATE TABLE mck (m MAP(VARCHAR,INT))");
        engine.execute("INSERT INTO mck SELECT OBJECT_CONSTRUCT('z',1,'a',2)::MAP(VARCHAR,INT)");
        assertEquals("true", String.valueOf(scalar("SELECT MAP_CONTAINS_KEY('z', m) FROM mck")));
        assertEquals("false", String.valueOf(scalar("SELECT MAP_CONTAINS_KEY('q', m) FROM mck")));
    }

    @Test
    public void aPlainObjectIsRefusedInTheMapPosition() {
        // Live: "Invalid argument types for function 'MAP_CONTAINS_KEY': (VARCHAR(1), OBJECT)" — the
        // list names BOTH arguments, so the offending position is visible in context.
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_CONTAINS_KEY('a', OBJECT_CONSTRUCT('a',1))");
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_CONTAINS_KEY'"),
            "expected Snowflake's argument-type error, got: " + error.getMessage());
    }
}
