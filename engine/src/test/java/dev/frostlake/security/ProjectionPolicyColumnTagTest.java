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
 * A projection policy's body reads the tags of the column it judges: SYSTEM$GET_TAG_ON_CURRENT_COLUMN answers the
 * column's own value of the tag, else its table's, so a policy keyed on the tag allows the projection once the column
 * or its table carries the value, and refuses it again once neither does (live-verified).
 */
public class ProjectionPolicyColumnTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aProjectionPolicyReadsItsColumnsTag() {
        engine.execute("CREATE OR REPLACE TAG tg2");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x', PROJECTION_CONSTRAINT(ALLOW => TRUE), "
            + "PROJECTION_CONSTRAINT(ALLOW => FALSE))");
        engine.execute("CREATE OR REPLACE TABLE tp (a INT, b INT)");
        engine.execute("INSERT INTO tp VALUES (1, 2)");
        engine.execute("ALTER TABLE tp MODIFY COLUMN a SET PROJECTION POLICY pp1");
        assertCells(new String[][] {
            {"SELECT a FROM tp",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  A"},
            {"ALTER TABLE tp MODIFY COLUMN a SET TAG tg2 = 'x'", "Statement executed successfully."},
            {"SELECT a FROM tp", "1"},
            {"SELECT b, a FROM tp", "2|1"},
            {"ALTER TABLE tp MODIFY COLUMN a SET TAG tg2 = 'y'", "Statement executed successfully."},
            {"SELECT a FROM tp",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  A"},
            {"ALTER TABLE tp MODIFY COLUMN a UNSET TAG tg2", "Statement executed successfully."},
            {"ALTER TABLE tp SET TAG tg2 = 'x'", "Statement executed successfully."},
            {"SELECT a FROM tp", "1"},
            {"ALTER TABLE tp UNSET TAG tg2", "Statement executed successfully."},
            {"SELECT a FROM tp",
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  A"},
            {"ALTER TABLE tp MODIFY COLUMN a SET TAG tg2 = 'x'", "Statement executed successfully."},
            {"SELECT COUNT(a) FROM tp", "1"},
            {"SELECT a + 1 FROM tp", "2"},
        });
    }
}
