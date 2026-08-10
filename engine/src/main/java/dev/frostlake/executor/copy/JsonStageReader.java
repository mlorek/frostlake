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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON staged-file reader: a top-level array yields one record per element, otherwise the file is read as
 * newline-delimited JSON (one record per non-blank line).
 */
public class JsonStageReader implements StageFileReader {

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        final String text = StagedFileIo.readString(file).trim();
        final List<JsonNode> records = new ArrayList<>();
        if (text.isEmpty()) {
            return records;
        }
        try {
            if (text.charAt(0) == '[') {
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
