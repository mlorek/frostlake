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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A WKT text holding an EMPTY shape is no value: TO_GEOMETRY, TO_GEOGRAPHY and TRY_TO_GEOMETRY read POINT,
 * LINESTRING, POLYGON, MULTIPOINT and GEOMETRYCOLLECTION EMPTY, an EWKT one included and a collection holding one
 * among its members, as NULL, so every function over it answers NULL (all live-verified). It was refused as input
 * the reader did not recognize.
 */
public class EmptyGeoInputTest extends MeasuredCellsTestSupport {

    @Test
    public void anEmptyShapeIsNoValue() {
        for (final String kind : new String[] {"POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "GEOMETRYCOLLECTION"}) {
            final String g = "TO_GEOMETRY('" + kind + " EMPTY')";
            assertAnswer("true|null|null|null",
                "SELECT " + g + " IS NULL, ST_ASWKT(" + g + "), ST_ASGEOJSON(" + g + "), ST_SRID(" + g + ")");
        }
        assertCells(new String[][] {
            {"SELECT ST_ASEWKT(TO_GEOMETRY('SRID=4326;POINT EMPTY'))", "null"},
            {"SELECT ST_ASWKB(TO_GEOMETRY('POINT EMPTY'))", "null"},
            {"SELECT ST_NPOINTS(TO_GEOMETRY('LINESTRING EMPTY'))", "null"},
            {"SELECT TO_GEOGRAPHY('POINT EMPTY') IS NULL, ST_ASWKT(TO_GEOGRAPHY('POINT EMPTY'))", "true|null"},
            {"SELECT TO_GEOGRAPHY('GEOMETRYCOLLECTION EMPTY') IS NULL", "true"},
            {"SELECT TRY_TO_GEOMETRY('POINT EMPTY') IS NULL", "true"},
            {"SELECT ST_ASWKT(TO_GEOMETRY('POINT(1 2)')), "
                + "ST_ASWKT(TO_GEOMETRY('GEOMETRYCOLLECTION(POINT(1 2), POINT EMPTY)'))", "POINT(1 2)|null"},
        });
    }
}
