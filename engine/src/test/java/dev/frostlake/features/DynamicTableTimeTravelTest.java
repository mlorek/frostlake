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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A dynamic table read, or streamed, AT or BEFORE a point after its first refresh (live-verified): the read sees the
 * rows of the last refresh before the point, and a stream starts there, every later refresh's changes pending. A
 * refresh statement names the moment it began for AT as for BEFORE, so the changes it made are not part of the
 * point; the statement that created the table cannot be named.
 */
public class DynamicTableTimeTravelTest extends MeasuredCellsTestSupport {

    private static final String READ = "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM %s ORDER BY a";
    private static final String UNNAMEABLE = "REFUSED Statement <QID> cannot be used to specify time for time travel"
        + " query.";

    private String created;
    private String firstRefresh;
    private String secondRefresh;

    @BeforeEach
    public void refreshTwice() {
        final Object current = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = current == null ? "COMPUTE_WH" : current.toString();
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE dsrc (a INT)", "Table DSRC successfully created."},
            {"INSERT INTO dsrc VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE DYNAMIC TABLE dtt TARGET_LAG = '1 day' WAREHOUSE = " + warehouse
                + " AS SELECT a FROM dsrc", "Dynamic table DTT successfully created."},
        });
        created = answer("SELECT LAST_QUERY_ID()");
        answer("INSERT INTO dsrc VALUES (3)");
        answer("ALTER DYNAMIC TABLE dtt REFRESH");
        firstRefresh = answer("SELECT LAST_QUERY_ID()");
        answer("INSERT INTO dsrc VALUES (4)");
        answer("ALTER DYNAMIC TABLE dtt REFRESH");
        secondRefresh = answer("SELECT LAST_QUERY_ID()");
    }

    private static String statement(final String point, final String queryId) {
        return point + " (STATEMENT => '" + queryId + "')";
    }

    @Test
    public void aStreamStartsAtARefreshWithItsChangesPending() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STREAM s_at ON DYNAMIC TABLE dtt " + statement("AT", firstRefresh),
                "Stream S_AT successfully created."},
            {String.format(READ, "s_at"), "3|INSERT|false ;; 4|INSERT|false"},
            {"CREATE OR REPLACE STREAM s_before ON DYNAMIC TABLE dtt " + statement("BEFORE", firstRefresh),
                "Stream S_BEFORE successfully created."},
            {String.format(READ, "s_before"), "3|INSERT|false ;; 4|INSERT|false"},
            {"CREATE OR REPLACE STREAM s_second ON DYNAMIC TABLE dtt " + statement("AT", secondRefresh),
                "Stream S_SECOND successfully created."},
            {String.format(READ, "s_second"), "4|INSERT|false"},
            {"CREATE OR REPLACE STREAM s_copy ON DYNAMIC TABLE dtt AT (STREAM => 's_at')",
                "Stream S_COPY successfully created."},
            {String.format(READ, "s_copy"), "3|INSERT|false ;; 4|INSERT|false"},
            {"CREATE OR REPLACE STREAM s_now ON DYNAMIC TABLE dtt", "Stream S_NOW successfully created."},
            {String.format(READ, "s_now"), ""},
            {"INSERT INTO dsrc VALUES (5)", "1"},
        });
        answer("ALTER DYNAMIC TABLE dtt REFRESH");
        assertCells(new String[][] {
            {String.format(READ, "s_at"), "3|INSERT|false ;; 4|INSERT|false ;; 5|INSERT|false"},
            {String.format(READ, "s_now"), "5|INSERT|false"},
        });
    }

    @Test
    public void aReadAtARefreshSeesTheRowsItReplaced() {
        assertCells(new String[][] {
            {"SELECT a FROM dtt " + statement("AT", firstRefresh) + " ORDER BY a", "1 ;; 2"},
            {"SELECT a FROM dtt " + statement("BEFORE", firstRefresh) + " ORDER BY a", "1 ;; 2"},
            {"SELECT a FROM dtt " + statement("AT", secondRefresh) + " ORDER BY a", "1 ;; 2 ;; 3"},
        });
    }

    @Test
    public void theCreateStatementNamesNoPoint() {
        assertShapedAnswer(UNNAMEABLE, "SELECT a FROM dtt " + statement("AT", created) + " ORDER BY a");
        assertShapedAnswer(UNNAMEABLE, "CREATE OR REPLACE STREAM s_created ON DYNAMIC TABLE dtt "
            + statement("AT", created));
        assertShapedAnswer(UNNAMEABLE, "CREATE OR REPLACE STREAM s_created ON DYNAMIC TABLE dtt "
            + statement("BEFORE", created));
    }
}
