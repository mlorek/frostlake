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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The syntax-error lines of a comma at either end of a parenthesized list - an external table's options, a list of
 * values, a stage's option lists - and of a view's column list followed by a comma (all live-verified).
 */
public class ListCommaPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void listCommaPlaces() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        engine.execute("CREATE OR REPLACE STAGE cs");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected ','. | syntax "
                + "error line 1 at position 105 unexpected ')'.",
            "CREATE OR REPLACE EXTERNAL TABLE pe (a INT AS (value:c1::INT)) LOCATION = @cs FILE_FORMAT = (, TYPE = "
                + "CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 104 unexpected ')'.",
            "CREATE OR REPLACE EXTERNAL TABLE pe (a INT AS (value:c1::INT)) LOCATION = @cs FILE_FORMAT = (TYPE = CSV,"
                + ")");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected ','. | syntax "
                + "error line 1 at position 94 unexpected ')'.",
            "CREATE EXTERNAL TABLE pe (a INT AS (value:c1::INT)) LOCATION = @cs FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected ','. | syntax "
                + "error line 1 at position 78 unexpected ')'.",
            "CREATE OR REPLACE EXTERNAL TABLE pe LOCATION = @cs FILE_FORMAT = (, TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV NULL_IF = ('a',))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV NULL_IF = ('a', 'b',))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ')'.",
            "CREATE OR REPLACE FILE FORMAT pf TYPE = CSV NULL_IF = ('a',)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected ','. | syntax "
                + "error line 1 at position 66 unexpected ''a''. | syntax error line 1 at position 70 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV NULL_IF = (, 'a'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'b'.",
            "CREATE OR REPLACE VIEW pv (b), CHANGE_TRACKING = TRUE AS SELECT a FROM ct");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'b'.",
            "CREATE OR REPLACE VIEW pv (b, c), CHANGE_TRACKING = TRUE AS SELECT a, a FROM ct");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'b'.",
            "CREATE OR REPLACE VIEW pv (b) , COMMENT = 'x' AS SELECT a FROM ct");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'b'.",
            "CREATE OR REPLACE VIEW pv (b), AS SELECT a FROM ct");
    }

    @Test
    public void listCommaFollowUps() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV,)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE,)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'b'.",
            "CREATE OR REPLACE SECURE VIEW pv (b), COMMENT = 'x' AS SELECT a FROM ct");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'b'.",
            "CREATE VIEW IF NOT EXISTS pv (b), COMMENT = 'x' AS SELECT a FROM ct");
    }
}
