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
 * A table property may be given NULL, a decimal or a parenthesized list of constants, and its name judges the value
 * (live-verified): a name that is no property is refused whatever its value; a decimal is refused as written; NULL
 * is a text's value and refused a switch; a list is a text parameter's, refused a count parameter by the parser's
 * word for it, a switch property as an invalid value specified, and the comment by the element that is no text. A
 * comment written as a word is the word as a name reads it.
 */
public class TablePropertyConstantValuesTest extends MeasuredCellsTestSupport {

    private static final String T = "CREATE OR REPLACE TABLE tq2 (a INT) ";
    private static final String CREATED = "Table TQ2 successfully created.";
    private static final String COMMENT = "SELECT comment FROM information_schema.tables WHERE table_schema = "
        + "'TEST_SCHEMA' AND table_name = 'TQ2'";

    @Test
    public void aNameThatIsNoPropertyIsRefusedWhateverItsValue() {
        final String refused = "REFUSED SQL compilation error: | invalid property 'x' for 'TABLE'";
        assertCells(new String[][] {
            {T + "x = (1)", refused},
            {T + "x = (a, b)", refused},
            {T + "x = (a = 1)", refused},
            {T + "x = NULL", refused},
            {T + "x = 1.5", refused},
        });
    }

    @Test
    public void aDecimalIsRefusedAsWritten() {
        final String[] names = {"CHANGE_TRACKING", "DATA_RETENTION_TIME_IN_DAYS", "ENABLE_SCHEMA_EVOLUTION",
            "MAX_DATA_EXTENSION_TIME_IN_DAYS", "COMMENT", "DEFAULT_DDL_COLLATION"};
        for (final String name : names) {
            assertCells(new String[][] {
                {T + name + " = 1.5",
                    "REFUSED SQL compilation error: | invalid value [1.5] for parameter '" + name + "'"},
            });
        }
    }

    @Test
    public void nullIsATextsValue() {
        assertCells(new String[][] {
            {T + "CHANGE_TRACKING = NULL",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'CHANGE_TRACKING'"},
            {T + "COMMENT = NULL", CREATED},
            {COMMENT, "null"},
        });
    }

    @Test
    public void aListIsJudgedByItsNamesType() {
        assertCells(new String[][] {
            {T + "CHANGE_TRACKING = (1)", "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {T + "ENABLE_SCHEMA_EVOLUTION = (1)",
                "REFUSED Invalid value specified for property 'ENABLE_SCHEMA_EVOLUTION'"},
            {T + "DATA_RETENTION_TIME_IN_DAYS = (1)", "REFUSED SQL compilation error: | invalid value "
                + "[TOK_CONSTANT_LIST] for parameter 'DATA_RETENTION_TIME_IN_DAYS'"},
            {T + "MAX_DATA_EXTENSION_TIME_IN_DAYS = (1)", "REFUSED SQL compilation error: | invalid value "
                + "[TOK_CONSTANT_LIST] for parameter 'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {T + "DEFAULT_DDL_COLLATION = (1)", CREATED},
            {T + "COMMENT = (1)", "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
        });
    }

    @Test
    public void aCommentWrittenAsAWordIsTheWordAsANameReadsIt() {
        assertCells(new String[][] {
            {T + "COMMENT = abc", CREATED},
            {COMMENT, "ABC"},
            {T + "COMMENT = $$dollar$$", CREATED},
            {COMMENT, "dollar"},
        });
    }
}
