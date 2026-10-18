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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * CHANGES over a view that joins its tables reads every point a table's does — AT and BEFORE a time or a
 * statement, another stream's offset, END, APPEND_ONLY — as the difference of the view's rows at the start and
 * at the end (live-verified). A joined row is known by the table rows it joins, so an UPDATE of either reads as a
 * DELETE and an INSERT under one METADATA$ROW_ID, METADATA$ISUPDATE TRUE, and a stream on the view reports the
 * same id window after window.
 */
public class JoinViewChangesTest extends MeasuredCellsTestSupport {

    /** Keeps the statements around a point apart, so each falls in a moment of its own on either side. */
    private static void pause() {
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void changesOverAJoinViewReadEveryPoint() {
        engine.execute("CREATE OR REPLACE TABLE ja (id INT, x STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE jb (id INT, y STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO ja VALUES (1, 'a'), (2, 'b')");
        engine.execute("INSERT INTO jb VALUES (1, 'p'), (2, 'q')");
        engine.execute("CREATE OR REPLACE VIEW jv CHANGE_TRACKING = TRUE AS SELECT ja.id, ja.x, jb.y FROM ja JOIN jb "
            + "ON ja.id = jb.id");
        engine.execute("CREATE OR REPLACE VIEW js CHANGE_TRACKING = TRUE AS SELECT id, x FROM ja");
        engine.execute("CREATE OR REPLACE STREAM jst ON TABLE ja");
        pause();
        engine.execute("SET p1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("UPDATE ja SET x = 'a2' WHERE id = 1");
        engine.execute("SET qu = LAST_QUERY_ID()");
        pause();
        engine.execute("SET p2 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("INSERT INTO jb VALUES (3, 'r')");
        engine.execute("INSERT INTO ja VALUES (3, 'c')");
        pause();
        engine.execute("SET p3 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("DELETE FROM jb WHERE id = 2");
        pause();
        assertCells(new String[][] {
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) ORDER BY 1, 4",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true ;; 2|b|q|DELETE|false ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p2, 3)) ORDER BY 1, 4",
                "2|b|q|DELETE|false ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p3, 3)) ORDER BY 1, 4",
                "2|b|q|DELETE|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) END(TIMESTAMP => TO_TIMESTAMP_LTZ($p2, 3)) ORDER BY 1, 4",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) END(TIMESTAMP => TO_TIMESTAMP_LTZ($p3, 3)) ORDER BY 1, 4",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p2, 3)) END(TIMESTAMP => TO_TIMESTAMP_LTZ($p3, 3)) ORDER BY 1, 4",
                "3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "BEFORE(TIMESTAMP => TO_TIMESTAMP_LTZ($p2, 3)) ORDER BY 1, 4",
                "2|b|q|DELETE|false ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => APPEND_ONLY) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) ORDER BY 1, 4",
                "3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => APPEND_ONLY) "
                + "AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3)) END(TIMESTAMP => TO_TIMESTAMP_LTZ($p3, 3)) ORDER BY 1, 4",
                "3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "AT(STATEMENT => $qu) ORDER BY 1, 4",
                "2|b|q|DELETE|false ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) "
                + "BEFORE(STATEMENT => $qu) ORDER BY 1, 4",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true ;; 2|b|q|DELETE|false ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM jv CHANGES(INFORMATION => DEFAULT) AT(STREAM "
                + "=> 'jst') ORDER BY 1, 4",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true ;; 2|b|q|DELETE|false ;; 3|c|r|INSERT|false"},
            {"SELECT id, x, METADATA$ACTION, METADATA$ISUPDATE FROM js CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP "
                + "=> TO_TIMESTAMP_LTZ($p1, 3)) ORDER BY 1, 3",
                "1|a|DELETE|true ;; 1|a2|INSERT|true ;; 3|c|INSERT|false"},
            {"SELECT id, x, METADATA$ACTION, METADATA$ISUPDATE FROM js CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP "
                + "=> TO_TIMESTAMP_LTZ($p1, 3)) END(TIMESTAMP => TO_TIMESTAMP_LTZ($p2, 3)) ORDER BY 1, 3",
                "1|a|DELETE|true ;; 1|a2|INSERT|true"},
            {"SELECT id, x, METADATA$ACTION FROM js CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => "
                + "TO_TIMESTAMP_LTZ($p2, 3)) ORDER BY 1",
                "3|c|INSERT"},
            {"SELECT COUNT(*) FROM jv AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p1, 3))", "2"},
            {"SELECT id, x, y FROM jv AT(TIMESTAMP => TO_TIMESTAMP_LTZ($p2, 3)) ORDER BY 1", "1|a2|p ;; 2|b|q"},
        });
    }

    @Test
    public void aJoinedRowKeepsItsIdAcrossUpdates() {
        engine.execute("CREATE OR REPLACE TABLE rt (a INT, b STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO rt VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE rc CLONE rt");
        engine.execute("CREATE OR REPLACE STREAM srt ON TABLE rt SHOW_INITIAL_ROWS = TRUE");
        engine.execute("CREATE OR REPLACE STREAM src ON TABLE rc SHOW_INITIAL_ROWS = TRUE");
        engine.execute("CREATE OR REPLACE TABLE rk (a INT, k STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO rk VALUES (1, 'k1'), (2, 'k2')");
        engine.execute("CREATE OR REPLACE VIEW rjv AS SELECT rt.a, rt.b, rk.k FROM rt JOIN rk ON rt.a = rk.a");
        engine.execute("CREATE OR REPLACE STREAM sjv ON VIEW rjv");
        engine.execute("CREATE OR REPLACE TABLE w1 (rid STRING, a INT, act STRING)");
        engine.execute("CREATE OR REPLACE TABLE w2 (rid STRING, a INT, act STRING)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM srt s JOIN src c ON s.METADATA$ROW_ID = c.METADATA$ROW_ID", "2"},
            {"SELECT COUNT(*) FROM srt", "2"},
            {"SELECT COUNT(*) FROM src", "2"},
            {"SELECT MIN(LENGTH(METADATA$ROW_ID)), MAX(LENGTH(METADATA$ROW_ID)) FROM srt", "40|40"},
            {"UPDATE rt SET b = 'x2' WHERE a = 1", "1|0"},
            {"INSERT INTO w1 SELECT METADATA$ROW_ID, a, METADATA$ACTION FROM sjv", "2"},
            {"SELECT a, act FROM w1 ORDER BY 1, 2", "1|DELETE ;; 1|INSERT"},
            {"UPDATE rt SET b = 'x3' WHERE a = 1", "1|0"},
            {"INSERT INTO w2 SELECT METADATA$ROW_ID, a, METADATA$ACTION FROM sjv", "2"},
            {"SELECT a, act FROM w2 ORDER BY 1, 2", "1|DELETE ;; 1|INSERT"},
            {"SELECT COUNT(*) FROM w1 JOIN w2 ON w1.rid = w2.rid WHERE w1.act = 'INSERT' AND w2.act = 'INSERT'", "1"},
            {"SELECT COUNT(*) FROM w1 JOIN w2 ON w1.rid = w2.rid", "4"},
            {"SELECT MIN(LENGTH(rid)), MAX(LENGTH(rid)) FROM w1", "40|40"},
            {"SELECT COUNT(*) FROM w1 JOIN srt ON w1.rid = srt.METADATA$ROW_ID", "0"},
            {"UPDATE rk SET k = 'k1b' WHERE a = 1", "1|0"},
            {"CREATE OR REPLACE TABLE w3 AS SELECT METADATA$ROW_ID AS rid, a, METADATA$ACTION AS act FROM sjv",
                "Table W3 successfully created."},
            {"SELECT a, act FROM w3 ORDER BY 1, 2", "1|DELETE ;; 1|INSERT"},
            {"SELECT COUNT(*) FROM w1 JOIN w3 ON w1.rid = w3.rid WHERE w1.act = 'INSERT' AND w3.act = 'INSERT'", "1"},
        });
    }
}
