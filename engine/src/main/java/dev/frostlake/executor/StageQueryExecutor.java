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

package dev.frostlake.executor;

import dev.frostlake.executor.copy.CopyCommandExecutor;
import dev.frostlake.executor.copy.StageFileReader;
import dev.frostlake.executor.copy.StageReaderFactory;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Query staged files directly — {@code SELECT $1, $2, metadata$filename FROM @stage[/path]
 * [(FILE_FORMAT => 'name', PATTERN => 'regex')]} — and the {@code DIRECTORY(@stage)} directory
 * table (file-level metadata). Reuses the COPY loader's plumbing: stage resolution, file listing
 * with PATTERN, the {@link StageFileReader} SPI for record formats, and the CSV field splitter.
 *
 * <p>CSV files expose one VARCHAR column per field ($1..$n, n = the widest row seen); record
 * formats (JSON, XML, Avro, …) expose the whole record as a single VARIANT $1. The
 * {@code metadata$filename} / {@code metadata$file_row_number} columns ride along on every row but
 * are hidden from {@code SELECT *} (Snowflake reserves the METADATA$ prefix).
 */
final class StageQueryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(StageQueryExecutor.class);

    private final QueryExecutor executor;

    StageQueryExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    /** {@code FROM @stage[/path] [(params)]} as a virtual table of staged-file fields. */
    TableData queryStage(final String location,
                         final FrostlakeParser.StageQueryParamsContext params, final String alias) {
        String fileFormat = "CSV";
        String pattern = null;
        char delimiter = ',';
        Character enclosure = null;
        int skipHeader = 0;

        if (params != null) {
            for (final FrostlakeParser.StageQueryParamContext param : params.stageQueryParam()) {
                final String key = param.FILE_FORMAT() != null ? "FILE_FORMAT"
                    : param.PATTERN() != null ? "PATTERN"
                    : ParseTreeText.getIdentifier(param.identifier()).toUpperCase();
                final String value = param.STRING_LITERAL() != null
                    ? ParseTreeText.extractStringLiteral(param.STRING_LITERAL())
                    : ParseTreeText.getQualifiedName(param.qualifiedName());
                if ("FILE_FORMAT".equals(key)) {
                    // FILE_FORMAT => 'name' (or an unquoted, possibly qualified name) names a CREATE
                    // FILE FORMAT object; fall back to treating the value as a bare TYPE (CSV/JSON/...)
                    // so ad-hoc queries stay convenient.
                    FileFormat named = lookupFileFormat(value);
                    if (named == null && value.contains(".")) {
                        named = lookupFileFormat(value.substring(value.lastIndexOf('.') + 1));
                    }
                    if (named != null) {
                        fileFormat = named.getType();
                        if (named.getOption("FIELD_DELIMITER") != null && !named.getOption("FIELD_DELIMITER").isEmpty()) {
                            delimiter = named.getOption("FIELD_DELIMITER").charAt(0);
                        }
                        if (named.getOption("SKIP_HEADER") != null) {
                            skipHeader = Integer.parseInt(named.getOption("SKIP_HEADER"));
                        }
                        final String enclosed = named.getOption("FIELD_OPTIONALLY_ENCLOSED_BY");
                        if (enclosed != null && !enclosed.isEmpty() && !"NONE".equalsIgnoreCase(enclosed)) {
                            enclosure = enclosed.charAt(0);
                        }
                    } else {
                        fileFormat = value.toUpperCase();
                    }
                } else if ("PATTERN".equals(key)) {
                    pattern = value;
                }
            }
        }

        final Path base = resolveBase(location);
        final List<Path> files = filesToRead(base, pattern);
        final boolean csv = "CSV".equals(fileFormat);
        final StageFileReader recordReader = csv ? null : StageReaderFactory.forType(fileFormat);
        if (!csv && recordReader == null) {
            throw new RuntimeException("Stage query: file format type '" + fileFormat
                + "' is not supported (supported: " + StageReaderFactory.supportedTypesDisplay() + ")");
        }

        final List<List<Object>> fieldRows = new ArrayList<>();
        final List<String> fileNames = new ArrayList<>();
        final List<Long> rowNumbers = new ArrayList<>();
        int width = csv ? 0 : 1;
        for (final Path file : files) {
            final String relativeName = base != null && !base.equals(file) && file.startsWith(base)
                ? base.relativize(file).toString() : file.getFileName().toString();
            long rowNumber = 0;
            try {
                if (recordReader != null) {
                    for (final JsonNode record : recordReader.readRecords(file)) {
                        fieldRows.add(new ArrayList<>(Arrays.asList((Object) record.toString())));
                        fileNames.add(relativeName);
                        rowNumbers.add(++rowNumber);
                    }
                } else {
                    final List<String> lines = Files.readAllLines(file);
                    for (int ln = skipHeader; ln < lines.size(); ln++) {
                        if (lines.get(ln).isEmpty()) {
                            continue;
                        }
                        final List<String> fields = CopyCommandExecutor.parseCsvLine(lines.get(ln), delimiter, enclosure);
                        width = Math.max(width, fields.size());
                        fieldRows.add(new ArrayList<Object>(fields));
                        fileNames.add(relativeName);
                        rowNumbers.add(++rowNumber);
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException("Stage query: cannot read staged file " + file + ": " + e.getMessage(), e);
            }
        }

        final List<TableColumn> columns = new ArrayList<>();
        for (int i = 1; i <= width; i++) {
            // $N canonicalizes to COLUMNN engine-wide (SqlIdentifiers), so the virtual columns use
            // that name — $1/$2 references resolve, and star output shows COLUMN1.. like COPY does.
            columns.add(new TableColumn("COLUMN" + i, csv ? StringType.VARCHAR : VariantType.VARIANT,
                true, null, false, false, false));
        }
        columns.add(new TableColumn("METADATA$FILENAME", StringType.VARCHAR, true, null, false, false, false));
        columns.add(new TableColumn("METADATA$FILE_ROW_NUMBER", NumericType.BIGINT, true, null, false, false, false));

        final List<Row> rows = new ArrayList<>();
        for (int r = 0; r < fieldRows.size(); r++) {
            final List<Object> values = fieldRows.get(r);
            while (values.size() < width) {
                values.add(null);
            }
            values.add(fileNames.get(r));
            values.add(rowNumbers.get(r));
            rows.add(new Row(values));
        }
        logger.debug("Stage query over {} file(s): {} row(s), {} field column(s)", files.size(), rows.size(), width);

        final Table virtualTable = new Table(alias != null ? alias : "STAGE_QUERY", columns, false);
        return new TableData(virtualTable, rows, alias);
    }

    /** {@code DIRECTORY(@stage)}: one row of file-level metadata per staged file. */
    TableData directoryTable(final FrostlakeParser.StageRefContext stageRef, final String alias) {
        final List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("RELATIVE_PATH", StringType.VARCHAR, true, null, false, false, false));
        columns.add(new TableColumn("SIZE", NumericType.BIGINT, true, null, false, false, false));
        columns.add(new TableColumn("LAST_MODIFIED", DateTimeType.TIMESTAMP_NTZ, true, null, false, false, false));
        columns.add(new TableColumn("MD5", StringType.VARCHAR, true, null, false, false, false));
        columns.add(new TableColumn("ETAG", StringType.VARCHAR, true, null, false, false, false));
        columns.add(new TableColumn("FILE_URL", StringType.VARCHAR, true, null, false, false, false));

        final Path base = resolveBase(executor.stageRefToLocation(stageRef));
        final List<Row> rows = new ArrayList<>();
        for (final Path file : filesToRead(base, null)) {
            final String relative = base != null && !base.equals(file) && file.startsWith(base)
                ? base.relativize(file).toString() : file.getFileName().toString();
            long size = 0;
            Timestamp modified = null;
            try {
                size = Files.size(file);
                modified = new Timestamp(Files.getLastModifiedTime(file).toMillis());
            } catch (final IOException e) {
                logger.warn("DIRECTORY(@stage): cannot stat {}: {}", file, e.getMessage());
            }
            rows.add(new Row(Arrays.asList(relative, size, modified, null, null, file.toUri().toString())));
        }
        final Table virtualTable = new Table(alias != null ? alias : "DIRECTORY", columns, false);
        return new TableData(virtualTable, rows, alias);
    }

    /** The named CREATE FILE FORMAT object, or null when no such format exists. */
    private FileFormat lookupFileFormat(final String name) {
        try {
            return executor.getCatalog().getFileFormat(name);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** The local path a stage location resolves to (a directory, or a single staged file). */
    private Path resolveBase(final String location) {
        final Path base = executor.resolveCopyBaseDir(location);
        if (base == null) {
            throw new RuntimeException("Stage query: cannot resolve stage location " + location);
        }
        return base;
    }

    /** The staged files to read: the single file itself, or the directory's files (PATTERN-filtered). */
    private List<Path> filesToRead(final Path base, final String pattern) {
        if (Files.isRegularFile(base)) {
            final List<Path> single = new ArrayList<>();
            single.add(base);
            return single;
        }
        if (!Files.isDirectory(base)) {
            throw new RuntimeException("Stage query: staged path does not exist: " + base);
        }
        return executor.listCopyFiles(base, pattern, null);
    }
}
