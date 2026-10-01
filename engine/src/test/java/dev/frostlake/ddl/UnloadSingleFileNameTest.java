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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code SINGLE = TRUE} onto a DIRECTORY writes one file named {@code data} — with no extension at
 * all, neither the format's nor the compression's. Frostlake gave it a multi-file unload's name,
 * {@code data_0_0_0.csv}, so a script reading its own output back by name found nothing.
 *
 * <p>The three cells that decide it are CSV, JSON and GZIP: all land as {@code data}.
 */
public class UnloadSingleFileNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE st");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
    }

    /** The names a listing answers, joined. */
    private String listed(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue("name")));
        }
        return out.toString();
    }

    /** One file called data, whatever the format and whatever the compression. */
    @Test
    public void aSingleUnloadIntoADirectoryIsCalledData() {
        engine.execute("COPY INTO @st/csvnone/ FROM (SELECT 1 AS x)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertEquals("st/csvnone/data", listed("LIST @st/csvnone"));

        engine.execute("COPY INTO @st/csvgzip/ FROM (SELECT 1 AS x)"
            + " FILE_FORMAT = (TYPE = CSV) SINGLE = TRUE");
        assertEquals("st/csvgzip/data", listed("LIST @st/csvgzip"),
            "GZIP adds no .gz either");

        engine.execute("COPY INTO @st/jsonnone/ FROM (SELECT OBJECT_CONSTRUCT('a', 1) AS x)"
            + " FILE_FORMAT = (TYPE = JSON COMPRESSION = NONE) SINGLE = TRUE");
        assertEquals("st/jsonnone/data", listed("LIST @st/jsonnone"));
    }

    /** A table stage names it the same way. */
    @Test
    public void aTableStageNamesItTheSame() {
        engine.execute("COPY INTO @%t/tsub/ FROM (SELECT 2 AS x)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertEquals("tsub/data", listed("LIST @%t/tsub"));
    }

    /** The shapes that were already right stay right. */
    @Test
    public void theOtherShapesAreUnchanged() {
        engine.execute("COPY INTO @st/multi/ FROM (SELECT 1 AS x)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)");
        assertEquals("st/multi/data_0_0_0.csv", listed("LIST @st/multi"),
            "a multi-file unload keeps the numbered name and the extension");

        engine.execute("COPY INTO @st/named/f.csv FROM (SELECT 1 AS x)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertEquals("st/named/f.csv", listed("LIST @st/named"), "a named file keeps its name");

        engine.execute("COPY INTO @st/noslash FROM (SELECT 1 AS x)"
            + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertEquals("st/noslash", listed("LIST @st/noslash"),
            "a path with no trailing slash IS the file name");
    }
}
