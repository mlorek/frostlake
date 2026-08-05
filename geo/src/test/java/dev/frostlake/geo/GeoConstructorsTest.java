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

package dev.frostlake.geo;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEOGRAPHY / GEOMETRY constructors and renderers, live-verified: WKT and GeoJSON parse into the
 * GeoJSON model (sorted keys, compact display here), ST_ASTEXT normalizes WKT (comma without
 * space), ST_ASWKB renders little-endian WKB, and geo values are barred from VALUES clauses.
 */
public class GeoConstructorsTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        final Object v = scalar(sql);
        return v == null ? null : v.toString();
    }

    @Test
    public void wktAndGeojsonParseToTheSameModel() {
        assertEquals("{\"coordinates\":[-122.35,37.55],\"type\":\"Point\"}",
            text("SELECT TO_GEOGRAPHY('POINT(-122.35 37.55)')"));
        assertEquals("{\"coordinates\":[-122.35,37.55],\"type\":\"Point\"}",
            text("SELECT TO_GEOGRAPHY('{\"type\":\"Point\",\"coordinates\":[-122.35,37.55]}')"));
        assertEquals("{\"coordinates\":[[0,0],[1,0]],\"type\":\"LineString\"}",
            text("SELECT TO_GEOGRAPHY('LINESTRING(0 0, 1 0)')"));
        assertEquals("{\"coordinates\":[[[0,0],[2,0],[2,2],[0,2],[0,0]]],\"type\":\"Polygon\"}",
            text("SELECT TO_GEOGRAPHY('POLYGON((0 0, 2 0, 2 2, 0 2, 0 0))')"));
        assertEquals("{\"coordinates\":[[0,0],[1,1]],\"type\":\"MultiPoint\"}",
            text("SELECT TO_GEOGRAPHY('MULTIPOINT((0 0), (1 1))')"));
    }

    @Test
    public void typeofRejectsGeoValuesLikeSnowflake() {
        // Live-verified: GEOGRAPHY / GEOMETRY are not variant-coercible — TYPEOF rejects them at
        // compile time with the argument-type error (TO_VARIANT does too).
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TYPEOF(TO_GEOGRAPHY('POINT(1 2)'))");
            }
        });
        assertTrue(rejected.getMessage().contains(
            "Invalid argument types for function 'TYPEOF': (GEOGRAPHY)"), rejected.getMessage());
    }

    @Test
    public void invalidInputErrorsWithSnowflakesMessageAndTryReturnsNull() {
        assertNull(scalar("SELECT TRY_TO_GEOGRAPHY('garbage')"));
        final RuntimeException bad = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_GEOGRAPHY('garbage')");
            }
        });
        assertTrue(bad.getMessage().contains(
            "Error parsing Geo input: garbage. Did not recognize valid GeoJSON, (E)WKT or (E)WKB."),
            bad.getMessage());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_GEOGRAPHY('POLYGON((0 0, 1 0))')");
            }
        });
    }

    @Test
    public void makePointConstructorsCarryTheirKind() {
        // The kind shows through the SRID (4326 for GEOGRAPHY, 0 for GEOMETRY) and through the
        // mixing rejection — TYPEOF itself rejects geo values (live-verified).
        assertEquals(4326L, ((Number) scalar("SELECT ST_SRID(ST_MAKEPOINT(-122.35, 37.55))")).longValue());
        assertEquals(4326L, ((Number) scalar("SELECT ST_SRID(ST_POINT(-122.35, 37.55))")).longValue());
        assertEquals(0L, ((Number) scalar("SELECT ST_SRID(ST_MAKEGEOMPOINT(3, 4))")).longValue());
    }

    @Test
    public void renderersRoundTrip() {
        assertEquals("POINT(-122.35 37.55)", text("SELECT ST_ASTEXT(TO_GEOGRAPHY('POINT(-122.35 37.55)'))"));
        assertEquals("LINESTRING(0 0,1 0)", text("SELECT ST_ASWKT(TO_GEOGRAPHY('LINESTRING(0 0, 1 0)'))"),
            "normalized WKT joins points with a bare comma (live-verified)");
        assertEquals("{\"coordinates\":[-122.35,37.55],\"type\":\"Point\"}",
            text("SELECT ST_ASGEOJSON(TO_GEOGRAPHY('POINT(-122.35 37.55)'))"));
        assertEquals("0101000000000000000000F03F0000000000000040",
            text("SELECT ST_ASWKB(TO_GEOGRAPHY('POINT(1 2)'))"),
            "little-endian WKB (BINARY displays as uppercase hex), live-verified byte-for-byte");
    }

    @Test
    public void geographyColumnsStoreAndReadBack() {
        engine.execute("CREATE TABLE geo_t (id INTEGER, g GEOGRAPHY)");
        engine.execute("INSERT INTO geo_t SELECT 1, TO_GEOGRAPHY('POINT(1 2)')");
        assertEquals("{\"coordinates\":[1,2],\"type\":\"Point\"}", text("SELECT g FROM geo_t"));
        assertEquals(1.0, ((Number) scalar("SELECT ST_X(g) FROM geo_t")).doubleValue(), 0.0001);
    }

    @Test
    public void geoConstructorsAreBarredFromValuesClauses() {
        engine.execute("CREATE TABLE geo_v (g GEOGRAPHY)");
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO geo_v VALUES (TO_GEOGRAPHY('POINT(3 4)'))");
            }
        });
        assertTrue(rejected.getMessage().contains("in VALUES clause"), rejected.getMessage());
    }
}
