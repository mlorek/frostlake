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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * SHOW GRANTS ON CORTEX SEARCH SERVICE names a service as an object of its schema, and refuses one that does not exist
 * as the account does, the privilege hint naming the kind in words (live-verified).
 */
public class CortexSearchServiceGrantNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aServiceThatDoesNotExist() {
        engine.execute("CREATE OR REPLACE TABLE rj911 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO rj911 VALUES (1, 'x'), (2, 'y')");
        assertCells(new String[][] {
            {"SHOW GRANTS ON CORTEX SEARCH SERVICE nosuch911",
                "REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH911' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH911."},
        });
    }
}
