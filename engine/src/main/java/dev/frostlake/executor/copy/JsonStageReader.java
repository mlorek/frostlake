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

package dev.frostlake.executor.copy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON staged-file reader: a file is read as newline-delimited JSON, one record per non-blank line, and a top-level
 * array is one record — or, under STRIP_OUTER_ARRAY = TRUE, one record per element (live-verified). The reader's
 * own {@link #readRecords(Path)} strips the outer array, as callers that know no file format read it.
 */
public class JsonStageReader implements StageFileReader {

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        return readRecords(file, true);
    }

    /**
     * A file's records under the format's STRIP_OUTER_ARRAY.
     *
     * @param file             the file
     * @param stripOuterArray  whether a top-level array's elements are records of their own
     * @return the records
     * @throws IOException when the file cannot be read
     */
    public List<JsonNode> readRecords(final Path file, final boolean stripOuterArray) throws IOException {
        return records(StagedFileIo.readString(file).trim(), stripOuterArray, file);
    }

    /**
     * A file's records under the format's STRIP_OUTER_ARRAY and REPLACE_INVALID_CHARACTERS: a byte that is no part of
     * UTF-8 refuses the file in the account's words — {@code Error parsing JSON: invalid UTF-8 sequence: 0xE9}, the
     * file, the line and the byte's character within it — unless the option reads each such sequence as U+FFFD
     * (live-verified).
     *
     * @param file             the file
     * @param stripOuterArray  whether a top-level array's elements are records of their own
     * @param replaceInvalid   whether REPLACE_INVALID_CHARACTERS = TRUE
     * @param shownName        the file as the refusal names it
     * @return the records
     * @throws IOException when the file cannot be read
     */
    public List<JsonNode> readRecords(final Path file, final boolean stripOuterArray, final boolean replaceInvalid,
                                      final String shownName) throws IOException {
        final String marked = StagedFileIo.readMarked(file, StandardCharsets.UTF_8);
        final int unreadable = StagedFileIo.firstUnreadable(marked);
        if (unreadable >= 0 && !replaceInvalid) {
            throw StagedFileIo.unreadableRefusal("JSON", marked, unreadable, shownName);
        }
        return records(StagedFileIo.unreadableReplaced(marked).trim(), stripOuterArray, file);
    }

    private static List<JsonNode> records(final String text, final boolean stripOuterArray, final Path file) {
        final List<JsonNode> records = new ArrayList<>();
        if (text.isEmpty()) {
            return records;
        }
        try {
            if (text.charAt(0) == '[' && stripOuterArray) {
                final JsonNode array = MAPPER.readTree(text);
                for (int i = 0; i < array.size(); i++) {
                    records.add(array.get(i));
                }
            } else {
                for (final String line : text.split("\\r?\\n")) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        records.add(MAPPER.readTree(trimmed));
                    }
                }
            }
        } catch (final RuntimeException jsonError) {
            throw new RuntimeException("Invalid JSON in file " + file.getFileName() + ": " + jsonError.getMessage(),
                jsonError);
        }
        return records;
    }

    @Override
    public String formatType() {
        return "JSON";
    }
}
