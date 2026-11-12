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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GET's PATTERN: "the command lists all files in the specified path and applies the regular expression pattern on
 * each of the files found" (documented), as LIST and COPY apply theirs, so only the files it matches are downloaded.
 * GET downloaded every file under the path, a pattern that matched nothing included.
 */
public class GetPatternTest extends BaseDatabaseTest {

    /** Each row's file and status, a semicolon between rows. */
    private String fetched(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(rs.getValue(0)).append(':').append(rs.getValue(2));
        }
        return text.toString();
    }

    private static String[] namesIn(final Path dir) {
        final String[] names = dir.toFile().list();
        final String[] sorted = names == null ? new String[0] : names;
        Arrays.sort(sorted);
        return sorted;
    }

    @Test
    public void onlyTheMatchingFilesAreDownloaded() throws IOException {
        final Path out = Files.createTempDirectory("fl-get-pattern");
        final Path matched = out.resolve("matched");
        final Path none = out.resolve("none");
        try {
            engine.execute("CREATE STAGE gp_st");
            engine.execute("COPY INTO @gp_st/d/t_jan.csv FROM (SELECT 1 AS a) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) HEADER = TRUE SINGLE = TRUE");
            engine.execute("COPY INTO @gp_st/d/u_feb.csv FROM (SELECT 2 AS a) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) HEADER = TRUE SINGLE = TRUE");
            assertEquals("d/u_feb.csv:DOWNLOADED",
                fetched("GET @gp_st/d/ 'file://" + matched + "/' PATTERN = '.*u_.*'"));
            assertArrayEquals(new String[] {"u_feb.csv"}, namesIn(matched));
            assertEquals("", fetched("GET @gp_st 'file://" + none + "/' PATTERN = '.*nomatch.*'"));
            assertArrayEquals(new String[0], namesIn(none));
        } finally {
            for (final Path dir : Arrays.asList(matched, none, out)) {
                final File[] files = dir.toFile().listFiles();
                if (files != null) {
                    for (final File file : files) {
                        Files.deleteIfExists(file.toPath());
                    }
                }
                Files.deleteIfExists(dir);
            }
        }
    }
}
