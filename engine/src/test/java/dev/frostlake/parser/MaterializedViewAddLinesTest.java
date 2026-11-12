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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A row access policy added to a materialized view is refused at the ADD alone: the policy's column list is not read
 * on (live-verified).
 */
public class MaterializedViewAddLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aRowAccessPolicyAddedToAMaterializedViewIsOneLine() {
        assertCells(new String[][] {
            {"ALTER MATERIALIZED VIEW mv931 ADD ROW ACCESS POLICY rp931 ON (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'ADD'."},
        });
    }
}
