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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEOGRAPHY / GEOMETRY measures against live-verified Snowflake values: the GEOGRAPHY sphere is
 * R = 6371008.7714 m (one degree = 111195.10117748393 m), HAVERSINE uses R = 6371 km, GEOMETRY is
 * planar, and mixing the two kinds raises the argument-type error.
 */
public class GeoMeasuresTest extends BaseDatabaseTest {

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void sphericalDistanceMatchesSnowflakesRadius() {
        assertEquals(111195.10117748393,
            num("SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('POINT(1 0)'))"), 0.01);
        assertEquals(111195.10117748393,
            num("SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOGRAPHY('POINT(0 1)'))"), 0.01);
        // Bay Area to Los Angeles — live value 537333.0745930525 m.
        assertEquals(537333.07,
            num("SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(-122.35 37.55)'), TO_GEOGRAPHY('POINT(-118.24 34.05)'))"), 5.0);
    }

    @Test
    public void planarGeometryDistanceIsCartesian() {
        assertEquals(5.0, num("SELECT ST_DISTANCE(TO_GEOMETRY('POINT(0 0)'), TO_GEOMETRY('POINT(3 4)'))"), 0.0001);
        assertEquals(5.0, num("SELECT ST_LENGTH(TO_GEOMETRY('LINESTRING(0 0, 3 4)'))"), 0.0001);
    }

    @Test
    public void haversineUsesItsOwnRadius() {
        // Live value 537.3322311897703 km — slightly SMALLER than ST_DISTANCE/1000.
        assertEquals(537.33223, num("SELECT HAVERSINE(37.55, -122.35, 34.05, -118.24)"), 0.005);
    }

    @Test
    public void sphericalLengthAreaAndPerimeter() {
        assertEquals(111195.10, num("SELECT ST_LENGTH(TO_GEOGRAPHY('LINESTRING(0 0, 1 0)'))"), 0.01);
        // Live 1.2364036567076408E10 m² for the 1°×1° square at the equator.
        assertEquals(1.2364036567076408E10,
            num("SELECT ST_AREA(TO_GEOGRAPHY('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'))"), 2.0E7);
        // Live 444763.46872762055 m.
        assertEquals(444763.47,
            num("SELECT ST_PERIMETER(TO_GEOGRAPHY('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'))"), 1.0);
        assertEquals(4.0, num("SELECT ST_AREA(TO_GEOMETRY('POLYGON((0 0, 2 0, 2 2, 0 2, 0 0))'))"), 0.0001);
        assertEquals(8.0, num("SELECT ST_PERIMETER(TO_GEOMETRY('POLYGON((0 0, 2 0, 2 2, 0 2, 0 0))'))"), 0.0001);
    }

    @Test
    public void extentsAndCounts() {
        assertEquals(-1.0, num("SELECT ST_XMIN(TO_GEOGRAPHY('LINESTRING(-1 -2, 3 4)'))"), 0.0001);
        assertEquals(3.0, num("SELECT ST_XMAX(TO_GEOGRAPHY('LINESTRING(-1 -2, 3 4)'))"), 0.0001);
        assertEquals(-2.0, num("SELECT ST_YMIN(TO_GEOGRAPHY('LINESTRING(-1 -2, 3 4)'))"), 0.0001);
        assertEquals(4.0, num("SELECT ST_YMAX(TO_GEOGRAPHY('LINESTRING(-1 -2, 3 4)'))"), 0.0001);
        assertEquals(5, (int) num("SELECT ST_NPOINTS(TO_GEOGRAPHY('POLYGON((0 0, 2 0, 2 2, 0 2, 0 0))'))"));
        assertEquals(0, (int) num("SELECT ST_DIMENSION(TO_GEOGRAPHY('POINT(1 2)'))"));
        assertEquals(1, (int) num("SELECT ST_DIMENSION(TO_GEOGRAPHY('LINESTRING(0 0, 1 1)'))"));
        assertEquals(2, (int) num("SELECT ST_DIMENSION(TO_GEOGRAPHY('POLYGON((0 0, 1 0, 1 1, 0 0))'))"));
        assertEquals(4326, (int) num("SELECT ST_SRID(TO_GEOGRAPHY('POINT(1 2)'))"));
        assertEquals(0, (int) num("SELECT ST_SRID(TO_GEOMETRY('POINT(3 4)'))"));
    }

    @Test
    public void pointAccessorsAndTheirTypeError() {
        assertEquals(-122.35, num("SELECT ST_X(TO_GEOGRAPHY('POINT(-122.35 37.55)'))"), 0.0001);
        assertEquals(37.55, num("SELECT ST_Y(TO_GEOGRAPHY('POINT(-122.35 37.55)'))"), 0.0001);
        final RuntimeException notPoint = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT ST_X(TO_GEOGRAPHY('LINESTRING(0 0, 1 1)'))");
            }
        });
        assertTrue(notPoint.getMessage().contains("Type LineString is not supported as argument to ST_X."),
            notPoint.getMessage());
    }

    @Test
    public void mixingGeographyAndGeometryIsRejected() {
        final RuntimeException mixed = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT ST_DISTANCE(TO_GEOGRAPHY('POINT(0 0)'), TO_GEOMETRY('POINT(1 1)'))");
            }
        });
        assertTrue(mixed.getMessage().contains(
            "Invalid argument types for function 'ST_DISTANCE': (GEOGRAPHY, GEOMETRY)"), mixed.getMessage());
    }
}
