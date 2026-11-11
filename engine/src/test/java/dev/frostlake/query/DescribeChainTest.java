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
 * A DESCRIBE with properties is a chain's stage like any other (live-verified): {@code DESCRIBE TABLE t TYPE = STAGE
 * ->> SELECT ...} reads the properties up to the arrow, a name without its value refuses the arrow, and a parameter
 * DESCRIBE refuses is refused for the chain as a whole.
 */
public class DescribeChainTest extends MeasuredCellsTestSupport {

    @Test
    public void describePropertiesBeforeAnArrow() {
        engine.execute("CREATE OR REPLACE TABLE dt935 (a INT)");
        assertCells(new String[][] {
            {"DESCRIBE TABLE dt935 TYPE = STAGE ->> SELECT \"property\" FROM $1 WHERE \"parent_property\" = "
                + "'STAGE_COPY_OPTIONS' AND \"property\" = 'ON_ERROR'",
                "ON_ERROR"},
            {"DESCRIBE TABLE dt935 TYPE = COLUMNS ->> SELECT \"name\" FROM $1", "A"},
            {"DESC TABLE dt935 TYPE = STAGE ->> SELECT 1 FROM $1 LIMIT 1", "1"},
            {"DESCRIBE TABLE dt935 ->> SELECT \"name\" FROM $1", "A"},
        });
    }

    @Test
    public void chainShapes() {
        engine.execute("CREATE OR REPLACE TABLE dt945 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW dv945 AS SELECT 1 AS b");
        assertCells(new String[][] {
            {"DESCRIBE TABLE dt945 TYPE = STAGE ->> SELECT COUNT(*) > 0 FROM $1", "true"},
            {"DESCRIBE TABLE dt945 TYPE = COLUMNS ->> SELECT \"name\" FROM $1", "A"},
            {"DESCRIBE TABLE dt945 TYPE = 'COLUMNS' ->> SELECT \"name\" FROM $1", "A"},
            {"DESCRIBE VIEW dv945 ->> SELECT \"name\" FROM $1", "B"},
            {"DESCRIBE TABLE dt945, TYPE = COLUMNS ->> SELECT \"name\" FROM $1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','."},
            {"DESCRIBE SCHEMA PUBLIC TYPE = COLUMNS ->> SELECT COUNT(*) >= 0 FROM $1", "true"},
            {"DESCRIBE TABLE dt945 TYPE = STAGE, TYPE = COLUMNS ->> SELECT \"name\" FROM $1", "A"},
            {"DESCRIBE TABLE dt945 FOO = 1 ->> SELECT 1", "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"DESCRIBE TABLE dt945 TYPE = COLUMNS->>SELECT \"name\" FROM $1", "A"},
            {"BEGIN DESCRIBE TABLE dt945 TYPE = COLUMNS ->> SELECT \"name\" FROM $1; RETURN 'b'; END;", "b"},
            {"DESCRIBE TABLE dt945 x ->> SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '->>'."},
            {"DESCRIBE TABLE dt945 TYPE ->> SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '->>'."},
            {"DESCRIBE TABLE dt945 TYPE = ->> SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '->>'."},
        });
    }
}
