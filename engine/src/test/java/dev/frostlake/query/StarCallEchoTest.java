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
 * A star argument in a call the plan echoes: an object built of the star keeps the star and names its columns
 * after the call, any other call reads them as its arguments; each column is named with its relation
 * (live-verified).
 */
public class StarCallEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void anObjectBuiltOfTheStar() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT {FZ.*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID))'",
            "SELECT TO_JSON(DISTINCT {* EXCLUDE (b)}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID))'",
            "SELECT TO_JSON(DISTINCT {* EXCLUDE b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID))'",
            "SELECT TO_JSON(DISTINCT {* ILIKE 'i%'}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'all' for function 'TO_JSON(ALL "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(ALL {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(F.ID, F.B, G.ID, G.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ f JOIN FZ g ON f.id = g.id");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(F.ID, F.B))'",
            "SELECT TO_JSON(DISTINCT {f.*}) FROM FZ f JOIN FZ g ON f.id = g.id");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_STAR'.",
            "SELECT TO_JSON(DISTINCT *) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT FZ.ID)'",
            "SELECT TO_JSON(DISTINCT id) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT UPPER(DISTINCT {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'HASH(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT HASH(DISTINCT {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_VARIANT(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_VARIANT(DISTINCT {*}) FROM FZ");
        assertAnswer(
            "0",
            "SELECT COUNT(DISTINCT {*}) FROM FZ");
        assertAnswer(
            "[]",
            "SELECT ARRAY_AGG(DISTINCT {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(\"values\".K, \"values\".B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM (SELECT id AS k, b FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(\"values\".\"a b\", \"values\".C))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM (SELECT 1 AS \"a b\", 2 AS c)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)())'",
            "SELECT TO_JSON(DISTINCT {*})");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT OBJECT_CONSTRUCT(*)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT('a', FZ.ID))'",
            "SELECT TO_JSON(DISTINCT OBJECT_CONSTRUCT('a', id)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT 1 FROM FZ WHERE TO_JSON(DISTINCT {*}) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT {*}) x FROM FZ GROUP BY id");
    }

    @Test
    public void otherCallsAndOtherRelations() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT HASH(FZ.ID, "
                + "FZ.B))'",
            "SELECT UPPER(DISTINCT HASH(*)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT "
                + "OBJECT_CONSTRUCT_KEEP_NULL(*)(FZ.ID, FZ.B))'",
            "SELECT UPPER(DISTINCT OBJECT_CONSTRUCT_KEEP_NULL(*)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT HASH(FZ.ID, "
                + "FZ.B))'",
            "SELECT UPPER(DISTINCT HASH(FZ.*)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT HASH(FZ.ID))'",
            "SELECT UPPER(DISTINCT HASH(* EXCLUDE (b))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT HASH(FZ.ID, "
                + "FZ.B, 1))'",
            "SELECT UPPER(DISTINCT HASH(*, 1)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT HASH(1, "
                + "FZ.ID, FZ.B))'",
            "SELECT UPPER(DISTINCT HASH(1, *)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT COUNT(*))'",
            "SELECT UPPER(DISTINCT COUNT(*)) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(Q.ID, Q.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ AS q");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM test_schema.FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(V.ID, V.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM (SELECT * FROM FZ) v");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B, Y.ID, Y.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ, FZ y");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(VALUES.COLUMN1, VALUES.COLUMN2))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1, 2))");
        answer(
            "SELECT TO_JSON(DISTINCT {*}) FROM (VALUES (1, 2)) AS w (m, n)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FLATTEN.SEQ, FLATTEN.KEY, FLATTEN.PATH, FLATTEN.INDEX, FLATTEN.VALUE, "
                + "FLATTEN.THIS))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM TABLE(FLATTEN([1]))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)())'",
            "SELECT TO_JSON(DISTINCT {* EXCLUDE (id, b)}) FROM FZ");
        answer(
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ f JOIN FZ g USING (id)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(C.ID))'",
            "WITH c AS (SELECT id FROM FZ) SELECT TO_JSON(DISTINCT {*}) FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'all' for function 'UPPER(ALL "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT UPPER(ALL {*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B), 1)'",
            "SELECT TO_JSON(DISTINCT {*}, 1) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'TO_JSON(DISTINCT "
                + "OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'",
            "SELECT TO_JSON(DISTINCT {*}) FROM FZ WHERE id = 1 ORDER BY 1");
    }
}
