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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Where the regressions take a VECTOR (live-verified): each refuses one in a position it converts to a DOUBLE, in that
 * conversion's words, and REGR_COUNT, REGR_AVGX and REGR_AVGY take one in the position they only test for NULL;
 * REGR_SXY and REGR_R2 refuse one in the product's words, REGR_SXY listing the X first and REGR_R2 the VECTOR twice;
 * REGR_VALX and REGR_VALY answer a DOUBLE and refuse a VECTOR where they return it, and take no window.
 */
public class RegrVectorPositionTest extends MeasuredCellsTestSupport {

    /** Each regression and its answer. */
    @Test
    public void eachRegressionRefusesAVectorWhereItConvertsIt() {
        engine.execute("CREATE OR REPLACE TABLE rv_t (g INT, x VECTOR(FLOAT, 2))");
        engine.execute("INSERT INTO rv_t SELECT 1, [1,2]::VECTOR(FLOAT,2) UNION ALL SELECT 2, [3,4]::VECTOR(FLOAT,2)");
        assertCells(new String[][] {
            {"SELECT REGR_COUNT(x, g) FROM rv_t",
                "2"},
            {"SELECT REGR_COUNT(g, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_COUNT(g, x) OVER () FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_AVGX(x, g) FROM rv_t",
                "1.5"},
            {"SELECT REGR_AVGX(g, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_AVGY(g, x) FROM rv_t",
                "1.5"},
            {"SELECT REGR_SXX(g, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_SYY(g, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_SXY(x, g) FROM rv_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (NUMBER(38,0), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_INTERCEPT(g, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_R2(x, g) FROM rv_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_SLOPE(g, x) OVER () FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT COVAR_POP(g, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT CORR(x, x) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.X IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_VALX(x, g), REGR_VALY(x, g) FROM rv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RV_T.X))] for parameter 'TO_DOUBLE'"},
        });
    }

    /** Each regression and its answer. */
    @Test
    public void theProductsRefuseAVectorInTheirOwnWords() {
        engine.execute("CREATE OR REPLACE TABLE rq_t (g INT, x VECTOR(FLOAT, 2))");
        engine.execute("INSERT INTO rq_t SELECT 1, [1,2]::VECTOR(FLOAT,2) UNION ALL SELECT 2, [3,4]::VECTOR(FLOAT,2)");
        assertCells(new String[][] {
            {"SELECT REGR_SXY(g, x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), NUMBER(38,0))"},
            {"SELECT REGR_SXY(x, x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_SXY(x, g) OVER () FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (NUMBER(38,0), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_R2(g, x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_R2(x, x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_R2(x, g) OVER () FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_VALX(x, g) FROM rq_t",
                "1.0 ;; 2.0"},
            {"SELECT REGR_VALY(x, g) FROM rq_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RQ_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RQ_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_VALX(g, x) FROM rq_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RQ_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RQ_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_VALY(g, x) FROM rq_t",
                "1.0 ;; 2.0"},
            {"SELECT REGR_VALX(x, g) OVER () FROM rq_t",
                "REFUSED SQL compilation error: | Invalid function type [REGR_VALX] for window function."},
            {"SELECT VAR_SAMP(x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT VAR_POP(x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT STDDEV(x) FROM rq_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_SXY(g, g + 1) FROM rq_t",
                "0.5"},
        });
    }

    /** Each call and its answer. */
    @Test
    public void regrValxAndRegrValyAnswerADouble() {
        engine.execute("CREATE OR REPLACE TABLE rx_t (g INT, h NUMBER(10,2), x VECTOR(FLOAT, 2))");
        engine.execute("INSERT INTO rx_t SELECT 1, 1.25, [1,2]::VECTOR(FLOAT,2) UNION ALL SELECT 2, 2.5, "
            + "[3,4]::VECTOR(FLOAT,2)");
        assertCells(new String[][] {
            {"SELECT REGR_VALX(g, g), SYSTEM$TYPEOF(REGR_VALX(g, g)) FROM rx_t ORDER BY 1",
                "1.0|FLOAT[DOUBLE] ;; 2.0|FLOAT[DOUBLE]"},
            {"SELECT REGR_VALY(h, g), SYSTEM$TYPEOF(REGR_VALY(h, g)) FROM rx_t ORDER BY 1",
                "1.25|FLOAT[DOUBLE] ;; 2.5|FLOAT[DOUBLE]"},
            {"SELECT REGR_VALX(NULL, 1), REGR_VALX(1, NULL), REGR_VALX(2, 3)",
                "null|null|3.0"},
            {"SELECT REGR_VALY(x, x) FROM rx_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(RX_T.X IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), RX_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_VALY(g, x) FROM rx_t ORDER BY 1",
                "1.0 ;; 2.0"},
            {"SELECT REGR_VALY(g, h) OVER () FROM rx_t",
                "REFUSED SQL compilation error: | Invalid function type [REGR_VALY] for window function."},
            {"SELECT REGR_SXY(g, h) FROM rx_t",
                "0.625"},
            {"SELECT REGR_SXY(h, x) FROM rx_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), NUMBER(10,2))"},
            {"SELECT REGR_R2(h, x) FROM rx_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_R2(x, h) FROM rx_t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))"},
            {"SELECT REGR_SXY(x, g) FROM rx_t GROUP BY g",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'*': (NUMBER(38,0), VECTOR(FLOAT, 2))"},
        });
    }
}
