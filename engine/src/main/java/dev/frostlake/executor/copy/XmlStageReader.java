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

import dev.frostlake.functions.scalar.semistructured.ParseXml;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * XML staged-file reader. A file reads as records in Snowflake's XML-in-VARIANT model, as PARSE_XML builds it (see
 * {@link ParseXml#parseRecords}): every top-level element a record, or every element those hold under
 * STRIP_OUTER_ELEMENT, attribute and element text turned into numbers and booleans unless DISABLE_AUTO_CONVERT, and
 * text trimmed unless PRESERVE_SPACE (live-verified). External entities and external DTDs are never read.
 */
public class XmlStageReader implements StageFileReader {

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        return readRecords(file, new FileFormatOptions(), String.valueOf(file.getFileName()));
    }

    /**
     * A file's records under its format's options. A byte that is no part of UTF-8 refuses the file in the account's
     * words — {@code Error parsing XML: invalid UTF-8 sequence: 0xE9}, the file, the line and the byte's character
     * within it — unless REPLACE_INVALID_CHARACTERS or IGNORE_UTF8_ERRORS reads each such byte as U+FFFD
     * (live-verified).
     *
     * @param file      the file
     * @param options   the format's options
     * @param shownName the file as a refusal names it
     * @return the records
     * @throws IOException when the file cannot be read
     */
    @Override
    public List<JsonNode> readRecords(final Path file, final FileFormatOptions options, final String shownName)
            throws IOException {
        final String marked = StagedFileIo.readMarked(file, StandardCharsets.UTF_8);
        final int unreadable = StagedFileIo.firstUnreadable(marked);
        if (unreadable >= 0 && !options.replacesInvalidCharacters()) {
            throw StagedFileIo.unreadableRefusal("XML", marked, unreadable, shownName);
        }
        try {
            return ParseXml.parseRecords(StagedFileIo.unreadableReplaced(marked), options.disablesAutoConvert(),
                options.preservesSpace(), options.stripsOuterElement());
        } catch (final IllegalArgumentException xmlError) {
            throw new RuntimeException("Invalid XML in file " + file.getFileName() + ": " + xmlError.getMessage(),
                xmlError);
        }
    }

    @Override
    public String formatType() {
        return "XML";
    }
}
