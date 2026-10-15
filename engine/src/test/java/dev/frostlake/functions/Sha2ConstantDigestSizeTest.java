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
 * SHA2's digest size must be a constant: a column, a typed NULL and an expression over an untyped NULL are
 * refused while the statement compiles, under the name the call is written with and over a table with no row,
 * where the bare word NULL is the size 0, refused when a row is read (live-verified).
 */
public class Sha2ConstantDigestSizeTest extends MeasuredCellsTestSupport {

    @Test
    public void theBareWordAndWhatIsNoConstant() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT SHA2('a', NULL::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found 'TN.N'",
            "SELECT SHA2('a', n) FROM tn");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2_HEX('a', NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2_BINARY('a', NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2(NULL, NULL)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: 255. Reason: invalid SHA2 digest size",
            "SELECT SHA2('a', 255)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', 256)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found 'TN.N'",
            "SELECT SHA2(s, n) FROM tn");
        assertAnswer(
            "REFUSED Invalid parameter value: 5. Reason: invalid SHA2 digest size",
            "SELECT SHA2(NULL, 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "SELECT SHA2('a', NULL + 1)");
    }

    @Test
    public void theOtherSpellingsAndAnEmptyTable() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        answer(
            "CREATE OR REPLACE TABLE ta (arr ARRAY, v VARIANT, b BOOLEAN, d DATE)");
        answer(
            "INSERT INTO ta SELECT ARRAY_CONSTRUCT(1, 2), PARSE_JSON('[1]'), TRUE, '2024-01-02'::DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2_HEX needs to be constant, found 'TN.N'",
            "SELECT SHA2_HEX('a', n) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2_BINARY needs to be constant, found "
                + "'TN.N'",
            "SELECT SHA2_BINARY('a', n) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2_HEX needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT SHA2_HEX('a', NULL::INT)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', 128 + 128)");
        assertAnswer(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "SELECT SHA2('a', '256')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found 'TN.A'",
            "SELECT SHA2('a', a) FROM tn");
        assertAnswer(
            "",
            "SELECT SHA2('a', NULL) FROM tn WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found 'TN.N'",
            "SELECT SHA2('a', n) FROM tn WHERE 1 = 0");
    }

    @Test
    public void expressionsOverAColumnAndANull() {
        answer(
            "CREATE OR REPLACE TABLE tn (a INT, n INT, s VARCHAR)");
        answer(
            "INSERT INTO tn VALUES (1, NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found 'TN.N + 1'",
            "SELECT SHA2('a', n + 1) FROM tn");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 2 to function SHA2 needs to be constant, found "
                + "'ABS(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SHA2('a', ABS(NULL))");
    }
}
