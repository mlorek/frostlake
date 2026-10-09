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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * APPROX_PERCENTILE and APPROX_PERCENTILE_ESTIMATE in their two readings. Up to 1400 values the sketch is the
 * values themselves and the answer the exact interpolated percentile; past that it is read as a digest, each
 * value a centroid whose mass sits around it, so the answer over an even series moves by half a position and
 * reaches past both ends. A state written with any other weight is read as a digest at every size.
 */
public class ApproxPercentileRegimesTest extends BaseDatabaseTest {

    private static final double CLOSE = 1e-6;

    private static String series(final int n) {
        return "(SELECT (ROW_NUMBER() OVER (ORDER BY seq4()) - 1)::INT AS v FROM TABLE(GENERATOR(ROWCOUNT => " + n
            + ")))";
    }

    /** A state of one unit centroid per value of the series. */
    private static String unitState(final int n, final String mean) {
        return "(SELECT OBJECT_CONSTRUCT('state', ARRAY_AGG(x) WITHIN GROUP (ORDER BY v, k), 'type', 'tdigest',"
            + " 'version', 1) AS s FROM (SELECT v, 0 AS k, (" + mean + ")::DOUBLE AS x FROM " + series(n)
            + " UNION ALL SELECT v, 1, 1::DOUBLE FROM " + series(n) + "))";
    }

    private double[] numbers(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final double[] out = new double[row.getValues().size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((Number) row.getValue(i)).doubleValue();
        }
        return out;
    }

    private double[] percentiles(final int n) {
        return numbers("SELECT APPROX_PERCENTILE(v, 0), APPROX_PERCENTILE(v, 0.01), APPROX_PERCENTILE(v, 0.25),"
            + " APPROX_PERCENTILE(v, 0.5), APPROX_PERCENTILE(v, 0.99), APPROX_PERCENTILE(v, 1) FROM " + series(n));
    }

    private double[] estimates(final String state) {
        return numbers("SELECT APPROX_PERCENTILE_ESTIMATE(s, 0), APPROX_PERCENTILE_ESTIMATE(s, 0.01),"
            + " APPROX_PERCENTILE_ESTIMATE(s, 0.25), APPROX_PERCENTILE_ESTIMATE(s, 0.5),"
            + " APPROX_PERCENTILE_ESTIMATE(s, 0.99), APPROX_PERCENTILE_ESTIMATE(s, 1) FROM " + state);
    }

    @Test
    public void upTo1400ValuesTheAnswerIsTheExactPercentile() {
        assertArrayEquals(new double[] {0.0, 0.04, 1.0, 2.0, 3.96, 4.0}, percentiles(5));
        assertArrayEquals(new double[] {0.0, 0.99, 24.75, 49.5, 98.01, 99.0}, percentiles(100));
        assertArrayEquals(new double[] {0.0, 13.99, 349.75, 699.5, 1385.01, 1399.0}, percentiles(1400));
        assertArrayEquals(new double[] {0.0, 13.99, 349.75, 699.5, 1385.01, 1399.0}, estimates(unitState(1400, "v")));
        final String units =
            "(SELECT PARSE_JSON('{\"state\":[1,1,2,1,10,1,100,1],\"type\":\"tdigest\",\"version\":1}') AS s)";
        assertArrayEquals(new double[] {1.0, 1.03, 1.75, 6.0, 97.3, 100.0}, estimates(units));
    }

    @Test
    public void pastThatTheSketchIsReadAsADigest() {
        assertArrayEquals(new double[] {-0.5, 13.51, 349.75, 700.0, 1386.49, 1400.5}, percentiles(1401));
        assertArrayEquals(new double[] {-0.5, 19.5, 499.5, 999.5, 1979.5, 1999.5}, percentiles(2000));
        assertArrayEquals(new double[] {-0.5, 13.51, 349.75, 700.0, 1386.49, 1400.5}, estimates(unitState(1401, "v")));
        assertArrayEquals(new double[] {-0.5, 210.5, 140250.5, 561750.5, 2203740.5, 2248499.5},
            estimates(unitState(1500, "v * v")));
    }

    @Test
    public void aStateOfOtherWeightsIsADigestAtEverySize() {
        final String heavy = "(SELECT PARSE_JSON('{\"state\":[1,10,9,30],\"type\":\"tdigest\",\"version\":1}') AS s)";
        assertArrayEquals(new double[] {-1.0, -0.84, 3.0, 7.0, 14.84, 15.0}, estimates(heavy));
        final String mixed =
            "(SELECT PARSE_JSON('{\"state\":[1,1,2,1,3,5,10,1],\"type\":\"tdigest\",\"version\":1}') AS s)";
        assertArrayEquals(new double[] {0.5, 0.58, 2.1666666667, 2.8333333333, 10.98, 11.1666666667}, estimates(mixed));
    }

    private static void assertArrayEquals(final double[] expected, final double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], CLOSE, "position " + i);
        }
    }
}
