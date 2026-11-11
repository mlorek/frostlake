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

package dev.frostlake.transaction;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A transaction writing the session's own temporary table holds no lock SHOW LOCKS lists (live-verified): after an
 * INSERT into it, SHOW LOCKS lists nothing.
 */
public class TemporaryTableShowLocksTest extends MeasuredCellsTestSupport {

    @Test
    public void aTemporaryTablesWriteListsNoLock() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TEMPORARY TABLE tt916 (a INT)", "Table TT916 successfully created."},
            {"BEGIN", "Statement executed successfully."},
            {"INSERT INTO tt916 VALUES (1)", "1"},
            {"SHOW LOCKS ->> SELECT \"resource\", \"type\", \"status\" FROM $1 ORDER BY 1", ""},
            {"COMMIT", "Statement executed successfully."},
            {"DROP TABLE IF EXISTS tt916", "TT916 successfully dropped."},
        });
    }
}
