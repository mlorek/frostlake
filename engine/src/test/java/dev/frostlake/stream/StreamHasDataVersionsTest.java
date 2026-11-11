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
 * SYSTEM$STREAM_HAS_DATA counts a version a stream's table took past its offset by what it wrote (live-verified): an
 * INSERT and a MERGE always, one of no row too; any other change where it left the table rows, or, for a stream that
 * reads more than inserts, where it removed some. So a DELETE of every row and a TRUNCATE count for a standard stream
 * alone, an UPDATE or a DELETE that changed no row, a TRUNCATE of an empty table and a DDL change for none, and a
 * change a view filters out counts though the stream lists nothing.
 */
public class StreamHasDataVersionsTest extends MeasuredCellsTestSupport {

    @Test
    public void changesAStreamDoesNotList() {
        engine.execute("CREATE OR REPLACE TABLE h928 (a INT)");
        engine.execute("INSERT INTO h928 VALUES (1), (2)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE STREAM hs928 ON TABLE h928 APPEND_ONLY = TRUE", "Stream HS928 successfully created."},
            {"DELETE FROM h928 WHERE a = 1", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('hs928')", "true"},
            {"SELECT COUNT(*) FROM hs928", "0"},
            {"CREATE OR REPLACE STREAM hs928 ON TABLE h928 APPEND_ONLY = TRUE", "Stream HS928 successfully created."},
            {"UPDATE h928 SET a = 5 WHERE a = 2", "1|0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('hs928')", "true"},
            {"SELECT a, METADATA$ACTION FROM hs928 ORDER BY 1", ""},
            {"CREATE OR REPLACE STREAM hs928 ON TABLE h928 APPEND_ONLY = TRUE", "Stream HS928 successfully created."},
            {"TRUNCATE TABLE h928", "Statement executed successfully."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('hs928')", "false"},
            {"INSERT INTO h928 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE STREAM ns928 ON TABLE h928", "Stream NS928 successfully created."},
            {"UPDATE h928 SET a = 1 WHERE FALSE", "0|0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ns928')", "false"},
            {"DELETE FROM h928 WHERE a > 100", "0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ns928')", "false"},
            {"INSERT INTO h928 SELECT 9 WHERE FALSE", "0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ns928')", "true"},
            {"UPDATE h928 SET a = a WHERE a = 1", "1|0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ns928')", "true"},
            {"SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM ns928 ORDER BY 1, 2", ""},
            {"CREATE OR REPLACE STREAM ns928 ON TABLE h928", "Stream NS928 successfully created."},
            {"DELETE FROM h928 WHERE a = 2", "1"},
            {"INSERT INTO h928 VALUES (2)", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ns928')", "true"},
            {"SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM ns928 ORDER BY 1, 2",
                "2|DELETE|false ;; 2|INSERT|false"},
            {"CREATE OR REPLACE VIEW hv928 AS SELECT a FROM h928 WHERE a > 100", "View HV928 successfully created."},
            {"CREATE OR REPLACE STREAM vs928 ON VIEW hv928", "Stream VS928 successfully created."},
            {"INSERT INTO h928 VALUES (3)", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('vs928')", "true"},
            {"SELECT COUNT(*) FROM vs928", "0"},
            {"CREATE OR REPLACE STREAM ds928 ON TABLE h928", "Stream DS928 successfully created."},
            {"DELETE FROM h928 WHERE a = 3", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ds928')", "true"},
        });
    }

    @Test
    public void whatEachVersionWrote() {
        engine.execute("CREATE OR REPLACE TABLE k929 (a INT)");
        assertCells(new String[][] {
            {"INSERT INTO k929 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"DELETE FROM k929", "2"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "true"},
            {"INSERT INTO k929 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"TRUNCATE TABLE k929", "Statement executed successfully."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "true"},
            {"SELECT COUNT(*) FROM ks929", "2"},
            {"INSERT INTO k929 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE STREAM ka929 ON TABLE k929 APPEND_ONLY = TRUE", "Stream KA929 successfully created."},
            {"DELETE FROM k929", "2"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ka929')", "false"},
            {"INSERT INTO k929 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"ALTER TABLE k929 ADD COLUMN b INT", "Statement executed successfully."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "false"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"ALTER TABLE k929 SET COMMENT = 'x'", "Statement executed successfully."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "false"},
            {"CREATE OR REPLACE STREAM ka929 ON TABLE k929 APPEND_ONLY = TRUE", "Stream KA929 successfully created."},
            {"INSERT INTO k929 (a) SELECT 9 WHERE FALSE", "0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ka929')", "true"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"MERGE INTO k929 USING (SELECT 99 a) s ON k929.a = s.a WHEN MATCHED THEN UPDATE SET a = s.a", "0"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "true"},
            {"TRUNCATE TABLE k929", "Statement executed successfully."},
            {"INSERT INTO k929 (a) VALUES (1)", "1"},
            {"INSERT INTO k929 (a) VALUES (2)", "1"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"DELETE FROM k929 WHERE a = 1", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "true"},
            {"SELECT a, METADATA$ACTION FROM ks929", "1|DELETE"},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"TRUNCATE TABLE k929", "Statement executed successfully."},
            {"TRUNCATE TABLE k929", "Statement executed successfully."},
            {"CREATE OR REPLACE STREAM ks929 ON TABLE k929", "Stream KS929 successfully created."},
            {"TRUNCATE TABLE k929", "Statement executed successfully."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ks929')", "false"},
        });
    }
}
