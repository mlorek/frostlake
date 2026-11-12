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

package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * EXPLAIN takes a CALL with an anonymous procedure and plans it as a Result naming the call over an empty ValuesClause
 * (live-verified).
 */
public class AnonymousProcedureExplainTest extends MeasuredCellsTestSupport {

    @Test
    public void theCallIsPlannedAsAResultOverAValuesClause() {
        assertCells(new String[][] {
            {"EXPLAIN WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|P()|null|null|null ;; "
                + "1|1|[0]|ValuesClause|null|null|()|null|null|null"},
        });
    }
}
