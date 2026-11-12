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
 * BEGIN is no reserved word (documented: it is not among the reserved keywords), so a column, an alias or a table
 * named begin is an ordinary name, and a flow chain after it runs as one statement ("Flow operator ->>",
 * documented). The lexer counted every such BEGIN as a scripting block's opening, so a later top-level ->> was read
 * as a statement's end: the chain was refused as two statements, or its second stage ran alone. A block's own BEGIN
 * still ends its statements at the arrow. Unmeasured on live.
 */
public class BeginNamedColumnFlowTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnOrAnAliasNamedBeginKeepsTheChainWhole() {
        engine.execute("CREATE OR REPLACE TABLE bncf (begin INT, finish INT)");
        engine.execute("INSERT INTO bncf VALUES (1, 2), (3, 4)");
        assertAnswer("2", "SELECT begin FROM bncf ->> SELECT COUNT(*) AS c FROM $1");
        assertAnswer("1", "SELECT 1 AS begin ->> SELECT * FROM $1");
        assertAnswer("2", """
            SELECT CASE WHEN finish > 0 THEN begin ELSE finish END AS x FROM bncf ->> SELECT COUNT(*) FROM $1""");
    }

    @Test
    public void aBlocksArrowStillEndsItsStatement() {
        assertAnswer("1", "BEGIN SELECT 1 ->> SELECT 2; RETURN 1; END");
    }
}
