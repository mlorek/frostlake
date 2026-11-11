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
 * A stream read inside a transaction (live-verified): a stream on a view with DISTINCT or GROUP BY is refused, one on
 * a derived relation or a NATURAL JOIN is created; the transaction's own INSERT is neither listed by the stream nor
 * counted by SYSTEM$STREAM_HAS_DATA until it commits; and a CTAS issued in the transaction commits it, reading and
 * consuming the stream only up to where the transaction began, so the transaction's own change stays listed.
 */
public class StreamReadsInTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void readsAndACreateTableAsInsideATransaction() {
        engine.execute("CREATE OR REPLACE TABLE st931 (a INT)");
        engine.execute("CREATE OR REPLACE STREAM ss931 ON TABLE st931");
        engine.execute("CREATE OR REPLACE TABLE su931 (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW sd931 AS SELECT DISTINCT a FROM st931", "View SD931 successfully created."},
            {"CREATE OR REPLACE STREAM sds931 ON VIEW sd931",
                "REFUSED SQL compilation error: line 1 at position 32: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.SD931')."},
            {"CREATE OR REPLACE VIEW sg931 AS SELECT a, COUNT(*) c FROM st931 GROUP BY a",
                "View SG931 successfully created."},
            {"CREATE OR REPLACE STREAM sgs931 ON VIEW sg931",
                "REFUSED SQL compilation error: line 1 at position 64: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.SG931')."},
            {"CREATE OR REPLACE VIEW sr931 AS SELECT a FROM (SELECT a FROM st931 WHERE a > 0)",
                "View SR931 successfully created."},
            {"CREATE OR REPLACE STREAM srs931 ON VIEW sr931", "Stream SRS931 successfully created."},
            {"CREATE OR REPLACE VIEW sn931 AS SELECT * FROM st931 NATURAL JOIN su931",
                "View SN931 successfully created."},
            {"CREATE OR REPLACE STREAM sns931 ON VIEW sn931", "Stream SNS931 successfully created."},
            {"BEGIN", "Statement executed successfully."},
            {"INSERT INTO st931 VALUES (1)", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ss931')", "false"},
            {"SELECT a, METADATA$ACTION FROM ss931", ""},
            {"SELECT a, METADATA$ACTION FROM srs931", ""},
            {"COMMIT", "Statement executed successfully."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ss931')", "true"},
            {"SELECT a, METADATA$ACTION FROM srs931", "1|INSERT"},
            {"BEGIN", "Statement executed successfully."},
            {"INSERT INTO st931 VALUES (2)", "1"},
            {"CREATE OR REPLACE TABLE ct931 AS SELECT a FROM ss931", "Table CT931 successfully created."},
            {"SELECT a FROM ct931 ORDER BY 1", "1"},
            {"SELECT COUNT(*) FROM ss931", "1"},
            {"SELECT CURRENT_TRANSACTION() IS NULL", "true"},
            {"ROLLBACK", "Statement executed successfully."},
        });
    }
}
