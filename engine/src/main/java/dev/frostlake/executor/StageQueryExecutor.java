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
import dev.frostlake.executor.copy.StagedFileIo;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.StagePositions;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.WidthlessStringType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
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
        // CSV's EMPTY_FIELD_AS_NULL reaches the stage-query path too, default TRUE and all: an unenclosed
        // empty field reads as SQL NULL, not as the empty string. Live-verified with a control
        // beside it — the same file read through a format carrying EMPTY_FIELD_AS_NULL = FALSE answered the
        // empty string for the same field.
        boolean emptyFieldAsNull = true;

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
                        if (named.getOption("EMPTY_FIELD_AS_NULL") != null) {
                            emptyFieldAsNull =
                                !"FALSE".equalsIgnoreCase(named.getOption("EMPTY_FIELD_AS_NULL"));
                        }
                    } else {
                        fileFormat = value.toUpperCase();
                    }
                } else if ("PATTERN".equals(key)) {
                    pattern = value;
                }
            }
        }

        // The path selects files by prefix, at any depth, and PATTERN matches the file's stored path —
        // the reading COPY gives both (live-verified).
        final StagePrefix staged = requireRoot(executor.resolveStagePrefix(location), location);
        final List<Path> files = staged.files(pattern);
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
            final String relativeName = staged.metadataFileName(file);
            long rowNumber = 0;
            try {
                if (recordReader != null) {
                    for (final JsonNode record : recordReader.readRecords(file)) {
                        fieldRows.add(new ArrayList<>(Arrays.asList((Object) record.toString())));
                        fileNames.add(relativeName);
                        rowNumbers.add(++rowNumber);
                    }
                } else {
                    // Compressed files read through, as COPY reads them: a gzipped unload is a CSV file too.
                    final List<String> lines = readLines(file);
                    for (int ln = skipHeader; ln < lines.size(); ln++) {
                        if (lines.get(ln).isEmpty()) {
                            continue;
                        }
                        final List<Boolean> enclosedEmpty = new ArrayList<>();
                        final List<String> fields = CopyCommandExecutor.parseCsvLine(
                            lines.get(ln), delimiter, enclosure, enclosedEmpty);
                        width = Math.max(width, fields.size());
                        final List<Object> values = new ArrayList<Object>(fields);
                        if (emptyFieldAsNull) {
                            for (int f = 0; f < values.size(); f++) {
                                final boolean enclosedBlank = f < enclosedEmpty.size()
                                    && enclosedEmpty.get(f).booleanValue();
                                if ("".equals(values.get(f)) && !enclosedBlank) {
                                    values.set(f, null);
                                }
                            }
                        }
                        fieldRows.add(values);
                        fileNames.add(relativeName);
                        rowNumbers.add(++rowNumber);
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException("Stage query: cannot read staged file " + file + ": " + e.getMessage(), e);
            }
        }

        final List<TableColumn> columns = new ArrayList<>();
        // A positional column's declared width follows the KIND of stage: a named or user stage
        // declares the 128MB text, a table stage the 16MB one (live-verified). The values agree.
        final DataType fieldType = csv ? (staged.kind() == StageKind.TABLE ? StringType.VARCHAR
            : new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR))
            : VariantType.VARIANT;
        for (int i = 1; i <= width; i++) {
            // $N canonicalizes to COLUMNN engine-wide (SqlIdentifiers), so the virtual columns use
            // that name — $1/$2 references resolve, and star output shows COLUMN1.. like COPY does.
            columns.add(new TableColumn("COLUMN" + i, fieldType, true, null, false, false, false));
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
        // $N reads the fields by position, never the METADATA$ columns after them, and NULL past the fields up
        // to the positions the query reads: 4096 for a CSV query over a named or user stage, as many as its
        // table's columns over a table stage (see StagePositions).
        final boolean tableStage = staged.kind() == StageKind.TABLE;
        final int positions = !csv ? width : tableStage ? tableStageColumns(location)
            : StagePositions.NAMED_STAGE_POSITIONS;
        virtualTable.setStagePositions(new StagePositions(width, positions, fieldType, csv && !tableStage));
        return new TableData(virtualTable, rows, alias);
    }

    /**
     * How many columns the table a table stage belongs to declares, or 0 when the location names none: the
     * location spells the stage {@code @%<table>[/path]}, as {@link QueryExecutor#resolveStagePrefix} reads it.
     */
    private int tableStageColumns(final String location) {
        final String body = location.substring(1);
        final int slash = body.indexOf('/');
        final String stagePart = slash >= 0 ? body.substring(0, slash) : body;
        if (!stagePart.startsWith("%")) {
            return 0;
        }
        try {
            final Table owner = executor.getCatalog().resolveTable(QualifiedName.parse(stagePart.substring(1)));
            return owner == null ? 0 : owner.getColumns().size();
        } catch (final RuntimeException unresolved) {
            return 0;
        }
    }

    /**
     * {@code DIRECTORY(@stage)}: one row of file-level metadata per staged file, in the account's declared
     * types — RELATIVE_PATH a VARCHAR(134217728), SIZE a NUMBER(38,0), LAST_MODIFIED a TIMESTAMP_TZ(3) at the
     * session's offset, and MD5, ETAG and FILE_URL a VARCHAR of no width (live-verified; a table built over the
     * listing stores the texts as VARCHAR(16777216)).
     */
    TableData directoryTable(final FrostlakeParser.StageRefContext stageRef, final String alias) {
        DirectoryStageReference.refuse(stageRef, executor.getCatalog());
        final List<TableColumn> columns = directoryColumns();
        final List<Row> rows = directoryListing(executor.stageRefToLocation(stageRef));
        final Table virtualTable = new Table(alias != null ? alias : "DIRECTORY", columns, false);
        return new TableData(virtualTable, rows, alias);
    }

    /** The columns a directory table lists, in the account's declared types. */
    static List<TableColumn> directoryColumns() {
        final List<TableColumn> columns = new ArrayList<>();
        columns.add(directoryColumn("RELATIVE_PATH", new StringType("VARCHAR", StringResultWidths.UNBOUNDED)));
        columns.add(directoryColumn("SIZE", new NumericType("NUMBER", 38, 0)));
        columns.add(directoryColumn("LAST_MODIFIED", new DateTimeType("TIMESTAMP_TZ", 3, true)));
        columns.add(directoryColumn("MD5", WidthlessStringType.WIDTHLESS));
        columns.add(directoryColumn("ETAG", WidthlessStringType.WIDTHLESS));
        columns.add(directoryColumn("FILE_URL", WidthlessStringType.WIDTHLESS));
        return columns;
    }

    /** The directory table's rows for a stage location: every file of the stage, at any depth. */
    List<Row> directoryListing(final String location) {
        // A directory table lists every file of the stage, at any depth, by its stage-relative path.
        final StagePrefix staged = requireRoot(executor.resolveStagePrefix(location), location);
        final List<Row> rows = new ArrayList<>();
        for (final Path file : staged.files(null)) {
            final String relative = staged.relative(file);
            long size = 0;
            ZonedDateTime modified = null;
            try {
                size = Files.size(file);
                modified = Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis())
                    .atZone(SessionZone.current()).withFixedOffsetZone();
            } catch (final IOException e) {
                logger.warn("DIRECTORY(@stage): cannot stat {}: {}", file, e.getMessage());
            }
            // FILE_URL is the file's stage file URL, as BUILD_STAGE_FILE_URL(@stage, RELATIVE_PATH) spells it.
            rows.add(new Row(Arrays.asList(relative, size, modified, null, null,
                executor.stageFileUrl(location, relative))));
        }
        return rows;
    }

    /** A DIRECTORY listing's column, its type as the account declares it. */
    private static TableColumn directoryColumn(final String name, final DataType type) {
        final TableColumn column = new TableColumn(name, type, true, null, false, false, false);
        column.setStaticallyTyped(true);
        return column;
    }

    /** The named CREATE FILE FORMAT object, or null when no such format exists. */
    private FileFormat lookupFileFormat(final String name) {
        try {
            return executor.getCatalog().getFileFormat(name);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** A staged file's lines, decompressed when the file is gzipped. */
    private static List<String> readLines(final Path file) throws IOException {
        final List<String> lines = new ArrayList<>();
        try (BufferedReader reader = StagedFileIo.reader(file)) {
            String line = reader.readLine();
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        }
        return lines;
    }

    /** A stage reference whose stage has a local directory behind it; any other cannot be read. */
    private static StagePrefix requireRoot(final StagePrefix staged, final String location) {
        if (staged.getRoot() == null) {
            throw new RuntimeException("Stage query: cannot resolve stage location " + location);
        }
        return staged;
    }
}
