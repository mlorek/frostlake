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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads a staged file of a record-based FILE_FORMAT (JSON, XML, Avro, Parquet, ORC) into a list of
 * {@link JsonNode} records. The COPY loader turns each record into a row via its existing JSON row-building
 * path, so the record formats share one loading path. CSV, being positional text rather than records, keeps
 * its own path in the loader.
 *
 * <p>Readers are discovered at runtime as a {@link java.util.ServiceLoader} SPI: each implementation is a
 * provider listed in {@code META-INF/services/dev.frostlake.executor.copy.StageFileReader} and is keyed by
 * {@link #formatType()}. The zero-dependency JSON and XML readers ship in the engine; the heavier
 * Avro/Parquet/ORC readers live in the optional {@code frostlake-formats} module and light up only when it
 * is on the classpath — so the core engine carries no Hadoop/Avro/ORC dependencies. Adding a format is
 * "implement this interface and register the provider".
 */
public interface StageFileReader {

    /** Parse {@code file} into its records. An unreadable/malformed file throws (a bad load is never silent). */
    List<JsonNode> readRecords(final Path file) throws IOException;

    /** The FILE_FORMAT TYPE this reader handles (e.g. {@code "JSON"}), matched case-insensitively. */
    String formatType();

    /**
     * The top-level columns a staged file of a self-describing format declares, as INFER_SCHEMA reports them.
     * A file this reader cannot read as its format is not refused: it declares no column at all.
     *
     * @param file the staged file
     * @param formatOptions the file format's options by upper-cased name, values as stored
     * @param iceberg whether the call asked for the types an Iceberg table takes ({@code KIND => 'ICEBERG'})
     * @return the columns in file order, empty for a file of another format; null when this reader does not
     *         describe files at all
     * @throws IOException when the file cannot be read
     */
    default List<StagedFileColumn> readColumns(final Path file, final Map<String, String> formatOptions,
                                               final boolean iceberg) throws IOException {
        return null;
    }
}
