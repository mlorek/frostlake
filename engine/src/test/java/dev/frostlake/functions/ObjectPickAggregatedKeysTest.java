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
 * OBJECT_PICK picks the keys an aggregate hands it as an ARRAY (live-verified): ARRAY_AGG, ARRAY_AGG ... WITHIN
 * GROUP and ARRAY_UNIQUE_AGG, directly and grouped, as a scalar subquery's array is picked; a VARIANT holding the
 * array is still one key, its text.
 */
public class ObjectPickAggregatedKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregatedKeyArrayIsPicked() {
        engine.execute("CREATE OR REPLACE TABLE pk (o OBJECT)");
        engine.execute("INSERT INTO pk SELECT OBJECT_CONSTRUCT('a',1,'b',2,'c',3)");
        assertCells(new String[][] {
            {"SELECT OBJECT_PICK(ANY_VALUE(o), ARRAY_AGG(x)) FROM pk, (SELECT 'b' x UNION ALL SELECT 'c')",
                "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK(o, ARRAY_AGG(x)) FROM pk, (SELECT 'b' x UNION ALL SELECT 'c') GROUP BY o",
                "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK(o, (SELECT ARRAY_AGG(x) FROM (SELECT 'b' x UNION ALL SELECT 'c'))) FROM pk",
                "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK(ANY_VALUE(o), ARRAY_AGG(x) WITHIN GROUP (ORDER BY x DESC)) FROM pk, (SELECT 'b' x "
                + "UNION ALL SELECT 'c')",
                "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK(ANY_VALUE(o), ARRAY_UNIQUE_AGG(x)) FROM pk, (SELECT 'b' x UNION ALL SELECT 'c')",
                "{\"b\":2,\"c\":3}"},
            {"SELECT OBJECT_PICK(ANY_VALUE(o), TO_VARIANT(ARRAY_AGG(x))) FROM pk, (SELECT 'b' x UNION ALL SELECT 'c')",
                "{}"},
            {"SELECT OBJECT_PICK(ANY_VALUE(o), ARRAY_AGG(x)::VARIANT) FROM pk, (SELECT 'b' x UNION ALL SELECT 'c')",
                "{}"},
        });
    }
}
