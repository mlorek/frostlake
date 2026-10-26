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

package dev.frostlake.formats;

import dev.frostlake.executor.copy.FileFormatOptions;
import dev.frostlake.executor.copy.StageFileReader;
import dev.frostlake.executor.copy.StagedFileColumn;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ORC staged-file reader. Reads a local {@code .orc} file (via the Hadoop LocalFileSystem — the
 * {@code file://} path needs no cluster) and converts each row of every column-vector batch to a
 * {@link JsonNode} through {@link OrcRecordConverter}, so ORC loads through the same record path as the
 * other formats (fields to columns by name, or the whole row into a VARIANT).
 */
public class OrcStageReader implements StageFileReader {

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        return records(file, true);
    }

    /**
     * A file's records under its format's options. A string that is no UTF-8 is refused in the account's words (see
     * {@link OrcUnreadableText}) unless REPLACE_INVALID_CHARACTERS reads each such byte as U+FFFD; a query that reads
     * none of the file's positional columns decodes no value, and COUNT(*) answers the rows (live-verified).
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
        return records(file, options.replacesInvalidCharacters() || options.readsNoPositionalColumn());
    }

    private List<JsonNode> records(final Path file, final boolean replaceInvalid) throws IOException {
        final List<JsonNode> records = new ArrayList<>();
        // org.apache.hadoop.fs.Path collides with java.nio.file.Path (the parameter), so qualify it inline.
        final org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(file.toUri());
        final Configuration conf = new Configuration();
        try (final Reader reader = OrcFile.createReader(hadoopPath, OrcFile.readerOptions(conf));
             final RecordReader rows = reader.rows()) {
            final TypeDescription schema = reader.getSchema();
            final VectorizedRowBatch batch = schema.createRowBatch();
            while (rows.nextBatch(batch)) {
                for (int row = 0; row < batch.size; row++) {
                    records.add(OrcRecordConverter.rowToJson(schema, batch, row, replaceInvalid));
                }
            }
        } catch (final IOException io) {
            throw io;
        } catch (final OrcUnreadableText unreadable) {
            throw unreadable;
        } catch (final RuntimeException orcError) {
            throw new RuntimeException("Invalid ORC in file " + file.getFileName() + ": " + orcError.getMessage(),
                orcError);
        }
        return records;
    }

    @Override
    public String formatType() {
        return "ORC";
    }

    @Override
    public List<StagedFileColumn> readColumns(final Path file, final Map<String, String> formatOptions,
                                              final boolean iceberg) {
        return OrcColumnTypes.read(file);
    }
}
