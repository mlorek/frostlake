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
 * SHOW COLUMNS IN TABLE of a stream lists its source's columns under the source's name, then the three
 * change-tracking columns, each with its comment, for a stream on a table, on a view and an append-only one
 * (live-verified). The schema's listing holds no stream.
 */
public class ShowColumnsOnStreamTest extends MeasuredCellsTestSupport {

    private static final String ID = "{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true}";
    private static final String S =
        "{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false}";

    private static String row(final String relation, final String column, final String type, final boolean nullable,
                              final String comment) {
        return relation + "|TEST_SCHEMA|" + column + "|" + type + "|" + (nullable ? "true" : "NOT_NULL") + "||COLUMN||"
            + comment + "|TEST_DB||null|null";
    }

    /** The three change-tracking rows, in the order SHOW COLUMNS lists them. */
    private static String tracking(final String relation) {
        return row(relation, "METADATA$ISUPDATE", "{\"type\":\"BOOLEAN\",\"nullable\":false}", false,
            "whether this row was the result of an update")
            + " ;; " + row(relation, "METADATA$ACTION",
                "{\"type\":\"TEXT\",\"length\":16,\"byteLength\":64,\"nullable\":false,\"fixed\":false}", false,
                "the action that resulted in this row")
            + " ;; " + row(relation, "METADATA$ROW_ID",
                "{\"type\":\"TEXT\",\"length\":40,\"byteLength\":160,\"nullable\":false,\"fixed\":false}", false,
                "a unique identifier that can be used to track the lineage of this row");
    }

    private void streams() {
        answer("CREATE OR REPLACE TABLE mt2 (id INTEGER, s STRING)");
        answer("CREATE OR REPLACE STREAM mst ON TABLE mt2");
        answer("CREATE OR REPLACE VIEW mv AS SELECT id FROM mt2");
        answer("CREATE OR REPLACE STREAM vst ON VIEW mv");
        answer("CREATE OR REPLACE STREAM aost ON TABLE mt2 APPEND_ONLY = TRUE");
    }

    @Test
    public void aStreamListsItsSourcesColumnsAndItsChangeTrackingColumns() {
        streams();
        final String onTable = row("MT2", "ID", ID, true, "") + " ;; " + row("MT2", "S", S, true, "") + " ;; "
            + tracking("MT2");
        assertCells(new String[][] {
            {"SHOW COLUMNS IN TABLE mst", onTable},
            {"SHOW COLUMNS IN mst", onTable},
            {"SHOW COLUMNS IN TABLE aost", onTable},
            {"SHOW COLUMNS IN TABLE vst", row("MV", "ID", ID, true, "") + " ;; " + tracking("MV")},
            {"SHOW COLUMNS LIKE 'METADATA%' IN TABLE mst", tracking("MT2")},
        });
    }

    @Test
    public void theOtherSpellingsAnswerAsBefore() {
        streams();
        assertCells(new String[][] {
            {"SHOW COLUMNS IN VIEW mst",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for MST"},
            {"SHOW COLUMNS LIKE 'METADATA%' IN SCHEMA", ""},
            {"SHOW COLUMNS IN STREAM mst", "REFUSED SQL compilation error: | syntax error line 1 at position 23 "
                + "unexpected 'mst'."},
        });
    }
}
