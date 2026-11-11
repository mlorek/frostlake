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
 * ST_ASEWKT writes a value's WKT behind its SRID (live-verified): {@code SRID=4326;POINT(1 2)}, a line string's
 * points joined without a blank as ST_ASWKT joins them.
 */
public class StAsEwktTest extends MeasuredCellsTestSupport {

    @Test
    public void theWktBehindTheSrid() {
        assertCells(new String[][] {
            {"SELECT ST_ASEWKT(TO_GEOMETRY('SRID=4326;POINT(1 2)')), ST_SRID(TO_GEOMETRY('SRID=4326;POINT(1 2)'))",
                "SRID=4326;POINT(1 2)|4326"},
            {"SELECT ST_ASEWKT(TO_GEOMETRY('SRID=4326;LINESTRING(1 2, 3 4)'))", "SRID=4326;LINESTRING(1 2,3 4)"},
        });
    }
}
