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

package dev.frostlake.stream;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A stream on a view and a CHANGES clause over one hand out what the view hands out (live-verified): the rows the
 * row access policy of a table under the view keeps, each value of a masked column masked, before the view's
 * filter and projection read them, so an expression over a masked column reads the masked value; then the rows
 * the view's own row access policy keeps. A table stream and a table's CHANGES read the table so already, and so
 * do a join view's, an append-only stream's and a stream showing its initial rows, and a CTAS from the stream
 * copies no more than the reader sees.
 */
public class ViewStreamPolicyTest extends MeasuredCellsTestSupport {

    /** Keeps the statements around a point apart, so each falls in a moment of its own on either side. */
    private static void pause() {
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void aViewOverAPolicedTable() {
        engine.execute("CREATE OR REPLACE TABLE pt (id INT, region STRING, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY prap AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE OR REPLACE MASKING POLICY pmp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("ALTER TABLE pt ADD ROW ACCESS POLICY prap ON (region)");
        engine.execute("ALTER TABLE pt MODIFY COLUMN secret SET MASKING POLICY pmp");
        engine.execute("CREATE OR REPLACE VIEW pv CHANGE_TRACKING = TRUE AS SELECT * FROM pt");
        engine.execute("CREATE OR REPLACE STREAM psv ON VIEW pv");
        engine.execute("CREATE OR REPLACE STREAM pst ON TABLE pt");
        pause();
        engine.execute("SET p1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("INSERT INTO pt VALUES (1, 'EU', 'a'), (2, 'US', 'b')");
        pause();
        assertCells(new String[][] {
            {"SELECT id, secret FROM pv ORDER BY 1", "1|***"},
            {"SELECT id, secret, METADATA$ACTION FROM psv ORDER BY 1", "1|***|INSERT"},
            {"SELECT id, secret, METADATA$ACTION FROM pst ORDER BY 1", "1|***|INSERT"},
            {"SELECT id, secret FROM pv CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) "
                + "ORDER BY 1",
                "1|***"},
            {"SELECT id, secret FROM pv CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) "
                + "ORDER BY 1",
                "1|***"},
            {"SELECT id, secret FROM pt CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) "
                + "ORDER BY 1",
                "1|***"},
            {"SELECT COUNT(*) FROM psv", "1"},
            {"SELECT id, region FROM psv WHERE region = 'US'", ""},
            {"CREATE OR REPLACE TABLE pcopy AS SELECT id, secret FROM psv", "Table PCOPY successfully created."},
            {"SELECT id, secret FROM pcopy ORDER BY 1", "1|***"},
        });
    }

    @Test
    public void aViewJoiningAPolicedTableToAMaskedOne() {
        engine.execute("CREATE OR REPLACE TABLE ja (id INT, region STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE jb (id INT, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY jrap AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE OR REPLACE MASKING POLICY jmp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("ALTER TABLE ja ADD ROW ACCESS POLICY jrap ON (region)");
        engine.execute("ALTER TABLE jb MODIFY COLUMN secret SET MASKING POLICY jmp");
        engine.execute("CREATE OR REPLACE VIEW jv CHANGE_TRACKING = TRUE AS SELECT ja.id, ja.region, jb.secret FROM "
            + "ja JOIN jb ON ja.id = jb.id");
        engine.execute("CREATE OR REPLACE STREAM jsv ON VIEW jv");
        pause();
        engine.execute("SET j1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("INSERT INTO ja VALUES (1, 'EU'), (2, 'US')");
        engine.execute("INSERT INTO jb VALUES (1, 'a'), (2, 'b')");
        pause();
        assertCells(new String[][] {
            {"SELECT id, region, secret FROM jv ORDER BY 1", "1|EU|***"},
            {"SELECT id, region, secret, METADATA$ACTION FROM jsv ORDER BY 1", "1|EU|***|INSERT"},
            {"SELECT id, region, secret FROM jv CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($j1, "
                + "3)) ORDER BY 1",
                "1|EU|***"},
            {"SELECT COUNT(*) FROM jsv", "1"},
        });
    }

    @Test
    public void theViewsOwnRowAccessPolicy() {
        engine.execute("CREATE OR REPLACE TABLE rt (id INT, region STRING, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rrap AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE OR REPLACE VIEW rv CHANGE_TRACKING = TRUE AS SELECT id, region, UPPER(secret) AS s "
            + "FROM rt");
        engine.execute("ALTER VIEW rv ADD ROW ACCESS POLICY rrap ON (region)");
        engine.execute("CREATE OR REPLACE STREAM rsv ON VIEW rv");
        pause();
        engine.execute("SET r1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("INSERT INTO rt VALUES (1, 'EU', 'a'), (2, 'US', 'b')");
        pause();
        assertCells(new String[][] {
            {"SELECT id, s FROM rv ORDER BY 1", "1|A"},
            {"SELECT id, s, METADATA$ACTION FROM rsv ORDER BY 1", "1|A|INSERT"},
            {"SELECT id, s FROM rv CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => TO_TIMESTAMP_LTZ($r1, 3)) ORDER BY "
                + "1",
                "1|A"},
            {"SELECT id, s FROM rv CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'rsv') ORDER BY 1", "1|A"},
            {"SELECT id, s FROM rt ORDER BY 1",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'S'"},
            {"SELECT COUNT(*) FROM rsv", "1"},
        });
    }

    @Test
    public void updatesDeletesAndInitialRows() {
        engine.execute("CREATE OR REPLACE TABLE dt (id INT, region STRING, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY drap AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE OR REPLACE MASKING POLICY dmp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("ALTER TABLE dt ADD ROW ACCESS POLICY drap ON (region)");
        engine.execute("ALTER TABLE dt MODIFY COLUMN secret SET MASKING POLICY dmp");
        engine.execute("CREATE OR REPLACE VIEW dv CHANGE_TRACKING = TRUE AS SELECT id, region, secret, "
            + "LENGTH(secret) AS n FROM dt");
        engine.execute("CREATE OR REPLACE STREAM dsv ON VIEW dv");
        engine.execute("CREATE OR REPLACE STREAM dsa ON VIEW dv APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO dt VALUES (1, 'EU', 'a'), (2, 'US', 'bb'), (3, 'EU', 'ccc')");
        pause();
        engine.execute("CREATE OR REPLACE STREAM dsu ON VIEW dv");
        engine.execute("CREATE OR REPLACE STREAM dsi ON VIEW dv SHOW_INITIAL_ROWS = TRUE");
        engine.execute("UPDATE dt SET secret = 'zzzz' WHERE id IN (1, 2)");
        engine.execute("DELETE FROM dt WHERE id = 3");
        pause();
        assertCells(new String[][] {
            {"SELECT id, secret, n, METADATA$ACTION FROM dsv ORDER BY 1, 4", "1|***|3|INSERT"},
            {"SELECT id, secret, n, METADATA$ACTION FROM dsa ORDER BY 1", "1|***|3|INSERT ;; 3|***|3|INSERT"},
            {"SELECT id, secret, n, METADATA$ACTION FROM dsi ORDER BY 1, 4", "1|***|3|INSERT ;; 3|***|3|INSERT"},
            {"SELECT id, secret, n FROM dv ORDER BY 1", "1|***|3"},
        });
    }
}
