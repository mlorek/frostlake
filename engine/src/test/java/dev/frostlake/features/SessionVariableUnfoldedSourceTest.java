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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A SET whose source calls a function the account's folder does not take is refused as non-constant, however deep
 * the call sits outside a scalar subquery of its own, and so is one calling a SQL function whose body calls one, a
 * conversion to VARIANT of anything but a literal, and an IS NULL over a VARIANT; the functions beside them fold (all
 * live-verified).
 */
public class SessionVariableUnfoldedSourceTest extends MeasuredCellsTestSupport {

    private static final String REFUSED =
        "REFUSED Unsupported feature 'assignment from non-constant source expression'.";

    private static final String[] UNFOLDED = {
        "SET b0 = TIME_FROM_PARTS(1, 2, 3, 4)",
        "SET b1 = DATE_FROM_PARTS(2024, 1, 2)",
        "SET b2 = TIMESTAMP_FROM_PARTS(2024, 1, 2, 3, 4, 5)",
        "SET b3 = TIMESTAMP_NTZ_FROM_PARTS(2024, 1, 2, 3, 4, 5)",
        "SET b4 = TIMESTAMP_LTZ_FROM_PARTS(2024, 1, 2, 3, 4, 5)",
        "SET b5 = TIMESTAMP_TZ_FROM_PARTS(2024, 1, 2, 3, 4, 5)",
        "SET b6 = TIMESTAMP_FROM_PARTS('2024-01-02'::DATE, '03:04:05'::TIME)",
        "SET b11 = TIME_FROM_PARTS(1, 2, 3)::VARCHAR",
        "SET b22 = DATEDIFF(day, '2024-01-01'::DATE, '2024-01-02'::DATE)",
        "SET b23 = MONTHNAME('2024-01-02'::DATE)",
        "SET b28 = DATE_FROM_PARTS(2024, 1, 2)::VARCHAR",
        "SET b29 = TIME_FROM_PARTS(1, 2, 3) IS NULL",
        "SET b30 = NVL(TIME_FROM_PARTS(1, 2, 3), '00:00:00'::TIME)",
        "SET b31 = HOUR(TIME_FROM_PARTS(1, 2, 3))",
        "SET b32 = TO_VARCHAR(TIME_FROM_PARTS(1, 2, 3))",
        "SET b37 = DATE_FROM_PARTS(2024, 1, 2) + 1",
        "SET b38 = TIME_FROM_PARTS(25, 0, 0)",
        "SET b39 = DATE_FROM_PARTS(2024, 13, 1)",
        "SET o1 = REPEAT('a', 2)",
        "SET o2 = HASH(1)",
        "SET o3 = RANDOM()",
        "SET o4 = UUID_STRING()",
        "SET o6 = SQUARE(2)",
        "SET o7 = BITAND(1, 3)",
        "SET o8 = ROUND(1.5)",
        "SET o11 = DECODE(1, 1, 'a')",
        "SET o14 = STRTOK('a b', ' ', 1)",
        "SET o15 = REGEXP_REPLACE('ab', 'b', 'c')",
        "SET o16 = SHA2('a')",
        "SET o17 = MD5('a')",
        "SET o18 = BASE64_ENCODE('a')",
        "SET o20 = LENGTH('abc')",
    };

    private static final String[] FOLDED = {
        "SET b7 = TO_TIME('01:02:03')",
        "SET b8 = TO_DATE('2024-01-02')",
        "SET b9 = TO_TIMESTAMP('2024-01-02 03:04:05')",
        "SET b10 = TO_TIMESTAMP_NTZ('2024-01-02 03:04:05.123')",
        "SET b12 = (SELECT TIME_FROM_PARTS(1, 2, 3))",
        "SET b13 = DATEADD(day, 1, '2024-01-02'::DATE)",
        "SET b14 = DATEADD(hour, 1, '01:02:03'::TIME)",
        "SET b15 = TIMEADD(hour, 1, '01:02:03'::TIME(3))",
        "SET b16 = CURRENT_DATE()",
        "SET b17 = CURRENT_TIME()",
        "SET b18 = CURRENT_TIMESTAMP()",
        "SET b19 = DATE_TRUNC('day', '2024-01-02 03:04:05'::TIMESTAMP_NTZ)",
        "SET b20 = LAST_DAY('2024-01-02'::DATE)",
        "SET b21 = YEAR('2024-01-02'::DATE)",
        "SET b24 = TIME_SLICE('2024-01-02 03:04:05'::TIMESTAMP_NTZ, 1, 'HOUR')",
        "SET b25 = ADD_MONTHS('2024-01-02'::DATE, 1)",
        "SET b26 = TRY_TO_TIME('01:02:03')",
        "SET b27 = TO_TIME('01:02:03', 'HH24:MI:SS')",
        "SET b33 = '01:02:03'::TIME(3)",
        "SET b34 = CAST('01:02:03' AS TIME(3))",
        "SET b35 = TO_TIME('01:02:03.123456')",
        "SET b36 = TIMESTAMPADD(minute, 1, '01:02:03'::TIME)",
        "SET o0 = CONCAT_WS(',', 'a', 'b')",
        "SET o5 = ZEROIFNULL(NULL)",
        "SET o9 = TO_NUMBER('12')",
        "SET o10 = TRY_TO_NUMBER('12')",
        "SET o12 = NULLIF(1, 2)",
        "SET o13 = GREATEST(1, 2)",
        "SET o19 = TO_BOOLEAN('true')",
        "SET o21 = DATE_PART(year, '2024-01-02'::DATE)",
        "SET o22 = EXTRACT(year FROM '2024-01-02'::DATE)",
        "SET o23 = TO_CHAR('2024-01-02'::DATE, 'YYYY')",
        "SET o24 = SYSDATE()",
        "SET o25 = LOCALTIMESTAMP()",
        "SET o26 = GETDATE()",
    };

    @Test
    public void theFolderTakesSomeFunctionsAndNotOthers() {
        for (final String sql : UNFOLDED) {
            assertAnswer(REFUSED, sql);
        }
        for (final String sql : FOLDED) {
            assertAnswer("Statement executed successfully.", sql);
        }
    }

    @Test
    public void aRefusedSourceSetsNothing() {
        assertAnswer(REFUSED, "SET b0 = TIME_FROM_PARTS(1, 2, 3, 4)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Session variable '$B0' does not "
            + "exist", "SELECT SYSTEM$TYPEOF($b0)");
    }

    @Test
    public void aSqlFunctionOverAnUnfoldedOneIsRefused() {
        engine.execute("CREATE OR REPLACE FUNCTION svu_chr(x INT) RETURNS VARCHAR AS 'CHR(x)'");
        engine.execute("CREATE OR REPLACE FUNCTION svu_one() RETURNS INT AS '1'");
        assertCells(new String[][] {
            {"SET svu1 = svu_chr(65)", REFUSED},
            {"SET svu2 = svu_one()", "Statement executed successfully."},
            {"SELECT $svu2", "1"},
        });
    }

    @Test
    public void aSqlFunctionReachingOneThroughAnotherOrInAnExpressionIsRefused() {
        Assumptions.assumeFalse(isLiveSnowflake(), "a nested SQL function and one inside an expression are unmeasured");
        engine.execute("CREATE OR REPLACE FUNCTION svu_chr(x INT) RETURNS VARCHAR AS 'CHR(x)'");
        engine.execute("CREATE OR REPLACE FUNCTION svu_nest() RETURNS VARCHAR AS 'svu_chr(65)'");
        engine.execute("CREATE OR REPLACE FUNCTION svu_up(x VARCHAR) RETURNS VARCHAR AS 'UPPER(x)'");
        assertCells(new String[][] {
            {"SET svu3 = svu_nest()", REFUSED},
            {"SET svu4 = 'x' || svu_chr(66)", REFUSED},
            {"SET svu5 = svu_up('a')", "Statement executed successfully."},
        });
    }

    @Test
    public void aVariantConversionOfANonLiteralOrItsNullTestIsRefused() {
        final String done = "Statement executed successfully.";
        assertCells(new String[][] {
            {"SET svv1 = NULL::VARIANT::VARIANT", REFUSED},
            {"SET svv2 = TO_VARIANT(NULL::VARCHAR)", REFUSED},
            {"SET svv3 = TO_VARIANT(TO_VARIANT(NULL))", REFUSED},
            {"SET svv4 = NULL::VARIANT IS NULL", REFUSED},
            {"SET svv5 = (SELECT TO_VARIANT(1)) IS NULL", REFUSED},
            {"SET svv6 = NULL::VARIANT", done},
            {"SET svv7 = TO_VARIANT(NULL)", done},
            {"SET svv8 = IFF(TRUE, NULL::VARIANT, NULL::VARIANT)", done},
            {"SET svv9 = COALESCE(NULL::VARIANT, NULL::VARIANT)", done},
            {"SET svv10 = 'x' || TO_VARIANT(1)", done},
            {"SET svv11 = TO_VARIANT(1)::NUMBER", done},
        });
    }
}
