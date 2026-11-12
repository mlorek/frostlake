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
 * A set operation's column is a VARIANT whichever side of a number or a BOOLEAN the VARIANT arm stands on: a number
 * followed by a VARIANT is a VARIANT (live-verified), and so is a VARIANT followed by one. The VARIANT-first order
 * left the column with no type, so what read it through a derived table or a CTE saw none, and x + 1 was declared
 * text.
 */
public class VariantFirstSetOperationTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariantFirstArmKeepsTheColumnAVariant() {
        engine.execute("CREATE OR REPLACE TABLE vfs (v VARIANT, n INT)");
        engine.execute("INSERT INTO vfs SELECT PARSE_JSON('{\"k\":1}'), 2");
        assertAnswer("VARIANT[LOB]|INTEGER ;; VARIANT[LOB]|OBJECT", "SELECT SYSTEM$TYPEOF(x), TYPEOF(x) FROM "
            + "(SELECT v x FROM vfs UNION ALL SELECT n FROM vfs) ORDER BY 2");
        assertAnswer("VARIANT[LOB] ;; VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(x) FROM (SELECT v x FROM vfs UNION ALL SELECT 1.5::FLOAT FROM vfs)");
        assertAnswer("VARIANT[LOB] ;; VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(x) FROM (SELECT v x FROM vfs UNION ALL SELECT TRUE FROM vfs)");
        assertAnswer("3|FLOAT[DOUBLE]", "WITH c AS (SELECT v x FROM vfs UNION ALL SELECT n FROM vfs) "
            + "SELECT (x + 1)::VARCHAR, SYSTEM$TYPEOF(x + 1) FROM c WHERE TYPEOF(x) = 'INTEGER'");
    }
}
