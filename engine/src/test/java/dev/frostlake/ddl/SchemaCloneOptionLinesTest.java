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
 * A CREATE SCHEMA ... CLONE whose option is cut short or misspelt reports the further lines the account reports:
 * INCLUDE alone at the end, INCLUDE INTERNAL STAGE at STAGE and what follows, IGNORE TABLES with no point of
 * Time Travel at TABLES and the word after its WITH; a clone at a point before its source schema was made is
 * refused as Time Travel that is not available for the schema (all live-verified).
 */
public class SchemaCloneOptionLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void cutShortOptionsAndEarlyPoints() {
        engine.execute("CREATE OR REPLACE TABLE t1 (i INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'STAGE'. | "
                + "syntax error line 1 at position 69 unexpected '<EOF>'.",
            "CREATE OR REPLACE SCHEMA sc1 CLONE test_schema INCLUDE INTERNAL STAGE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'TABLES'. | "
                + "syntax error line 1 at position 55 unexpected 'INSUFFICIENT'.",
            "CREATE SCHEMA sc8 CLONE test_schema IGNORE TABLES WITH INSUFFICIENT DATA RETENTION");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'TABLES'. | "
                + "syntax error line 1 at position 55 unexpected 'INSUFFICIENT'.",
            "CREATE SCHEMA sc8 CLONE test_schema IGNORE TABLES WITH INSUFFICIENT DATA");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'TABLES'. | "
                + "syntax error line 1 at position 54 unexpected '<EOF>'.",
            "CREATE SCHEMA sc8 CLONE test_schema IGNORE TABLES WITH");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'TABLES'. | "
                + "syntax error line 1 at position 55 unexpected 'INSUFFICIENT'.",
            "CREATE SCHEMA sc8 CLONE test_schema IGNORE TABLES WITH INSUFFICIENT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '<EOF>'.",
            "CREATE SCHEMA sc8 CLONE test_schema INCLUDE INTERNAL");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '<EOF>'.",
            "CREATE SCHEMA sc8 CLONE test_schema INCLUDE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '<EOF>'.",
            "CREATE SCHEMA sc8 CLONE test_schema IGNORE HYBRID");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'STAGE'. | "
                + "syntax error line 1 at position 59 unexpected 'IGNORE'. | syntax error line 1 at position 73 "
                + "unexpected 'TABLES'.",
            "CREATE SCHEMA sc8 CLONE test_schema INCLUDE INTERNAL STAGE IGNORE HYBRID TABLES");
        assertAnswer("REFUSED Time travel data is not available for schema TEST_SCHEMA. The requested time is either "
                + "beyond the allowed time travel period or before the object creation time.",
            "CREATE SCHEMA sc6 CLONE test_schema AT (OFFSET => -100000)");
        assertAnswer("REFUSED Time travel data is not available for schema TEST_SCHEMA. The requested time is either "
                + "beyond the allowed time travel period or before the object creation time.",
            "CREATE SCHEMA sc6 CLONE test_schema BEFORE (OFFSET => -100000)");
        assertAnswer("REFUSED Time travel data is not available for schema TEST_SCHEMA. The requested time is either "
                + "beyond the allowed time travel period or before the object creation time.",
            "CREATE SCHEMA sc6 CLONE test_schema AT (TIMESTAMP => '2000-01-01 00:00:00'::TIMESTAMP_LTZ)");
        assertAnswer("REFUSED Time travel data is not available for schema TEST_SCHEMA. The requested time is either "
                + "beyond the allowed time travel period or before the object creation time.",
            "CREATE SCHEMA sc6 CLONE test_schema AT (OFFSET => -100000) IGNORE TABLES WITH INSUFFICIENT DATA "
                + "RETENTION");
        assertAnswer("REFUSED Time travel data is not available for schema TEST_SCHEMA. The requested time is either "
                + "beyond the allowed time travel period or before the object creation time.",
            "CREATE SCHEMA sc6 CLONE test_schema AT (OFFSET => -100000) INCLUDE INTERNAL STAGES");
    }
}
