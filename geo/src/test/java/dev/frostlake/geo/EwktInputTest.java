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
 * TO_GEOMETRY and TO_GEOGRAPHY read EWKT (live-verified): {@code SRID=n;} ahead of the WKT, in any case and with a
 * blank after its semicolon, sets a GEOMETRY's SRID, 0 included, an SRID argument overriding it; a GEOGRAPHY reads
 * SRID 4326 alone and refuses another as input it does not recognize; TRY_TO_GEOMETRY reads it too.
 */
public class EwktInputTest extends MeasuredCellsTestSupport {

    @Test
    public void ewktSetsTheSrid() {
        assertCells(new String[][] {
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('SRID=4326;POINT(1 2)')), ST_SRID(TO_GEOGRAPHY('SRID=4326;POINT(1 2)'))",
                "POINT(1 2)|4326"},
            {"SELECT ST_SRID(TO_GEOMETRY('SRID=3857;POINT(1 2)'))", "3857"},
            {"SELECT ST_ASWKT(TO_GEOGRAPHY('SRID=3857;POINT(1 2)'))",
                "REFUSED Error parsing Geo input: SRID=3857;POINT(1 2). Did not recognize valid GeoJSON, (E)WKT or "
                + "(E)WKB."},
            {"SELECT ST_SRID(TO_GEOMETRY('srid=4326;POINT(1 2)'))", "4326"},
            {"SELECT ST_SRID(TO_GEOMETRY('SRID=4326;POINT(1 2)', 3857))", "3857"},
            {"SELECT ST_SRID(TO_GEOMETRY('SRID=0;POINT(1 2)'))", "0"},
            {"SELECT ST_SRID(TO_GEOMETRY('SRID=4326; POINT(1 2)'))", "4326"},
            {"SELECT TRY_TO_GEOMETRY('SRID=4326;POINT(1 2)') IS NULL", "false"},
        });
    }
}
