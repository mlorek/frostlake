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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The VARIANCE family presented at the scale its column DECLARES.
 *
 * <p>★ THE TYPE WAS ALREADY RIGHT AND THE VALUE WAS NOT. {@code NUMBER(38, min(12, 2s + 6))} is what both
 * engines declare for an exact input of scale s, and a CTAS already stored the value correctly — it was
 * only the bare SELECT that handed back the raw double. So this pads the value out to the scale that was
 * being declared all along, the way AVG already did.
 *
 * <p>★ AN APPROXIMATE INPUT KEEPS THE DOUBLE, which is why the scale is applied where the input is still
 * typed rather than at the end: over a FLOAT the whole family stays FLOAT, and a scaled decimal would
 * render the other way.
 *
 * <p>NOT COVERED HERE: COVAR_SAMP, STDDEV and STDDEV_POP are declared FLOAT and belong to the float-text
 * width, not to this scale rule; DIVISION is the other half of the same task and lands separately.
 */
public class VarianceDeclaredScaleTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE var_scale (a NUMBER(10,2), f FLOAT)");
        engine.execute("INSERT INTO var_scale VALUES (1.00, 1.0), (2.00, 2.0), (3.00, 3.0), (5.00, 5.0)");
    }

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    @Test
    void theSampleVarianceIsPaddedToItsDeclaredScale() {
        assertEquals("2.9166666667", one("SELECT VARIANCE(a) FROM var_scale"));
        assertEquals("2.9166666667", one("SELECT VAR_SAMP(a) FROM var_scale"));
    }

    @Test
    void thePopulationVarianceIsPaddedToo() {
        assertEquals("2.1875000000", one("SELECT VARIANCE_POP(a) FROM var_scale"));
    }

    @Test
    void theDeclaredTypeIsUnchanged() {
        engine.execute("CREATE OR REPLACE TABLE var_scale_t AS SELECT VARIANCE(a) AS c FROM var_scale");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE var_scale_t");
        rs.next();
        assertEquals("NUMBER(38,10)", String.valueOf(rs.getValue(1)));
    }

    @Test
    void aStoredValueStillAgreesWithTheSelected() {
        engine.execute("CREATE OR REPLACE TABLE var_scale_s AS SELECT VARIANCE(a) AS c FROM var_scale");
        assertEquals("2.9166666667", one("SELECT c FROM var_scale_s"));
        assertEquals("2.9166666667", one("SELECT VARIANCE(a) FROM var_scale"));
    }

    @Test
    void theNeighboursThatWereAlreadyRightAreUnmoved() {
        assertEquals("2.75000000", one("SELECT AVG(a) FROM var_scale"));
        assertEquals("2.1875", one("SELECT COVAR_POP(a, a) FROM var_scale"));
        assertEquals("1.0", one("SELECT CORR(a, a) FROM var_scale"));
        assertEquals("11.00", one("SELECT SUM(a) FROM var_scale"));
    }

    @Test
    void tooFewRowsIsStillNull() {
        engine.execute("CREATE OR REPLACE TABLE var_one (a NUMBER(10,2))");
        engine.execute("INSERT INTO var_one VALUES (1.00)");
        assertEquals("null", one("SELECT VARIANCE(a) FROM var_one"));
    }
}
