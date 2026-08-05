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
 * MAP_CAT and the MAP data type (a MAP is backed by an OBJECT): a {@code ::MAP(keyType, valueType)} cast is a
 * pass-through of the object, and MAP_CAT merges two maps with {@code map2} winning on a shared key.
 */
public class MapCatTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void mapCatMergesTwoMaps() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR), OBJECT_CONSTRUCT('b','2')::MAP(VARCHAR,VARCHAR))")));
    }

    @Test
    public void mapCatSecondMapWinsOnSharedKey() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1','b','x')::MAP(VARCHAR,VARCHAR), OBJECT_CONSTRUCT('b','2','c','3')::MAP(VARCHAR,VARCHAR))")));
    }

    @Test
    public void castToMapIsAnObjectPassThrough() {
        assertEquals("{\"a\":\"1\"}", String.valueOf(scalar(
            "SELECT OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR)::MAP(VARCHAR,VARCHAR)")));
        assertEquals("{\"a\":\"1\"}", String.valueOf(scalar(
            "SELECT CAST(OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR) AS MAP(VARCHAR,VARCHAR))")));
    }

    @Test
    public void bareMapCastIsRejected() {
        // Live-verified: MAP is a STRUCTURED type that must carry its key and value types, so a bare
        // `::MAP` / `CAST(… AS MAP)` is a syntax error — only `MAP(<keyType>, <valueType>)` parses.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT NULL::MAP");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST(OBJECT_CONSTRUCT('a','1') AS MAP)");
            }
        });
    }

    @Test
    public void loaderShapeMapCatOfCastsBackToVariant() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR)::MAP(VARCHAR,VARCHAR), "
            + "OBJECT_CONSTRUCT('b','2')::MAP(VARCHAR,VARCHAR)::MAP(VARCHAR,VARCHAR))::VARIANT")));
    }

    @Test
    public void nullHandling() {
        assertNull(scalar("SELECT MAP_CAT(NULL::MAP(VARCHAR,VARCHAR), NULL::MAP(VARCHAR,VARCHAR))"));
    }

    @Test
    public void aNullOnEitherSideMakesTheWholeResultNull() {
        // Live-verified: MAP_CAT PROPAGATES a NULL rather than treating it as an empty map,
        // which is what Frostlake used to do — it merged the other side through and returned it.
        assertNull(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR), NULL::MAP(VARCHAR,VARCHAR))"));
        assertNull(scalar(
            "SELECT MAP_CAT(NULL::MAP(VARCHAR,VARCHAR), OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR))"));
    }

    @Test
    public void anEmptyMapIsNotANullMap() {
        // The other half of the rule above: an EMPTY map merges normally (live: {"a":1}).
        assertEquals("{\"a\":\"1\"}", String.valueOf(scalar(
            "SELECT MAP_CAT(OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR), "
            + "OBJECT_CONSTRUCT()::MAP(VARCHAR,VARCHAR))")));
    }

    @Test
    public void mapCatOverColumnsPropagatesNullPerRow() {
        engine.execute("CREATE TABLE mc (id INTEGER, a MAP(VARCHAR,VARCHAR), b MAP(VARCHAR,VARCHAR))");
        engine.execute("INSERT INTO mc SELECT 1, OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR), "
            + "OBJECT_CONSTRUCT('b','2')::MAP(VARCHAR,VARCHAR)");
        engine.execute("INSERT INTO mc SELECT 2, OBJECT_CONSTRUCT('a','1')::MAP(VARCHAR,VARCHAR), NULL");
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}",
            String.valueOf(scalar("SELECT MAP_CAT(a, b) FROM mc WHERE id = 1")));
        assertNull(scalar("SELECT MAP_CAT(a, b) FROM mc WHERE id = 2"));
    }

    @Test
    public void mapCatResultIsItselfAMap() {
        // The result's declared type is a MAP, so it feeds the rest of the family — live agrees:
        // MAP_KEYS(MAP_CAT(MAP_CONSTRUCT('a',1), MAP_CONSTRUCT('b',2))) is ["a","b"].
        assertEquals("[\"a\",\"b\"]", String.valueOf(scalar(
            "SELECT MAP_KEYS(MAP_CAT(MAP_CONSTRUCT('a',1), MAP_CONSTRUCT('b',2)))")));
    }

    @Test
    public void untypedNullArgument() {
        // Live-verified: MAP is a STRUCTURED type, so an UNTYPED NULL is not one of its values —
        // "Invalid argument types for function 'MAP_CAT': (NULL, MAP(VARCHAR(…), VARCHAR(…)))". Only a
        // NULL carrying the type (NULL::MAP(VARCHAR,VARCHAR), see nullHandling above) is accepted.
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_CAT(NULL, OBJECT_CONSTRUCT('b','2')::MAP(VARCHAR,VARCHAR))");
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_CAT'"),
            "expected Snowflake's argument-type error, got: " + error.getMessage());
    }

    @Test
    public void mapUsableAsColumnTypeAndName() {
        engine.execute("CREATE TABLE mt (id INTEGER, map MAP(VARCHAR,VARCHAR))");
        engine.execute("INSERT INTO mt SELECT 1, OBJECT_CONSTRUCT('k','v')::MAP(VARCHAR,VARCHAR)");
        assertEquals("{\"k\":\"v\"}", String.valueOf(scalar("SELECT map FROM mt")));
    }
}
