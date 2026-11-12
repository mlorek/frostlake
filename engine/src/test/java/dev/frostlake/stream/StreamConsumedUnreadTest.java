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
 * A DML statement that names a table stream consumes it when it commits, whether or not the stream's rows are ever
 * read (documented: a stream's offset advances when it is used in a DML statement that commits): a DELETE over an
 * empty target, or an UPDATE whose earlier predicate is FALSE for every row, never evaluates its subquery over the
 * stream, yet moves the stream on as a stream on a view and a USING source already did. Unmeasured on live.
 */
public class StreamConsumedUnreadTest extends MeasuredCellsTestSupport {

    private void setUpStream() {
        engine.execute("CREATE OR REPLACE TABLE scu_src (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE STREAM scu_s ON TABLE scu_src");
        engine.execute("CREATE OR REPLACE TABLE scu_tgt (id INT)");
        engine.execute("INSERT INTO scu_src VALUES (1), (2)");
    }

    @Test
    public void aSubqueryNeverEvaluatedStillConsumesTheStream() {
        setUpStream();
        engine.execute("DELETE FROM scu_tgt WHERE id IN (SELECT id FROM scu_s)");
        assertAnswer("0", "SELECT COUNT(*) FROM scu_s");
        engine.execute("INSERT INTO scu_src VALUES (3)");
        engine.execute("INSERT INTO scu_tgt VALUES (9)");
        engine.execute("UPDATE scu_tgt SET id = id + 1 WHERE 1 = 0 AND id IN (SELECT id FROM scu_s)");
        assertAnswer("0", "SELECT COUNT(*) FROM scu_s");
    }

    @Test
    public void aRolledBackStatementLeavesTheStream() {
        setUpStream();
        engine.execute("BEGIN");
        engine.execute("DELETE FROM scu_tgt WHERE id IN (SELECT id FROM scu_s)");
        engine.execute("ROLLBACK");
        assertAnswer("2", "SELECT COUNT(*) FROM scu_s");
        engine.execute("BEGIN");
        engine.execute("DELETE FROM scu_tgt WHERE id IN (SELECT id FROM scu_s)");
        engine.execute("COMMIT");
        assertAnswer("0", "SELECT COUNT(*) FROM scu_s");
    }
}
