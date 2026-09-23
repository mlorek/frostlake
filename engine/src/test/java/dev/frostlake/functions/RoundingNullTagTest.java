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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CEIL, FLOOR and ROUND over a NULL are tagged as the NULL is, {@code [SB1]}, whatever width they declare
 * (live-verified).
 */
public class RoundingNullTagTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The bare NULL, a typed one, and a scale argument. */
    @Test
    public void roundingOverNullIsTaggedAsTheNull() {
        final String[][] cells = {
            {"SELECT SYSTEM$TYPEOF(CEIL(NULL))",
                "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(FLOOR(NULL))",
                "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(ROUND(NULL))",
                "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(CEIL(NULL, 1))",
                "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(ROUND(NULL, 2))",
                "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(CEIL(NULL::INT))",
                "NUMBER(38,0)[SB1]"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
