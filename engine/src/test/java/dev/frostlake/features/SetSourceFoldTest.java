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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * What SET folds as its source (live-verified): a scalar subquery handing out a VARIANT or an OBJECT under a LIMIT is
 * refused, without the LIMIT, or over another type, it sets; a DECODE sets, except one with no default whose search
 * matches the subject, NULL to NULL included; the inferred spellings of the functions the folder does not take are
 * refused with them; a branch the fold does not take is not judged; a SQL function whose body is a query sets.
 */
public class SetSourceFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void theSourcesTheFoldTakes() {
        engine.execute("CREATE OR REPLACE FUNCTION sf931() RETURNS VARCHAR AS 'SELECT CHR(65)'");
        assertCells(new String[][] {
            {"SET v931 = (SELECT NULL::VARIANT LIMIT 1)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = (SELECT NULL::VARIANT)", "Statement executed successfully."},
            {"SET v931 = (SELECT NULL::OBJECT LIMIT 1)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = (SELECT NULL::OBJECT)", "Statement executed successfully."},
            {"SET v931 = (SELECT 1 LIMIT 1)", "Statement executed successfully."},
            {"SET v931 = DECODE(1, 1, 'a', 'b')", "Statement executed successfully."},
            {"SET v931 = DECODE(1, 2, 'a')", "Statement executed successfully."},
            {"SET v931 = DECODE(NULL, NULL, 'a')",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = DECODE(1, 1, 'a')",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = DATEFROMPARTS(2024, 1, 2)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = TIMEFROMPARTS(1, 2, 3)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = TIMESTAMPFROMPARTS(2024, 1, 2, 3, 4, 5)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = TIMEDIFF(day, '2024-01-01'::DATE, '2024-01-02'::DATE)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = TIMESTAMPDIFF(day, '2024-01-01'::DATE, '2024-01-02'::DATE)",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = LEN('abc')", "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = CHAR(65)", "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = POW(2, 3)", "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = MD5_HEX('a')",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = SHA2_HEX('a')",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = CHR(65)", "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = IFF(TRUE, 1, CHR(65))", "Statement executed successfully."},
            {"SET v931 = CASE WHEN TRUE THEN 1 ELSE CHR(65) END", "Statement executed successfully."},
            {"SET v931 = sf931()", "Statement executed successfully."},
            {"SET v931 = LENGTH('abc')",
                "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
            {"SET v931 = POWER(2, 3)", "REFUSED Unsupported feature 'assignment from non-constant source expression'."},
        });
    }
}
