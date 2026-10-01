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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A double wrapped into a VARIANT converts back to text in one of two forms. A double live's compiler folds —
 * a literal under casts and signs, {@code + - *}, IFF and CASE over a constant condition, ABS, PI, TO_DOUBLE,
 * FLOOR, GREATEST, COALESCE, and a derived column projecting one — keeps its shortest round-trip form
 * with every digit ({@code 2.0}, {@code 1.0E20}, {@code 0.30000000000000004}). Every other double — a division, SQRT, EXP, POWER, ROUND, CEIL, a stored
 * column — takes the FLOAT text ({@code 2}, {@code 1e+20}). The same text shows through {@code ::STRING},
 * TO_VARCHAR and CONCAT. Every cell is live-verified.
 */
public class FoldedDoubleVariantTextTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE OR REPLACE TABLE ft (f FLOAT, o INT)");
        engine.execute("INSERT INTO ft VALUES (2, 1), (2.5, 2), (1e20, 3), (0.1, 4), (1e-7, 5)");
    }

    @AfterEach
    public void dropTable() {
        engine.execute("DROP TABLE IF EXISTS ft");
    }

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** {@code SELECT TO_VARIANT(<double>)::VARCHAR}. */
    private String wrapped(final String doubleExpression) {
        return answer("SELECT TO_VARIANT(" + doubleExpression + ")::VARCHAR");
    }

    @Test
    public void aFoldedDoubleKeepsItsRoundTripForm() {
        assertEquals("2.0", wrapped("2::FLOAT"));
        assertEquals("1.0E20", wrapped("1e20::FLOAT"));
        assertEquals("1.0E-7", wrapped("1e-7::FLOAT"));
        assertEquals("3.0", wrapped("1.5::FLOAT * 2"));
        assertEquals("-0.0", wrapped("-0.0::FLOAT"));
        assertEquals("0.30000000000000004", wrapped("0.1::FLOAT + 0.2::FLOAT"));
        assertEquals("1.4142135623730951", wrapped("1.4142135623730951::FLOAT"));
        assertEquals("3.141592653589793", wrapped("PI()"));
        assertEquals("1.23456789015E10", wrapped("12345678901.5::FLOAT"));
        assertEquals("2.0", wrapped("3::FLOAT - 1"));
        assertEquals("-2.0", wrapped("-2::FLOAT"));
        assertEquals("2.0", wrapped("TO_DOUBLE('2')"));
        assertEquals("2.0", wrapped("IFF(TRUE, 2::FLOAT, NULL)"));
        assertEquals("2.0", wrapped("PI() * 0 + 2"));
        assertEquals("2.0", wrapped("ABS(-2::FLOAT)"));
        assertEquals("2.0", wrapped("FLOOR(2.5::FLOAT)"));
        assertEquals("2.0", wrapped("GREATEST(2::FLOAT, 1)"));
        assertEquals("2.0", wrapped("COALESCE(2::FLOAT, 1)"));
        assertEquals("2.0", wrapped("CASE WHEN TRUE THEN 2::FLOAT END"));
        assertEquals("2.0", answer("SELECT 2::FLOAT::VARIANT::VARCHAR"));
        assertEquals("3.0", answer("SELECT (1.5::FLOAT * 2)::VARIANT::VARCHAR"));
        assertEquals("2.0", answer("WITH c AS (SELECT 2::FLOAT AS f) SELECT TO_VARIANT(f)::VARCHAR FROM c"));
        assertEquals("2.0", answer("SELECT TO_VARIANT(f)::VARCHAR FROM (SELECT 2::FLOAT AS f)"));
        assertEquals("2.0", wrapped("(SELECT 2::FLOAT)"));
    }

    @Test
    public void aComputedDoubleTakesTheFloatText() {
        assertEquals("2", wrapped("SQRT(4)"));
        assertEquals("2", wrapped("SQRT(4) + 0"));
        assertEquals("2", wrapped("4::FLOAT / 2"));
        assertEquals("1", wrapped("EXP(0)"));
        assertEquals("1e+20", wrapped("POWER(10, 20)"));
        assertEquals("2e-07", wrapped("SQRT(4) * 1e-7"));
        assertEquals("1e-05", wrapped("SQRT(1e-10)"));
        assertEquals("-2", wrapped("-SQRT(4)"));
        assertEquals("0", wrapped("SQRT(0)"));
        assertEquals("2", wrapped("ROUND(2.4::FLOAT)"));
        assertEquals("2", wrapped("CEIL(1.5::FLOAT)"));
        assertEquals("-1", wrapped("SIGN(-2::FLOAT)"));
        assertEquals("2", wrapped("MOD(5::FLOAT, 3)"));
        assertEquals("4", wrapped("SQUARE(2::FLOAT)"));
        assertEquals("2", wrapped("ABS(SQRT(4))"));
        assertEquals("3", wrapped("1.5::FLOAT * 2 / 1"));
        assertEquals("2", answer("SELECT SQRT(4)::VARIANT::VARCHAR"));
        assertEquals("2", answer("WITH c AS (SELECT SQRT(4) AS f) SELECT TO_VARIANT(f)::VARCHAR FROM c"));
        assertEquals("2 2.5 1e+20 0.1 1e-07",
            answer("SELECT LISTAGG(TO_VARIANT(f)::VARCHAR, ' ') WITHIN GROUP (ORDER BY o) FROM ft"));
        assertEquals("2 2.5 1e+20 0.1 1e-07",
            answer("SELECT LISTAGG(TO_VARIANT(SQRT(f * f))::VARCHAR, ' ') WITHIN GROUP (ORDER BY o) FROM ft"));
        assertEquals("1.414213562", wrapped("SQRT(2)"));
        assertEquals("2.5", wrapped("SQRT(6.25)"));
    }

    @Test
    public void everyStringConversionReadsTheSameText() {
        assertEquals("true", answer("SELECT TO_VARIANT(SQRT(4))::STRING = '2'"));
        assertEquals("2", answer("SELECT CONCAT(TO_VARIANT(SQRT(4)), '')"));
        assertEquals("2.0", answer("SELECT CONCAT(TO_VARIANT(2::FLOAT), '')"));
        assertEquals("2", answer("SELECT TO_VARCHAR(TO_VARIANT(SQRT(4)))"));
        assertEquals("2.0", answer("SELECT TO_VARCHAR(TO_VARIANT(2::FLOAT))"));
        assertEquals("2", answer("SELECT TO_VARIANT(SQRT(4))::VARCHAR(1)"));
        assertEquals("2|2.0", answer("SELECT TO_VARIANT(SQRT(4))::VARCHAR || '|' || TO_VARIANT(2::FLOAT)::VARCHAR"));
        assertEquals("2", answer("SELECT PARSE_JSON('2.0')::VARCHAR"));
    }
}
