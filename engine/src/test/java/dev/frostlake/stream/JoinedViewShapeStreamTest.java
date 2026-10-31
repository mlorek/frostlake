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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A view that joins NATURAL, or joins a FROM-clause subquery, takes a stream, and its change tracking turns on with
 * it (live-verified); the stream reads the view's changes as a join view's stream does, the view's rows before and
 * after compared.
 */
public class JoinedViewShapeStreamTest extends MeasuredCellsTestSupport {

    @Test
    public void aNaturalJoinAndAJoinedSubqueryTakeAStream() {
        engine.execute("CREATE OR REPLACE TABLE ta606 (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW vn606 AS SELECT ta606.id FROM ta606 NATURAL JOIN ta606 b");
        engine.execute("CREATE OR REPLACE VIEW vd606 AS SELECT x.id FROM (SELECT id FROM ta606) x JOIN ta606 "
            + "ON x.id = ta606.id");
        assertAnswer("Stream SN606 successfully created.", "CREATE OR REPLACE STREAM sn606 ON VIEW vn606");
        assertAnswer("Stream SD606 successfully created.", "CREATE OR REPLACE STREAM sd606 ON VIEW vd606");
        answer("SHOW VIEWS LIKE 'V_606'");
        assertAnswer("VD606|ON ;; VN606|ON",
            "SELECT \"name\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    @Test
    public void theStreamsReadTheViewsChanges() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the rows these streams read are not measured");
        engine.execute("CREATE OR REPLACE TABLE tl606 (id INT, v INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE tr606 (id INT, w INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW vn606 AS SELECT tl606.id, v, w FROM tl606 NATURAL JOIN tr606");
        engine.execute("CREATE OR REPLACE VIEW vd606 AS SELECT x.id, w FROM (SELECT id FROM tl606 WHERE id > 1) x "
            + "JOIN tr606 ON x.id = tr606.id");
        engine.execute("CREATE OR REPLACE STREAM sn606 ON VIEW vn606");
        engine.execute("CREATE OR REPLACE STREAM sd606 ON VIEW vd606");
        engine.execute("INSERT INTO tl606 VALUES (1, 10), (2, 20)");
        engine.execute("INSERT INTO tr606 VALUES (1, 100), (2, 200), (3, 300)");
        assertAnswer("1|10|100|INSERT|false ;; 2|20|200|INSERT|false",
            "SELECT id, v, w, METADATA$ACTION, METADATA$ISUPDATE FROM sn606 ORDER BY 1");
        assertAnswer("2|200|INSERT", "SELECT id, w, METADATA$ACTION FROM sd606 ORDER BY 1");
        engine.execute("CREATE OR REPLACE TABLE sink606 (id INT)");
        engine.execute("INSERT INTO sink606 SELECT id FROM sn606");
        engine.execute("UPDATE tr606 SET w = 101 WHERE id = 1");
        assertAnswer("1|100|DELETE|true ;; 1|101|INSERT|true",
            "SELECT id, w, METADATA$ACTION, METADATA$ISUPDATE FROM sn606 ORDER BY 3");
        assertAnswer("2|200|INSERT", "SELECT id, w, METADATA$ACTION FROM sd606 ORDER BY 1");
    }
}
