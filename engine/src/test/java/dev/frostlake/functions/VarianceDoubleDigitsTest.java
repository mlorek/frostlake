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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * The variance family over FLOATs, digit for digit (live-verified through {@code ::NUMBER(38,20)}, the
 * only surface that shows the seventeenth digit): VARIANCE over 0.1, 0.2, 0.4 is 0.02333333333333333440
 * where a two-pass sum of squared deviations reads …3787, and the same digits come back from a table, a
 * UNION ALL, a VALUES list and a flattened array. STDDEV is the root of that variance. The account reads
 * the variance from its sums — see VarianceFloatSumsTest for the shapes that tell the forms apart.
 */
public class VarianceDoubleDigitsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vd3 (f FLOAT)");
        engine.execute("INSERT INTO vd3 VALUES (0.1), (0.2), (0.4)");
        engine.execute("CREATE OR REPLACE TABLE vd6 (f FLOAT)");
        engine.execute("INSERT INTO vd6 VALUES (0.1), (0.7), (0.3), (0.9), (0.2), (0.8)");
        engine.execute("CREATE OR REPLACE TABLE vd7 (f FLOAT)");
        engine.execute("INSERT INTO vd7 VALUES (1.5), (2.5), (3.5), (4.5), (5.5), (6.5), (7.5)");
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void theVarianceIsWelfordsDigits() {
        assertEquals("0.02333333333333333440", cell("SELECT VARIANCE(f)::NUMBER(38,20) FROM vd3"));
        assertEquals("0.02333333333333333440", cell("SELECT VAR_SAMP(f)::NUMBER(38,20) FROM vd3"));
        assertEquals("0.01555555555555555684", cell("SELECT VARIANCE_POP(f)::NUMBER(38,20) FROM vd3"));
        assertEquals("0.01555555555555555684", cell("SELECT VAR_POP(f)::NUMBER(38,20) FROM vd3"));
        assertEquals("0.11600000000000001976", cell("SELECT VARIANCE(f)::NUMBER(38,20) FROM vd6"));
        assertEquals("0.09666666666666667851", cell("SELECT VARIANCE_POP(f)::NUMBER(38,20) FROM vd6"));
        assertEquals("4.66666666666666696273", cell("SELECT VARIANCE(f)::NUMBER(38,20) FROM vd7"));
    }

    @Test
    public void theShapeOfTheSourceDoesNotChangeTheDigits() {
        assertEquals("0.02333333333333333440", cell("SELECT VARIANCE(f)::NUMBER(38,20)"
            + " FROM (SELECT 0.1::FLOAT AS f UNION ALL SELECT 0.2 UNION ALL SELECT 0.4)"));
        assertEquals("0.02333333333333333440", cell("SELECT VARIANCE(f)::NUMBER(38,20)"
            + " FROM (VALUES (0.1::FLOAT), (0.2), (0.4)) AS v(f)"));
        assertEquals("0.02333333333333333440", cell("SELECT VARIANCE(value::FLOAT)::NUMBER(38,20)"
            + " FROM TABLE(FLATTEN(PARSE_JSON('[0.1,0.2,0.4]')))"));
    }

    @Test
    public void theStandardDeviationIsTheRootOfThatVariance() {
        assertEquals("0.15275252316519466467", cell("SELECT STDDEV(f)::NUMBER(38,20) FROM vd3"));
        assertEquals("0.12472191289246471746", cell("SELECT STDDEV_POP(f)::NUMBER(38,20) FROM vd3"));
    }

    @Test
    public void theEdgesAreUnchanged() {
        assertEquals("null", cell("SELECT VARIANCE(f)::NUMBER(38,20) FROM (SELECT 0.1::FLOAT AS f)"));
        assertEquals("0.00000000000000000000",
            cell("SELECT TO_VARCHAR(VARIANCE_POP(f)::NUMBER(38,20)) FROM (SELECT 0.1::FLOAT AS f)"));
        assertEquals("null", cell("SELECT VARIANCE(f) FROM vd3 WHERE 1=0"));
        // A NUMBER input keeps its declared-scale presentation.
        engine.execute("CREATE OR REPLACE TABLE vdn (n NUMBER(5,2))");
        engine.execute("INSERT INTO vdn VALUES (0.1), (0.2), (0.4)");
        assertEquals("0.02333333330000000000", cell("SELECT VARIANCE(n)::NUMBER(38,20) FROM vdn"));
    }
}
