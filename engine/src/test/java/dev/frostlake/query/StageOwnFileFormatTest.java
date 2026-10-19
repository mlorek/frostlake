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

package dev.frostlake.query;

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A staged-file query that writes no FILE_FORMAT reads the files by the format their stage keeps — a named stage's
 * FILE_FORMAT, inline or naming a format, and a table stage's STAGE_FILE_FORMAT — and {@code FILE_FORMAT => NULL}
 * names no format at all, reading them by the default (live-verified).
 */
public class StageOwnFileFormatTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sf_one (a INT)");
        engine.execute("CREATE TABLE sf_two (a INT, b VARCHAR) STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')");
        engine.execute("CREATE TABLE sf_copy (a VARCHAR)");
        engine.execute("CREATE FILE FORMAT sf_pipe TYPE = CSV FIELD_DELIMITER = '|'");
        engine.execute("CREATE STAGE sf_inline FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')");
        engine.execute("CREATE STAGE sf_named FILE_FORMAT = sf_pipe");
        engine.execute("CREATE STAGE sf_listed FILE_FORMAT = (FORMAT_NAME = 'sf_pipe')");
        engine.execute("CREATE STAGE sf_header FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1"
            + " NULL_IF = ('b'))");
        put("one.csv", "1\n2\n", "@%sf_one");
        put("pipe.csv", "1|a\n2|b\n", "@%sf_two");
        put("pipe.csv", "1|a\n2|b\n", "@sf_inline");
        put("pipe.csv", "1|a\n2|b\n", "@sf_named");
        put("pipe.csv", "1|a\n2|b\n", "@sf_listed");
        put("header.csv", "h|h\n1|a\n2|b\n", "@sf_header");
    }

    /** Writes {@code text} to a file of its own and PUTs it on {@code stage}. */
    private void put(final String name, final String text, final String stage) {
        try {
            final Path file = Files.createTempDirectory("sf_own_format").resolve(name);
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            engine.executeQuery("PUT file://" + file.toAbsolutePath() + " " + stage + " AUTO_COMPRESS=FALSE");
        } catch (final IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    @Test
    public void aQueryThatNamesNoFormatReadsTheStagesOwn() {
        assertCells(engine, new String[][] {
            {"SELECT $1, $2 FROM @sf_inline ORDER BY 1", "1, a | 2, b"},
            {"SELECT $1, $2 FROM @sf_inline (PATTERN => NULL) ORDER BY 1", "1, a | 2, b"},
            {"SELECT $1, $2 FROM @sf_named ORDER BY 1", "1, a | 2, b"},
            {"SELECT $1, $2 FROM @sf_listed ORDER BY 1", "1, a | 2, b"},
            {"SELECT t.$2 FROM @sf_named t ORDER BY 1", "a | b"},
            {"SELECT $1, $2 FROM @sf_header ORDER BY 1", "1, a | 2, null"},
            {"SELECT $1, $2 FROM @%sf_two ORDER BY 1", "1, a | 2, b"},
        });
    }

    @Test
    public void aNullFormatReadsByTheDefault() {
        assertCells(engine, new String[][] {
            {"SELECT $1, $2 FROM @sf_inline (FILE_FORMAT => NULL) ORDER BY 1", "1|a, null | 2|b, null"},
            {"SELECT $1 FROM @sf_header (FILE_FORMAT => NULL) ORDER BY 1", "1|a | 2|b | h|h"},
            {"SELECT $1 FROM @%sf_one (FILE_FORMAT => NULL) ORDER BY 1", "1 | 2"},
            {"SELECT $1 FROM @%sf_one (FILE_FORMAT => NULL, PATTERN => NULL) ORDER BY 1", "1 | 2"},
            {"INSERT INTO sf_copy SELECT $1 FROM @%sf_one (FILE_FORMAT => NULL)", "2"},
            {"SELECT a FROM sf_copy ORDER BY 1", "1 | 2"},
            {"ALTER STAGE sf_inline SET FILE_FORMAT = (TYPE = CSV)", "Statement executed successfully."},
            {"SELECT $1, $2 FROM @sf_inline ORDER BY 1", "1|a, null | 2|b, null"},
        });
    }
}
