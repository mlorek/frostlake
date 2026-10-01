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
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Avro staged-file reader. An Avro container file is self-describing (embedded schema); each record becomes
 * a {@link JsonNode} object via {@link AvroRecordConverter} (a single VARIANT column receives the whole
 * record; otherwise fields map to columns by name).
 */
public class AvroStageReader implements StageFileReader {

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        final List<JsonNode> records = new ArrayList<>();
        final DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        try (final DataFileReader<GenericRecord> reader = new DataFileReader<>(file.toFile(), datumReader)) {
            while (reader.hasNext()) {
                records.add(AvroRecordConverter.toJson(reader.next()));
            }
        } catch (final IOException io) {
            throw io;
        } catch (final RuntimeException avroError) {
            throw new RuntimeException("Invalid Avro in file " + file.getFileName() + ": " + avroError.getMessage(),
                avroError);
        }
        return records;
    }

    @Override
    public String formatType() {
        return "AVRO";
    }

    @Override
    public List<StagedFileColumn> readColumns(final Path file, final Map<String, String> formatOptions,
                                              final boolean iceberg) {
        return AvroColumnTypes.read(file);
    }
}
