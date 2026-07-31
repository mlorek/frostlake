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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEO predicates and ST_CENTROID against the live-verified truth table. */
public class GeoPredicatesTest extends BaseDatabaseTest {

    private static final String SQUARE = "TO_GEOGRAPHY('POLYGON((0 0, 2 0, 2 2, 0 2, 0 0))')";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void containsAndWithin() {
        assertEquals(Boolean.TRUE, scalar("SELECT ST_CONTAINS(" + SQUARE + ", TO_GEOGRAPHY('POINT(1 1)'))"));
        assertEquals(Boolean.FALSE, scalar("SELECT ST_CONTAINS(" + SQUARE + ", TO_GEOGRAPHY('POINT(5 5)'))"));
        assertEquals(Boolean.TRUE, scalar("SELECT ST_WITHIN(TO_GEOGRAPHY('POINT(1 1)'), " + SQUARE + ")"));
        assertEquals(Boolean.FALSE, scalar("SELECT ST_WITHIN(TO_GEOGRAPHY('POINT(5 5)'), " + SQUARE + ")"));
    }

    @Test
    public void polygonWithAHoleExcludesIt() {
        final String donut = "TO_GEOGRAPHY('POLYGON((0 0, 10 0, 10 10, 0 10, 0 0), (4 4, 6 4, 6 6, 4 6, 4 4))')";
        assertEquals(Boolean.TRUE, scalar("SELECT ST_CONTAINS(" + donut + ", TO_GEOGRAPHY('POINT(1 1)'))"));
        assertEquals(Boolean.FALSE, scalar("SELECT ST_CONTAINS(" + donut + ", TO_GEOGRAPHY('POINT(5 5)'))"),
            "a point inside the hole is not contained");
    }

    @Test
    public void intersectsAndDisjoint() {
        assertEquals(Boolean.TRUE, scalar(
            "SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0, 2 2)'), TO_GEOGRAPHY('LINESTRING(0 2, 2 0)'))"));
        assertEquals(Boolean.FALSE, scalar(
            "SELECT ST_INTERSECTS(TO_GEOGRAPHY('LINESTRING(0 0, 1 1)'), TO_GEOGRAPHY('LINESTRING(5 5, 6 6)'))"));
        assertEquals(Boolean.TRUE, scalar(
            "SELECT ST_DISJOINT(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('POINT(1 0)'))"));
        assertEquals(Boolean.TRUE, scalar(
            "SELECT ST_INTERSECTS(" + SQUARE + ", TO_GEOGRAPHY('POINT(1 1)'))"),
            "a contained point intersects its container");
    }

    @Test
    public void dwithinUsesTheSphericalMeters() {
        // One degree at the equator is 111195.1 m (live-verified).
        assertEquals(Boolean.TRUE, scalar(
            "SELECT ST_DWITHIN(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('POINT(1 0)'), 120000)"));
        assertEquals(Boolean.FALSE, scalar(
            "SELECT ST_DWITHIN(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('POINT(1 0)'), 100000)"));
    }

    @Test
    public void centroidOfSimpleShapes() {
        // Live: the square's centroid is (~1.0, ~1.00005) — the planar approximation here lands
        // within 1e-3 of it; the linestring midpoint is exact.
        final String squareCentroid = String.valueOf(scalar("SELECT ST_ASTEXT(ST_CENTROID(" + SQUARE + "))"));
        assertTrue(squareCentroid.startsWith("POINT(1 1") || squareCentroid.startsWith("POINT(0.999"),
            squareCentroid);
        assertEquals("POINT(1 0)", String.valueOf(scalar(
            "SELECT ST_ASTEXT(ST_CENTROID(TO_GEOGRAPHY('LINESTRING(0 0, 2 0)')))")));
    }
}
