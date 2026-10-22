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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A write's value that fails is a DML failure on its column, with one exception: an UPDATE's value whose query
 * reads a stream fails bare, in a transaction or not; a MERGE's update is bare too (all live-verified).
 */
public class StreamSubqueryDivisionTest extends MeasuredCellsTestSupport {

    @Test
    public void envelopes() {
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE sink (id INT)");
        engine.execute("INSERT INTO sink VALUES (7)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dz) WHERE id = 7");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dz)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(dz.id) FROM dz WHERE dz.id < sink.id)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "INSERT INTO sink SELECT 1/(SELECT MIN(id) FROM dz)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "INSERT INTO sink SELECT 1/id FROM dz");
        assertAnswer("REFUSED Division by zero",
            "MERGE INTO sink USING (SELECT 7 k) s ON sink.id = s.k WHEN MATCHED THEN UPDATE SET id = 1/(SELECT "
                + "MIN(id) FROM dz)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "MERGE INTO sink USING (SELECT 8 k) s ON sink.id = s.k WHEN NOT MATCHED THEN INSERT (id) VALUES "
                + "(1/(SELECT MIN(id) FROM dz))");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Numeric value 'a0' is not "
                + "recognized",
            "UPDATE sink SET id = ('a' || (SELECT MIN(id) FROM dz))::INT");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/0");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT 0)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = (SELECT 1/MIN(id) FROM dz)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dz) + 0 * id");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause",
            "INSERT INTO sink VALUES (1/0)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = (SELECT MIN(id) FROM dz)::BOOLEAN::INT / (SELECT MIN(id) FROM dz)");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dz WHERE id < 7) WHERE id = 7");
        assertAnswer("0|0", "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dz) WHERE id = 8");
        assertAnswer("7", "SELECT id FROM sink ORDER BY id");
    }

    @Test
    public void overAStream() {
        engine.execute("CREATE OR REPLACE TABLE dz (id INT)");
        engine.execute("INSERT INTO dz VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE dzs (id INT)");
        engine.execute("CREATE OR REPLACE STREAM sts ON TABLE dzs");
        engine.execute("INSERT INTO dzs VALUES (0), (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE sink (id INT)");
        engine.execute("INSERT INTO sink VALUES (7)");
        assertAnswer("REFUSED Division by zero", "UPDATE sink SET id = 1/(SELECT MIN(id) FROM sts) WHERE id = 7");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dz) WHERE id = 7");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("REFUSED Division by zero", "UPDATE sink SET id = 1/(SELECT MIN(id) FROM sts) WHERE id = 7");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "INSERT INTO sink SELECT 1/id FROM sts");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dzs) WHERE id = 7");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("REFUSED DML operation to table SINK failed on column ID with error: Division by zero",
            "UPDATE sink SET id = 1/(SELECT MIN(id) FROM dzs) WHERE id = 7");
        assertAnswer("7", "SELECT id FROM sink ORDER BY id");
    }
}
