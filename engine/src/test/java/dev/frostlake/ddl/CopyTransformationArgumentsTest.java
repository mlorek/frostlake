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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A COPY's transformation query takes a staged-file query's arguments after its stage, judged as that query's are:
 * a format named over an empty stage loads nothing, and a format written as a list is no constant (live-verified).
 * The format and the pattern the arguments give read the files where the COPY writes none of its own. Engine only:
 * COPY is not run on the live account.
 */
public class CopyTransformationArgumentsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "COPY is not run on the live account");
        engine.execute("CREATE STAGE ust");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE FILE FORMAT ff_pipe TYPE = CSV FIELD_DELIMITER = '|'");
        engine.execute("CREATE TABLE st_t (a VARCHAR, b VARCHAR)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private List<String> loaded() {
        final List<String> out = new ArrayList<>();
        final ResultSet rows = engine.executeQuery("SELECT a || '/' || COALESCE(b, '-') FROM st_t ORDER BY 1");
        for (final Row row : rows.getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        return out;
    }

    @Test
    public void theArgumentsAreJudgedAsAStagedQuerys() {
        assertEquals("Copy executed with 0 files processed.", String.valueOf(engine.executeQuery(
            "COPY INTO st_t FROM (SELECT $1, $2 FROM @ust (FILE_FORMAT => ff))").getRows().get(0).getValue(0)));
        assertEquals("SQL compilation error:\nInvalid argument [FILE_FORMAT=>[unresolved function: \"=\"](TYPE, CSV)]"
            + " for table function. Table function argument is required to be a constant.",
            refusal("COPY INTO st_t FROM (SELECT $1, $2 FROM @ust (FILE_FORMAT => (TYPE = CSV)))"));
        assertEquals("Unsupported feature 'TABLE'.",
            refusal("COPY INTO st_t FROM (SELECT $1, $2 FROM @ust (ff))"));
    }

    @Test
    public void theArgumentsReadTheFilesWhereTheCopyWritesNoneOfItsOwn() {
        stageLocalFile("ust", "p1.csv", "x|1\ny|2\n");
        stageLocalFile("ust", "q1.csv", "z|3\n");
        engine.execute("COPY INTO st_t FROM (SELECT $1, $2 FROM @ust (FILE_FORMAT => ff_pipe, PATTERN => '.*p1.*'))");
        assertEquals("[x/1, y/2]", loaded().toString());
        engine.execute("TRUNCATE TABLE st_t");
        // The COPY's own FILE_FORMAT and PATTERN are read first.
        engine.execute("COPY INTO st_t FROM (SELECT $1, $2 FROM @ust (FILE_FORMAT => ff_pipe, PATTERN => '.*p1.*'))"
            + " FILE_FORMAT = (FORMAT_NAME = 'ff') PATTERN = '.*q1.*' FORCE = TRUE");
        assertEquals("[z|3/-]", loaded().toString());
    }
}
