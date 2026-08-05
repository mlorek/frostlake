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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine WITHOUT the optional frostlake-geo module: GEOGRAPHY / GEOMETRY columns still parse
 * (the type surface is engine-side), but the ST_ functions are unknown and coercing a string into
 * a geo column points at the missing module — mirroring the UDF-runtime absence contract. The geo
 * functions themselves are tested in the frostlake-geo module.
 */
public class GeoModuleAbsenceTest extends BaseDatabaseTest {

    private static final String GEO_ALWAYS_PRESENT =
        "asserts the behaviour of an engine WITHOUT the optional frostlake-geo module; a real account "
        + "always carries the geospatial surface, so it coerces a WKT string into a GEOGRAPHY column and "
        + "resolves TO_GEOGRAPHY instead of pointing at a missing module";

    @Test
    public void geoColumnsParseButStringCoercionNamesTheModule() {
        Assumptions.assumeFalse(isLiveSnowflake(), GEO_ALWAYS_PRESENT);
        engine.execute("CREATE TABLE geo_abs (g GEOGRAPHY, m GEOMETRY)");
        final RuntimeException missing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO geo_abs (g) SELECT 'POINT(1 2)'");
            }
        });
        assertTrue(missing.getMessage().contains("frostlake-geo"), missing.getMessage());
    }

    @Test
    public void geoFunctionsAreUnknownWithoutTheModule() {
        Assumptions.assumeFalse(isLiveSnowflake(), GEO_ALWAYS_PRESENT);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_GEOGRAPHY('POINT(1 2)')");
            }
        });
    }
}
