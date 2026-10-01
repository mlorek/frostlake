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

import dev.frostlake.executor.copy.StageFileReader;
import dev.frostlake.executor.copy.StagedFileColumn;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.LocalInputFile;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parquet staged-file reader. Reads the columnar file via a {@link LocalInputFile} (a local java.nio file,
 * so no Hadoop FileSystem) and surfaces each row as an Avro {@link GenericRecord}, converted to a
 * {@link JsonNode} by the shared {@link AvroRecordConverter}. Parquet therefore loads through the same
 * record path as Avro — fields to columns by name, or the whole row into a VARIANT.
 */
public class ParquetStageReader implements StageFileReader {

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        final List<JsonNode> records = new ArrayList<>();
        final InputFile inputFile = new LocalInputFile(file);
        try (final ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            GenericRecord record = reader.read();
            while (record != null) {
                records.add(AvroRecordConverter.toJson(record));
                record = reader.read();
            }
        } catch (final IOException io) {
            throw io;
        } catch (final RuntimeException parquetError) {
            throw new RuntimeException("Invalid Parquet in file " + file.getFileName() + ": "
                + parquetError.getMessage(), parquetError);
        }
        return records;
    }

    @Override
    public String formatType() {
        return "PARQUET";
    }

    @Override
    public List<StagedFileColumn> readColumns(final Path file, final Map<String, String> formatOptions,
                                              final boolean iceberg) {
        return ParquetColumnTypes.read(file, formatOptions, iceberg);
    }
}
