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
 * The syntax-error lines of a CLUSTER BY on a view and of a materialized view whose COPY GRANTS follows its
 * CLUSTER BY (all live-verified).
 */
public class ViewClusterSyntaxLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void viewClusterLines() {
        engine.execute("CREATE OR REPLACE TABLE t2099 (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'CLUSTER'.",
            "ALTER VIEW nosuch_v CLUSTER BY (x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'BY'.",
            "CREATE OR REPLACE VIEW v_or CLUSTER BY (x) AS SELECT x, y FROM t2099");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'GRANTS'. | "
                + "syntax error line 1 at position 68 unexpected '<EOF>'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) COPY GRANTS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'CLUSTER'.",
            "ALTER VIEW nosuch_v CLUSTER BY (x, y)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'BY'. | syntax "
                + "error line 1 at position 41 unexpected 'AS'.",
            "CREATE OR REPLACE VIEW v_or CLUSTER BY x AS SELECT x FROM t2099");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'GRANTS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) COPY GRANTS AS SELECT x FROM t2099");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'CLUSTER'.",
            "ALTER VIEW nosuch_v CLUSTER");
    }
}
