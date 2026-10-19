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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A view read AT or BEFORE a point reads the tables it reads as of the point, through the views it reads too, a
 * statement's point and a join view's included; a point before a table it reads was created is refused naming
 * the table (live-verified).
 */
public class ViewTimeTravelTest extends MeasuredCellsTestSupport {

    /** Keeps the statements around a point apart, so each falls in a moment of its own on either side. */
    private static void pause() {
        try {
            Thread.sleep(isLiveSnowflake() ? 2500L : 60L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void aViewReadAtAPointReadsItsTablesThen() {
        engine.execute("CREATE OR REPLACE TABLE vt_t (a INT, b STRING)");
        engine.execute("CREATE OR REPLACE TABLE vt_u (a INT, c STRING)");
        engine.execute("INSERT INTO vt_t VALUES (1, 'x')");
        engine.execute("INSERT INTO vt_u VALUES (1, 'p'), (2, 'q')");
        engine.execute("CREATE OR REPLACE VIEW vt_v AS SELECT a, b FROM vt_t WHERE a > 0");
        engine.execute("CREATE OR REPLACE VIEW vt_j AS SELECT vt_t.a, b, c FROM vt_t JOIN vt_u ON vt_t.a = vt_u.a");
        engine.execute("CREATE OR REPLACE VIEW vt_n AS SELECT a, b FROM vt_v");
        pause();
        engine.execute("SET t1 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("INSERT INTO vt_t VALUES (2, 'y')");
        engine.execute("SET qi = LAST_QUERY_ID()");
        pause();
        engine.execute("SET t2 = DATE_PART(EPOCH_MILLISECOND, CURRENT_TIMESTAMP())");
        pause();
        engine.execute("UPDATE vt_t SET b = 'x2' WHERE a = 1");
        engine.execute("DELETE FROM vt_u WHERE a = 2");
        pause();
        assertCells(new String[][] {
            {"SELECT a, b FROM vt_v AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t1, 3)) ORDER BY 1", "1|x"},
            {"SELECT a, b FROM vt_v AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t2, 3)) ORDER BY 1", "1|x ;; 2|y"},
            {"SELECT a, b FROM vt_v BEFORE(TIMESTAMP => TO_TIMESTAMP_LTZ($t2, 3)) ORDER BY 1", "1|x ;; 2|y"},
            {"SELECT a, b FROM vt_v AT(STATEMENT => $qi) ORDER BY 1", "1|x ;; 2|y"},
            {"SELECT a, b FROM vt_v BEFORE(STATEMENT => $qi) ORDER BY 1", "1|x"},
            {"SELECT a, b, c FROM vt_j AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t1, 3)) ORDER BY 1", "1|x|p"},
            {"SELECT a, b, c FROM vt_j AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t2, 3)) ORDER BY 1", "1|x|p ;; 2|y|q"},
            {"SELECT a, b FROM vt_n AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t1, 3)) ORDER BY 1", "1|x"},
            {"SELECT v.a, u.c FROM vt_v AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t1, 3)) v JOIN vt_u u ON v.a = u.a ORDER BY "
                + "1",
                "1|p"},
            {"SELECT a FROM vt_v AT(TIMESTAMP => '2020-01-01'::TIMESTAMP_LTZ)",
                "REFUSED Time travel data is not available for table VT_T. The requested time is either beyond the "
                + "allowed time travel period or before the object creation time."},
            {"SELECT a FROM vt_v AT(OFFSET => -100000)",
                "REFUSED Time travel data is not available for table VT_T. The requested time is either beyond the "
                + "allowed time travel period or before the object creation time."},
            {"SELECT COUNT(*) FROM (SELECT * FROM vt_v AT(TIMESTAMP => TO_TIMESTAMP_LTZ($t1, 3)))", "1"},
            {"SELECT a, b FROM vt_v ORDER BY 1", "1|x2 ;; 2|y"},
        });
    }
}
