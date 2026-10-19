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

import org.junit.jupiter.api.Test;

/**
 * An IFF or a CASE over texts and a NULL reads beside a number at the scale its texts spell, the NULL widening
 * nothing — {@code IFF(TRUE, '1.50', NULL) * 1} is 1.5, a NUMBER(19,1) — and LAST_QUERY_ID's refusal echoes the
 * product as the plan converts it, unfolded and uncast: {@code (TO_NUMBER(IFF(…), 18, 1)) * 1} (live-verified).
 */
public class TextConditionalNullScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullResultWidensNothing() {
        assertCells(new String[][] {
            {"SELECT IFF(TRUE, '1.50', '2.0') * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', '2.0') * 1)",
                "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '1.50', '2') * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', '2') * 1)", "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '1.500', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.500', NULL) * 1)",
                "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '10', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '10', NULL) * 1)", "10|NUMBER(19,0)[SB16]"},
            {"SELECT IFF(TRUE, '1.0', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.0', NULL) * 1)", "1|NUMBER(19,0)[SB16]"},
            {"SELECT IFF(TRUE, '0.10', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '0.10', NULL) * 1)",
                "0.1|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '1.50', NULL::VARCHAR) * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL::VARCHAR) * 1)",
                "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT CASE WHEN TRUE THEN '1.50' WHEN FALSE THEN NULL ELSE '2.125' END * 1, SYSTEM$TYPEOF(CASE "
                + "WHEN TRUE THEN '1.50' WHEN FALSE THEN NULL ELSE '2.125' END * 1)",
                "1.500|NUMBER(19,3)[SB16]"},
            {"SELECT IFF(TRUE, '1.50', NULL) * 2, SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL) * 2)",
                "3.0|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '1.50', NULL) / 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL) / 1)",
                "1.5000000|NUMBER(24,7)[SB16]"},
            {"SELECT IFF(TRUE, '1.50', NULL) - 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL) - 1)",
                "0.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(FALSE, NULL, '1.50') * 1, SYSTEM$TYPEOF(IFF(FALSE, NULL, '1.50') * 1)",
                "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT CASE WHEN FALSE THEN '1.50' END * 1, SYSTEM$TYPEOF(CASE WHEN FALSE THEN '1.50' END * 1)",
                "null|NUMBER(19,1)[SB1]"},
            {"SELECT IFF(TRUE, '-1.50', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '-1.50', NULL) * 1)",
                "-1.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '100.25', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '100.25', NULL) * 1)",
                "100.25|NUMBER(19,2)[SB16]"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2.125') * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2.125'), 18, 3) AS NUMBER(19,3)) AS "
                + "NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', '2.0') * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', '2.0'), 18, 1) AS NUMBER(19,1)) AS "
                + "NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.500', NULL) * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.500', SYSTEM$NULL_TO_TEXT(null)), 18, 1)) * 1'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '10', NULL) * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '10', SYSTEM$NULL_TO_TEXT(null)), 18, 0)) * 1'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', NULL) + 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', SYSTEM$NULL_TO_TEXT(null)), 18, 1)) + (CAST(1 AS "
                + "NUMBER(3,1)))'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', NULL) * 2)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', SYSTEM$NULL_TO_TEXT(null)), 18, 1)) * 2'"},
            {"SELECT LAST_QUERY_ID(1 * IFF(TRUE, '1.50', NULL))",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'1 * (TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', SYSTEM$NULL_TO_TEXT(null)), 18, 1))'"},
        });
    }

    @Test
    public void theIndexEchoKeepsTheProduct() {
        assertCells(new String[][] {
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.50', NULL) * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.50', SYSTEM$NULL_TO_TEXT(null)), 18, 1)) * 1'"},
            {"SELECT LAST_QUERY_ID(CASE WHEN TRUE THEN '1.50' END * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(CASE_FLATTENED(CAST(TRUE AS BOOLEAN), '1.50', SYSTEM$NULL_TO_TEXT(null)), 18, 1)) * 1'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, '1.5', NULL) * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), '1.5', SYSTEM$NULL_TO_TEXT(null)), 18, 1)) * 1'"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, NULL, '1.50') * 1)",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(TO_NUMBER(IFF(CAST(TRUE AS BOOLEAN), SYSTEM$NULL_TO_TEXT(null), '1.50'), 18, 1)) * 1'"},
            {"SELECT IFF(TRUE, '1.50', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL) * 1)",
                "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT CASE WHEN TRUE THEN '1.50' END * 1, SYSTEM$TYPEOF(CASE WHEN TRUE THEN '1.50' END * 1)",
                "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(TRUE, '1.5', NULL) * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.5', NULL) * 1)", "1.5|NUMBER(19,1)[SB16]"},
            {"SELECT IFF(FALSE, '1.50', NULL) * 1, SYSTEM$TYPEOF(IFF(FALSE, '1.50', NULL) * 1)",
                "null|NUMBER(19,1)[SB1]"},
            {"SELECT IFF(TRUE, '1.50', '2.125') * 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', '2.125') * 1)",
                "1.500|NUMBER(19,3)[SB16]"},
            {"SELECT IFF(TRUE, '1.50', NULL) + 1, SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL) + 1)",
                "2.5|NUMBER(19,1)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, '1.50', NULL))", "VARCHAR(134217728)[LOB]"},
        });
    }
}
