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

package dev.frostlake.stage;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A staged JSON record is the OBJECT the account holds: its keys sorted at every level, whatever order the file
 * writes them in (all live-verified).
 */
public class StagedJsonRecordKeyOrderTest extends MeasuredCellsTestSupport {

    private static final String SORTED = "{\"a\":\"x\",\"m\":{\"b\":3,\"y\":2},\"z\":1}";

    @Test
    public void keysSortedAtEveryLevel() {
        engine.execute("CREATE OR REPLACE STAGE st_order");
        stageLocalFile("st_order", "order.json", "{\"z\":1,\"a\":\"x\",\"m\":{\"y\":2,\"b\":3}}\n");
        engine.execute("CREATE OR REPLACE FILE FORMAT fj_order TYPE = JSON");
        final String source = " FROM @st_order/order.json (FILE_FORMAT => 'fj_order')";
        assertAnswer(SORTED, "SELECT $1" + source);
        assertAnswer("{\"b\":3,\"y\":2}", "SELECT $1:m" + source);
        assertAnswer("[\"a\",\"m\",\"z\"]", "SELECT OBJECT_KEYS($1)" + source);
        assertAnswer(SORTED, "SELECT TO_JSON($1)" + source);
        assertAnswer(SORTED, "SELECT $1::VARCHAR" + source);
        assertAnswer("OBJECT", "SELECT TYPEOF($1)" + source);
    }
}
