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
 * A VECTOR is refused by the statistics, the boolean aggregates and the percentiles in their window forms as in
 * their plain ones (live-verified): CORR, COVAR_POP, COVAR_SAMP and REGR_SLOPE in the DOUBLE conversion's words,
 * BOOLAND_AGG, BOOLOR_AGG and BOOLXOR_AGG in TO_BOOLEAN's, and PERCENTILE_CONT and PERCENTILE_DISC OVER () as their
 * plain forms are; REGR_COUNT counts the rows.
 */
public class VectorAggregateRefusalTest extends MeasuredCellsTestSupport {

    /** Each aggregate and its answer. */
    @Test
    public void aVectorIsRefusedInEveryForm() {
        engine.execute("CREATE OR REPLACE TABLE sv_t (g INT, x VECTOR(FLOAT, 2))");
        engine.execute("INSERT INTO sv_t SELECT 1, [1,2]::VECTOR(FLOAT,2) UNION ALL SELECT 2, [3,4]::VECTOR(FLOAT,2)");
        assertCells(new String[][] {
            {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) FROM sv_t",
                "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]"},
            {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) OVER () FROM sv_t",
                "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]"},
            {"SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x) FROM sv_t",
                "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]"},
            {"SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x) OVER () FROM sv_t",
                "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]"},
            {"SELECT CORR(x, g) FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(SV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), SV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT CORR(x, g) OVER () FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(SV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), SV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT CORR(g, x) FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(SV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), SV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT COVAR_POP(x, g) FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(SV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), SV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT COVAR_SAMP(x, g) OVER () FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(SV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), SV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_SLOPE(x, g) FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(IFF(SV_T.G IS NULL, SYSTEM$NULL_TO_VECTOR("
                + "NULL), SV_T.X))] for parameter 'TO_DOUBLE'"},
            {"SELECT REGR_COUNT(x, g) FROM sv_t",
                "2"},
            {"SELECT BOOLAND_AGG(x), BOOLOR_AGG(x) FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(SV_T.X)] for parameter 'TO_BOOLEAN'"},
            {"SELECT BOOLXOR_AGG(x) FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(SV_T.X)] for parameter 'TO_BOOLEAN'"},
            {"SELECT BOOLAND_AGG(x) OVER () FROM sv_t",
                "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(SV_T.X)] for parameter 'TO_BOOLEAN'"},
            {"SELECT MEDIAN(x) FROM sv_t",
                "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]"},
            {"SELECT MEDIAN(x) OVER () FROM sv_t",
                "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]"},
        });
    }
}
