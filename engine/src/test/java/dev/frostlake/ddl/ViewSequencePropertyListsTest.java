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
 * A view's and a sequence's properties take a list or a constant as a table's do, and the object's kind judges the
 * name (live-verified): STAGE_FILE_FORMAT and STAGE_COPY_OPTIONS are incompatible with a view, any other name is no
 * property of a table, and none at all of a sequence. An empty list is no value of a switch, and a clone written with
 * COMMENT = NULL keeps no comment.
 */
public class ViewSequencePropertyListsTest extends MeasuredCellsTestSupport {

    @Test
    public void propertiesWithListsConstantsAndNull() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tq910 (a INT) INCREMENT = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'INCREMENT'."},
            {"CREATE OR REPLACE TABLE tq910 (a INT) ORDER = TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'ORDER'."},
            {"CREATE OR REPLACE VIEW pv910 STAGE_FILE_FORMAT = (TYPE = CSV) AS SELECT 1 a",
                "REFUSED SQL compilation error: | options STAGE_FILE_FORMAT and {1} are incompatible."},
            {"CREATE OR REPLACE VIEW pv910 STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE) AS SELECT 1 a",
                "REFUSED SQL compilation error: | options STAGE_COPY_OPTIONS and {1} are incompatible."},
            {"CREATE OR REPLACE VIEW pv910 x = (1) AS SELECT 1 a",
                "REFUSED SQL compilation error: | invalid property 'x' for 'TABLE'"},
            {"CREATE OR REPLACE SEQUENCE ps910 STAGE_FILE_FORMAT = (TYPE = CSV)",
                "REFUSED SQL compilation error: | invalid property 'STAGE_FILE_FORMAT' for 'SEQUENCE'"},
            {"CREATE OR REPLACE SEQUENCE ps910 x = (1)",
                "REFUSED SQL compilation error: | invalid property 'x' for 'SEQUENCE'"},
            {"CREATE OR REPLACE TABLE pt910 (a INT)", "Table PT910 successfully created."},
            {"CREATE OR REPLACE TABLE tq910 (a INT) CHANGE_TRACKING = ()",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"CREATE OR REPLACE TABLE tq910 (a INT) CHANGE_TRACKING = (a = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'CHANGE_TRACKING'"},
            {"CREATE OR REPLACE TABLE tc910 (a INT) COMMENT = 'src'", "Table TC910 successfully created."},
            {"CREATE OR REPLACE TABLE tc910b CLONE tc910 COMMENT = NULL", "Table TC910B successfully created."},
            {"SHOW TABLES LIKE 'TC910B' ->> SELECT \"comment\" FROM $1", ""},
        });
    }
}
