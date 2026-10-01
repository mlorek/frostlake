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

package dev.frostlake.types;

/**
 * The declared types of the columns a STATEMENT's own result set carries — COPY's load and unload
 * summaries, and the file-transfer commands — which are not one width either, and are not the
 * function widths {@link IntegerResultWidths} collects.
 *
 * <pre>
 *   NUMBER(19,0)         a DML statement's counts: rows inserted, updated, deleted, multi-joined
 *   NUMBER(38,0)         the load counts: rows_parsed rows_loaded error_limit errors_seen
 *                        first_error_line first_error_character
 *   NUMBER(31,0)         the unload summary: rows_unloaded input_bytes output_bytes
 *   NUMBER(18,0)         DETAILED_OUTPUT's per-file FILE_SIZE and ROW_COUNT
 *   VARCHAR(134217728)   DETAILED_OUTPUT's FILE_NAME
 *   VARCHAR(20480)       the file-transfer texts: file status encryption message
 *   DECIMAL(10,0)        the file-transfer size
 * </pre>
 *
 * <p>The last two are the ones that look wrong and are not: a file transfer is answered by the client
 * rather than the server, so its result carries widths no column of a table ever has — ten digits for
 * a size where every other count is 18 or 38, and 20480 for a text.
 */
public final class StatementResultWidths {

    /** A count in an INSERT, UPDATE, DELETE or MERGE result — the width a signed 64-bit count needs. */
    public static final NumericType DML_COUNT = new NumericType("NUMBER", 19, 0);

    /** A count in COPY INTO &lt;table&gt;'s per-file load summary. */
    public static final NumericType LOAD_COUNT = new NumericType("NUMBER", 38, 0);

    /** A count or a byte total in COPY INTO &lt;location&gt;'s unload summary. */
    public static final NumericType UNLOAD_COUNT = new NumericType("NUMBER", 31, 0);

    /** A per-file size or row count in the unload's DETAILED_OUTPUT rows. */
    public static final NumericType UNLOAD_FILE_COUNT = new NumericType("NUMBER", 18, 0);

    /** A file name in the unload's DETAILED_OUTPUT rows. */
    public static final StringType UNLOAD_FILE_NAME = new StringType("VARCHAR", 134217728);

    /** A text column of a GET or PUT result. */
    public static final StringType TRANSFER_TEXT = new StringType("VARCHAR", 20480);

    /** A byte size in a GET or PUT result. */
    public static final NumericType TRANSFER_SIZE = new NumericType("NUMBER", 10, 0);

    private StatementResultWidths() {
    }
}
