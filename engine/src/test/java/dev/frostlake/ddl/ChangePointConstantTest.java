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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A time-travel or change-tracking point that reads a name. In a query - AT, BEFORE, a CHANGES clause's AT, BEFORE
 * and END - a point is read outside the table's rows, so a column of the table is an invalid identifier, placed
 * at the name. A CREATE STREAM's point and a clone's are read with the table in scope: a column resolves and
 * is refused as no constant, echoed as the plan holds it - {@code CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))} whatever
 * precision was written, {@code NEGATE(CT_CONST.ID)}, {@code (CT_CONST.ID * 2) + 1} - while an unknown name is
 * an invalid identifier there too and a function whose value moves is refused first. Every answer is
 * live-verified.
 */
public class ChangePointConstantTest extends MeasuredCellsTestSupport {

    @Test
    public void aPointThatReadsAName() {
        answer(
            "CREATE OR REPLACE TABLE ct_const (id INTEGER) CHANGE_TRACKING = TRUE");
        answer(
            "INSERT INTO ct_const VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 70 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (OFFSET => id)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 | invalid identifier 'CT_CONST.ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => ct_const.id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 70 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (OFFSET => id + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 71 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (OFFSET => -id)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (STATEMENT => id::VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 77 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) BEFORE (TIMESTAMP => id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 88 | invalid identifier 'ID'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (OFFSET => 0) END (OFFSET => id)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 | invalid identifier 'NOSUCH'",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => nosuch::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 | Session variable '$CT_NOSUCH' does not "
                + "exist",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => $ct_nosuch::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 70 | Session variable '$CT_NOSUCH' does not "
                + "exist",
            "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) AT (OFFSET => $ct_nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument OFFSET to function AT needs to be constant, found "
                + "'CT_CONST.ID'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (OFFSET => id)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function BEFORE needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const BEFORE (TIMESTAMP => id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 63 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => nosuch::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => ct_const.id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument OFFSET to function AT needs to be constant, found "
                + "'CT_CONST.ID + 1'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (OFFSET => id + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => TO_TIMESTAMP_LTZ(id))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument JOBID to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS VARCHAR(134217728))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (STATEMENT => id::VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 63 | Session variable '$CT_NOSUCH' does not "
                + "exist",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => $ct_nosuch::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 60 | Session variable '$CT_NOSUCH' does not "
                + "exist",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (OFFSET => $ct_nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_NTZ(0))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => id::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => id::TIMESTAMP_LTZ(3))");
        assertAnswer(
            "REFUSED SQL compilation error: line 1 at position 81: | Query contains time travel to a version "
                + "containing function 'CURRENT_DATE', but change tracking is not supported on queries with "
                + "non-deterministic functions in versions.",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (TIMESTAMP => DATEADD(day, -id, "
                + "CURRENT_DATE)::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument OFFSET to function AT needs to be constant, found "
                + "'NEGATE(CT_CONST.ID)'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (OFFSET => -id)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument OFFSET to function AT needs to be constant, found "
                + "'(CT_CONST.ID * 2) + 1'",
            "CREATE OR REPLACE STREAM s1 ON TABLE ct_const AT (OFFSET => id * 2 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'ID'",
            "SELECT * FROM ct_const AT (TIMESTAMP => id::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'ID'",
            "SELECT * FROM ct_const AT (OFFSET => id)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'ID'",
            "SELECT * FROM ct_const BEFORE (STATEMENT => id::VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | Session variable '$CT_NOSUCH' does not "
                + "exist",
            "SELECT * FROM ct_const AT (OFFSET => $ct_nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | Session variable '$CT_NOSUCH' does not "
                + "exist",
            "SELECT * FROM ct_const AT (TIMESTAMP => $ct_nosuch::TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument OFFSET to function AT needs to be constant, found "
                + "'CT_CONST.ID'",
            "CREATE OR REPLACE TABLE ct_clone CLONE ct_const AT (OFFSET => id)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument TIMESTAMP to function AT needs to be constant, found "
                + "'CAST(CT_CONST.ID AS TIMESTAMP_LTZ(0))'",
            "CREATE OR REPLACE TABLE ct_clone CLONE ct_const AT (TIMESTAMP => id::TIMESTAMP_LTZ)");
    }
}
