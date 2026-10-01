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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three stage surfaces: DIRECTORY() reading a malformed name in its own frame, a partitioned unload naming its
 * files after itself, and GET choosing between same-named files in the order it walks them.
 */
public class StageNamesAndCollisionsTest extends BaseDatabaseTest {

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            for (int c = 0; c < rs.getColumnCount(); c++) {
                text.append(c == 0 ? "" : "|").append(rs.getValue(c));
            }
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                rows(sql);
            }
        }).getMessage().replace("\n", " | ");
    }

    /** A name ending in a dot stops at the reference's own end; a doubled dot is named where it stands. */
    @Test
    public void directoryReadsItsNameInItsOwnFrame() {
        engine.execute("CREATE OR REPLACE STAGE st DIRECTORY = (ENABLE = TRUE)");
        assertTrue(refusal("SELECT * FROM DIRECTORY(@st.)")
            .endsWith("syntax error line 1 at position 3 unexpected '<EOF>'."));
        assertTrue(refusal("SELECT * FROM DIRECTORY(@st..)")
            .endsWith("syntax error line 1 at position 3 unexpected '.'."));
    }

    /** DETAILED_OUTPUT names each partition ahead of its file, and a file is named after its unload. */
    @Test
    public void aPartitionedUnloadNamesItsPartitionsAndFiles() {
        engine.execute("CREATE OR REPLACE STAGE sp");
        final ResultSet rs = engine.executeQuery("COPY INTO @sp/p FROM (SELECT 1 AS v, 'x' AS k) PARTITION BY k "
            + "FILE_FORMAT = (TYPE = CSV) DETAILED_OUTPUT = TRUE");
        assertEquals("PARTITION_NAME", rs.getColumns().get(0).getName());
        rs.next();
        assertEquals("x", String.valueOf(rs.getValue(0)));
        assertTrue(String.valueOf(rs.getValue(1))
            .matches("x/data_[0-9a-f-]{36}_[0-9]+_[0-9]+_[0-9]+[.]csv[.]gz"), String.valueOf(rs.getValue(1)));
    }

    /** A rerun adds files beside the last run's instead of overwriting them. */
    @Test
    public void aRerunAddsFiles() {
        engine.execute("CREATE OR REPLACE STAGE sr");
        engine.execute("COPY INTO @sr/r FROM (SELECT 1 AS v, 'x' AS k) PARTITION BY k FILE_FORMAT = (TYPE = CSV)");
        engine.execute("COPY INTO @sr/r FROM (SELECT 1 AS v, 'x' AS k) PARTITION BY k FILE_FORMAT = (TYPE = CSV)");
        engine.execute("LIST @sr/r");
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    /** The NULL partition is named \\N, as a directory and in DETAILED_OUTPUT. */
    @Test
    public void theNullPartitionIsBackslashN() {
        engine.execute("CREATE OR REPLACE STAGE sn");
        final ResultSet rs = engine.executeQuery("COPY INTO @sn/n FROM (SELECT 1 AS v, NULL::VARCHAR AS k) "
            + "PARTITION BY k FILE_FORMAT = (TYPE = CSV) DETAILED_OUTPUT = TRUE");
        rs.next();
        assertEquals("\\N", String.valueOf(rs.getValue(0)));
        assertTrue(String.valueOf(rs.getValue(1)).startsWith("\\N/data_"));
    }

    /**
     * GET over the files at these paths, uploaded in this order to a fresh stage: each row's file, status and
     * message, in the order GET answers them.
     */
    private String get(final String stage, final String... paths) {
        engine.execute("CREATE OR REPLACE STAGE " + stage);
        for (final String path : paths) {
            engine.execute("COPY INTO @" + stage + "/" + path + " FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) SINGLE = TRUE OVERWRITE = TRUE");
        }
        final ResultSet rs = engine.executeQuery("GET @" + stage + " 'file:///tmp/fl-get-order-test/" + stage + "/'");
        final StringBuilder walk = new StringBuilder();
        while (rs.next()) {
            walk.append(rs.getValue(0)).append(':').append(rs.getValue(2)).append(':')
                .append(rs.getValue(4)).append(';');
        }
        return walk.toString();
    }

    /**
     * Same-named files are walked in the order the client's hash set and map lay the stage-relative paths out, the
     * files that collide with nothing included, whatever order they arrived in: each file a later one displaces is a
     * COLLISION naming its displacer, and the last one walked is downloaded.
     */
    @Test
    public void getKeepsTheLastFileItWalks() {
        assertEquals("a/g:COLLISION:a/g has same name as c/g;c/g:COLLISION:c/g has same name as b/g;"
            + "b/g:COLLISION:b/g has same name as d/g;d/g:DOWNLOADED:;", get("go", "d/g", "a/g", "c/g", "b/g"));
        final String abc = "a/g:COLLISION:a/g has same name as c/g;c/g:COLLISION:c/g has same name as b/g;"
            + "b/g:DOWNLOADED:;f1:DOWNLOADED:;f1.csv:DOWNLOADED:;";
        assertEquals(abc, get("go1", "c/g", "b/g", "a/g", "f1", "f1.csv"));
        assertEquals(abc, get("go6", "a/g", "b/g", "c/g", "f1", "f1.csv"));
        assertEquals("m/g:COLLISION:m/g has same name as a/g;a/g:COLLISION:a/g has same name as z/g;"
            + "z/g:DOWNLOADED:;", get("go2", "z/g", "m/g", "a/g"));
        assertEquals("f1:DOWNLOADED:;x3/g:COLLISION:x3/g has same name as x2/g;"
            + "x2/g:COLLISION:x2/g has same name as x1/g;x1/g:DOWNLOADED:;", get("go3", "x3/g", "f1", "x1/g", "x2/g"));
        assertEquals("a/g:COLLISION:a/g has same name as zz/g;zz/g:COLLISION:zz/g has same name as p/q/g;"
            + "p/q/g:DOWNLOADED:;", get("go5", "zz/g", "a/g", "p/q/g"));
    }
}
