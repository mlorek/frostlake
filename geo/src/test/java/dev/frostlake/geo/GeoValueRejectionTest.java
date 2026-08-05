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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GEOGRAPHY / GEOMETRY rejections over POPULATED rows, and — the point of running them here — the
 * {@code ST_} surface they must not touch.
 *
 * <p>The rules themselves live in the ENGINE, because a geo COLUMN can be declared without this
 * module; {@code dev.frostlake.features.GeoTypeRejectionTest} asserts them there, on declared columns
 * over an empty table. This class re-asserts the headline ones with real geo VALUES in the rows, and
 * then pins everything live ACCEPTS: the {@code ST_} accessors are the documented way to a geo value's
 * text, {@code TO_GEOGRAPHY} / {@code TO_GEOMETRY} take a geo value where the CAST spellings do not,
 * and {@code GROUP BY ST_ASWKT(g)} is how a caller groups geospatial data at all. An over-broad rule
 * would take the whole module out, and these tests are what says so.
 *
 * <p>Measured against a live Snowflake account.
 */
public class GeoValueRejectionTest extends BaseDatabaseTest {

    private static final String POINT_ONE = "TO_GEOGRAPHY('POINT(1 1)')";
    private static final String POINT_TWO = "TO_GEOGRAPHY('POINT(2 2)')";

    @BeforeEach
    public void createPopulatedGeoTable() {
        engine.execute("CREATE TABLE gv (id INTEGER, g GEOGRAPHY, gm GEOMETRY, n NUMBER)");
        engine.execute("INSERT INTO gv SELECT 1, " + POINT_ONE + ", TO_GEOMETRY('POINT(1 1)'), 10");
        engine.execute("INSERT INTO gv SELECT 2, " + POINT_TWO + ", TO_GEOMETRY('POINT(2 2)'), 20");
    }

    private void assertFails(final String sql, final String expectedMessage) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(error.getMessage().contains(expectedMessage),
            "expected [" + expectedMessage + "] for [" + sql + "] but got: " + error.getMessage());
    }

    private String text(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    // ── the rejections, over rows that really hold geo values ────────────────

    @Test
    public void keyPositionsRejectAPopulatedGeoColumn() {
        assertFails("SELECT COUNT(*) FROM gv GROUP BY g",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("SELECT COUNT(*) FROM gv GROUP BY gm",
            "Expressions of type GEOMETRY cannot be used as GROUP BY keys");
        assertFails("SELECT id FROM gv ORDER BY g",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
        assertFails("SELECT ROW_NUMBER() OVER (PARTITION BY g ORDER BY id) FROM gv",
            "Expressions of type GEOGRAPHY cannot be used as PARTITION BY keys");
    }

    @Test
    public void readingAndOrderingAPopulatedGeoColumnIsRejected() {
        assertFails("SELECT MAX(g) FROM gv", "Function MAX does not support GEOGRAPHY argument type");
        assertFails("SELECT SUM(g) FROM gv", "Invalid argument types for function 'SUM': (GEOGRAPHY)");
        assertFails("SELECT UPPER(g) FROM gv",
            "Invalid argument types for function 'UPPER': (GEOGRAPHY)");
        assertFails("SELECT 'x' || g FROM gv",
            "Invalid argument types for function '||': (VARCHAR(1), GEOGRAPHY)");
        assertFails("SELECT g = g FROM gv",
            "Invalid argument types for function '=': (GEOGRAPHY, GEOGRAPHY)");
    }

    /**
     * The cast is refused even though the value is right there — the difference from a plain OBJECT,
     * whose {@code CAST(o AS VARCHAR)} returns its JSON. The {@code ST_} accessors below are the way in.
     */
    @Test
    public void castingAPopulatedGeoValueToTextIsRejected() {
        assertFails("SELECT CAST(g AS VARCHAR) FROM gv",
            "invalid type [CAST(GV.G AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TO_VARCHAR(g) FROM gv",
            "invalid type [TO_VARCHAR(GV.G)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT CAST(g AS VARIANT) FROM gv",
            "invalid type [CAST(GV.G AS VARIANT)] for parameter 'TO_VARIANT'");
    }

    /** A geo LITERAL built by the constructor is refused exactly as a geo COLUMN is. */
    @Test
    public void aConstructedGeoValueIsRefusedInTheSamePositions() {
        assertFails("SELECT SUM(" + POINT_ONE + ")",
            "Invalid argument types for function 'SUM': (GEOGRAPHY)");
        assertFails("SELECT MEDIAN(" + POINT_ONE + ")",
            "incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]");
        assertFails("SELECT STDDEV(" + POINT_ONE + ")",
            "Invalid argument types for function '*': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT ABS(" + POINT_ONE + ")",
            "Invalid argument types for function 'ABS': (GEOGRAPHY)");
    }

    // ── the ST_ surface, which none of it may touch ──────────────────────────

    /** The documented routes to a geo value's text all still work — they take a geo argument. */
    @Test
    public void theStTextAccessorsStillTakeAGeoArgument() {
        assertEquals("POINT(1 1)", text("SELECT ST_ASWKT(" + POINT_ONE + ")"));
        assertEquals("POINT(1 1)", text("SELECT ST_ASTEXT(" + POINT_ONE + ")"));
        assertEquals("POINT(1 1)", text("SELECT ST_ASWKT(TO_GEOMETRY('POINT(1 1)'))"));
        assertEquals("{\"coordinates\":[1,1],\"type\":\"Point\"}",
            text("SELECT ST_ASGEOJSON(" + POINT_ONE + ")"));
        assertEquals("0101000000000000000000F03F000000000000F03F",
            text("SELECT ST_ASWKB(" + POINT_ONE + ")"));
    }

    /** The measures and predicates take geo arguments in every position. */
    @Test
    public void theStMeasuresAndPredicatesStillTakeGeoArguments() {
        assertEquals(1.0, Double.parseDouble(text("SELECT ST_X(" + POINT_ONE + ")")), 1e-9);
        assertEquals(1.0, Double.parseDouble(text("SELECT ST_Y(" + POINT_ONE + ")")), 1e-9);
        assertEquals("true", text("SELECT ST_INTERSECTS(" + POINT_ONE + ", " + POINT_ONE + ")"));
        assertEquals("false", text("SELECT ST_DISJOINT(" + POINT_ONE + ", " + POINT_ONE + ")"));
        assertTrue(Double.parseDouble(text("SELECT ST_DISTANCE(" + POINT_ONE + ", " + POINT_TWO + ")")) > 0);
    }

    /** The constructor FUNCTIONS accept a geo value, where the CAST spellings are refused. */
    @Test
    public void theGeoConstructorsStillTakeAGeoArgument() {
        assertEquals("{\"coordinates\":[1,1],\"type\":\"Point\"}", text("SELECT TO_GEOGRAPHY(g) FROM gv"));
        assertEquals("{\"coordinates\":[1,1],\"type\":\"Point\"}", text("SELECT TO_GEOMETRY(g) FROM gv"));
    }

    /**
     * The key positions are open again once the value is TEXT — which is how geospatial data is
     * grouped and sorted at all, and the reason the rejections above are not the end of the story.
     */
    @Test
    public void groupingAndOrderingByAnStAccessorIsAccepted() {
        assertEquals(2, engine.executeQuery(
            "SELECT COUNT(*) FROM gv GROUP BY ST_ASWKT(g)").getRows().size());
        assertEquals(2, engine.executeQuery(
            "SELECT id FROM gv ORDER BY ST_ASWKT(g)").getRows().size());
        assertEquals("POINT(2 2)", text("SELECT MAX(y) FROM (SELECT ST_ASWKT(g) AS y FROM gv)"));
    }

    /** A populated geo column is still counted, de-duplicated and carried. */
    @Test
    public void distinctCountAndCarryingFormsStillWorkOverRealValues() {
        assertEquals(2, engine.executeQuery("SELECT DISTINCT g FROM gv").getRows().size());
        assertEquals("2", text("SELECT COUNT(g) FROM gv"));
        assertEquals("2", text("SELECT COUNT(DISTINCT g) FROM gv"));
        assertEquals("{\"coordinates\":[1,1],\"type\":\"Point\"}", text("SELECT ANY_VALUE(g) FROM gv"));
        assertEquals("{\"coordinates\":[1,1],\"type\":\"Point\"}", text("SELECT IFF(TRUE, g, g) FROM gv"));
        assertEquals(4, engine.executeQuery(
            "SELECT g FROM gv UNION ALL SELECT g FROM gv").getRows().size());
    }
}
