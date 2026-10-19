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

import dev.frostlake.executor.copy.CsvRecordReader;
import dev.frostlake.executor.copy.FileFormatOptions;
import dev.frostlake.executor.copy.JsonNullStripping;
import dev.frostlake.executor.copy.JsonStageReader;
import dev.frostlake.executor.copy.StageFileReader;
import dev.frostlake.executor.copy.StageReaderFactory;
import dev.frostlake.executor.copy.StagedFileIo;
import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StagePositions;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.persistence.CheckpointDebt;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Query staged files directly — {@code SELECT $1, $2, metadata$filename FROM @stage[/path]
 * [(FILE_FORMAT => name, PATTERN => 'regex')]}, its arguments read by {@link StageQueryArguments} — and the
 * {@code DIRECTORY(@stage)} directory
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
        // The options reach the stage-query path at their defaults, a named format's in their place: an unenclosed
        // empty field reads as SQL NULL (EMPTY_FIELD_AS_NULL), so does the text \N (NULL_IF), a backslash takes
        // the delimiter or itself literally (ESCAPE_UNENCLOSED_FIELD), TRIM_SPACE trims an unenclosed field, and a
        // blank line is a record of NULLs unless SKIP_BLANK_LINES passes it over. Live-verified, each with a control
        // beside it — the same file read through a format that turns the option off.
        final FileFormatOptions options = new FileFormatOptions();

        final StageQueryArguments arguments = StageQueryArguments.read(executor, params);
        FileFormat named = arguments.format();
        // A query that writes no FILE_FORMAT reads the files by the format their stage keeps — a named stage's
        // FILE_FORMAT, a table stage's STAGE_FILE_FORMAT — the format that one names first and its own options after.
        // FILE_FORMAT => NULL names none and reads them by the default (live-verified).
        final Map<String, String> kept = arguments.formatWritten() ? new HashMap<String, String>()
            : executor.stageFileFormat(location);
        if (named == null && kept.get("FORMAT_NAME") != null) {
            named = InferSchemaArguments.resolveFileFormat(executor.getCatalog(), kept.get("FORMAT_NAME"));
        }
        if (named != null) {
            options.takeFormat(named);
        }
        // A JSON format's STRIP_OUTER_ARRAY and STRIP_NULL_VALUES read the file as a load reads it (live-verified).
        options.takeAll(kept);
        final String fileFormat = options.getType();
        final String pattern = arguments.pattern();

        // The path selects files by prefix, at any depth, and PATTERN matches the file's stored path —
        // the reading COPY gives both (live-verified).
        final StagePrefix staged = requireRoot(executor.resolveStagePrefix(location), location);
        final List<Path> files = staged.files(pattern);
        // A write that reads these files reads them as they are now (see CheckpointDebt).
        CheckpointDebt.owe();
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
        // A CSV query over a table's stage reads its table's columns: as many positions, and every record held to
        // that width as a load holds it, unless the format turns the check off (see CsvRecordReader.tableWidthRefusal).
        // The first record refused is refused where the query reads the rows, after its names compile.
        final Table owner = csv && staged.kind() == StageKind.TABLE ? tableStageOwner(location) : null;
        final List<String> ownerColumns = new ArrayList<>();
        if (owner != null) {
            for (final TableColumn column : owner.getColumns()) {
                ownerColumns.add(column.getName());
            }
        }
        final boolean widthChecked = owner != null && options.checksColumnCount();
        RuntimeException refusedRecord = null;
        for (final Path file : files) {
            if (refusedRecord != null) {
                break;
            }
            final String relativeName = staged.metadataFileName(file);
            long rowNumber = 0;
            try {
                if (recordReader != null) {
                    // A JSON file's refusal of a byte that is no UTF-8 names it under its stage's storage path.
                    final List<JsonNode> records = recordReader instanceof JsonStageReader
                        ? ((JsonStageReader) recordReader).readRecords(file, options.stripsOuterArray(),
                            options.replacesInvalidCharacters(), storedName(staged, relativeName))
                        : recordReader.readRecords(file);
                    for (final JsonNode record : records) {
                        if (options.stripsNullValues()) {
                            JsonNullStripping.strip(record);
                        }
                        fieldRows.add(new ArrayList<>(Arrays.asList((Object) record.toString())));
                        fileNames.add(relativeName);
                        rowNumbers.add(++rowNumber);
                    }
                } else {
                    // Compressed files read through, as COPY reads them: a gzipped unload is a CSV file too. The
                    // records split as INFER_SCHEMA splits them (see CsvRecordReader): delimiters of any length,
                    // NONE included, an escaped line break inside a record, and a closing enclosure followed by
                    // anything but a delimiter refused.
                    // The file is read in its format's ENCODING, and a byte that set cannot read refuses its field
                    // unless REPLACE_INVALID_CHARACTERS reads it as U+FFFD (live-verified).
                    final CsvRecordReader reader = new CsvRecordReader(
                        new StringReader(StagedFileIo.readMarked(file, csvCharset(options.getEncoding()))),
                        relativeName, options, true).settlingUnreadable(options.replacesInvalidCharacters());
                    reader.skipLines(options.getSkipHeader());
                    final List<Boolean> enclosed = new ArrayList<>();
                    long fileRow = 0;
                    List<String> fields = reader.next(-1, ++fileRow, enclosed);
                    while (fields != null) {
                        refusedRecord = widthChecked ? reader.tableWidthRefusal(fields.size(), owner.getName(),
                            ownerColumns, fileRow == 1, fileRow) : null;
                        if (refusedRecord != null) {
                            break;
                        }
                        width = Math.max(width, fields.size());
                        // TRIM_SPACE, NULL_IF and EMPTY_FIELD_AS_NULL settle the fields as a load's are settled.
                        options.settle(fields, enclosed);
                        final List<Object> values = new ArrayList<Object>(fields);
                        // A field its file's encoding could not read holds its refusal until a query reads it.
                        for (final Map.Entry<Integer, RuntimeException> fault : reader.unreadableFaults().entrySet()) {
                            values.set(fault.getKey().intValue() - 1, new DeferredFault(fault.getValue()));
                        }
                        fieldRows.add(values);
                        fileNames.add(relativeName);
                        rowNumbers.add(++rowNumber);
                        fields = reader.next(-1, ++fileRow, enclosed);
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException("Stage query: cannot read staged file " + file + ": " + e.getMessage(), e);
            }
        }

        if (owner != null) {
            width = ownerColumns.size();
        }
        final List<TableColumn> columns = new ArrayList<>();
        // A positional column's declared width follows the KIND of stage: a named or user stage
        // declares the 128MB text, a table stage the 16MB one (live-verified). The values agree.
        final DataType fieldType = csv ? (staged.kind() == StageKind.TABLE ? StringType.VARCHAR
            : new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR))
            : VariantType.VARIANT;
        // A star reads a CSV query's fields over a table's stage only: over a named or user stage it reads none, so a
        // bare star is "SELECT with no columns" and ORDER BY 1 names no column (live-verified).
        final boolean starless = csv && staged.kind() != StageKind.TABLE;
        for (int i = 1; i <= width; i++) {
            // $N canonicalizes to COLUMNN engine-wide (SqlIdentifiers), so the virtual columns use
            // that name — $1/$2 references resolve, and star output shows COLUMN1.. like COPY does.
            final TableColumn field = new TableColumn("COLUMN" + i, fieldType, true, null, false, false, false);
            columns.add(starless ? field.starHiddenCopy() : field);
        }
        columns.add(new TableColumn("METADATA$FILENAME", StringType.VARCHAR, true, null, false, false, false));
        columns.add(new TableColumn("METADATA$FILE_ROW_NUMBER", NumericType.BIGINT, true, null, false, false, false));

        final List<Row> rows = new ArrayList<>();
        for (int r = 0; r < fieldRows.size(); r++) {
            final List<Object> values = fieldRows.get(r);
            while (values.size() < width) {
                values.add(null);
            }
            while (values.size() > width) {
                values.remove(values.size() - 1);
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
        final int positions = !csv ? width : tableStage ? ownerColumns.size()
            : StagePositions.NAMED_STAGE_POSITIONS;
        virtualTable.setStagePositions(new StagePositions(width, positions, fieldType, csv && !tableStage));
        if (refusedRecord != null) {
            final RuntimeException refused = refusedRecord;
            return new TableData(virtualTable, new RowsProvider() {
                @Override
                public List<Row> rows() {
                    throw refused;
                }

                @Override
                public String describe() {
                    return null;
                }
            }, alias, null);
        }
        return new TableData(virtualTable, rows, alias);
    }

    /**
     * The table a table stage belongs to, or null when the location names none: the location spells the stage
     * {@code @%<table>[/path]}, as {@link QueryExecutor#resolveStagePrefix} reads it.
     */
    private Table tableStageOwner(final String location) {
        final String body = location.substring(1);
        final int slash = body.indexOf('/');
        final String stagePart = slash >= 0 ? body.substring(0, slash) : body;
        if (!stagePart.startsWith("%")) {
            return null;
        }
        try {
            return executor.getCatalog().resolveTable(QualifiedName.parse(stagePart.substring(1)));
        } catch (final RuntimeException unresolved) {
            return null;
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
        final List<Row> rows = registeredFiles(StageReferenceShape.namedStage(stageRef, executor.getCatalog()),
            executor.stageRefToLocation(stageRef));
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

    /**
     * The rows a directory table lists: the files its last refresh registered, each as it was then, by path. A
     * file staged, replaced or removed since is listed as registered until the next refresh (live-verified). The
     * modification time is told at the session's offset and the file URL under the stage's names as they are now.
     */
    private List<Row> registeredFiles(final Stage stage, final String location) {
        final List<Row> rows = new ArrayList<>();
        for (final List<Object> registered : new TreeMap<>(stage.getDirectoryRegistry()).values()) {
            final List<Object> values = new ArrayList<>(registered);
            if (values.get(2) instanceof ZonedDateTime) {
                values.set(2, ((ZonedDateTime) values.get(2)).withZoneSameInstant(SessionZone.current())
                    .withFixedOffsetZone());
            }
            values.set(5, executor.stageFileUrl(location, String.valueOf(values.get(0))));
            rows.add(new Row(values));
        }
        return rows;
    }

    /**
     * What a refresh would register for a stage location: every file of the stage, at any depth, as it is now.
     * MD5 and ETAG are the digest of the file's bytes.
     */
    List<Row> directoryListing(final String location) {
        // A directory table lists every file of the stage, at any depth, by its stage-relative path.
        final StagePrefix staged = requireRoot(executor.resolveStagePrefix(location), location);
        final List<Row> rows = new ArrayList<>();
        for (final Path file : staged.files(null)) {
            final String relative = staged.relative(file);
            long size = 0;
            ZonedDateTime modified = null;
            String digest = null;
            try {
                size = Files.size(file);
                modified = Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis())
                    .atZone(SessionZone.current()).withFixedOffsetZone();
                digest = md5Of(file);
            } catch (final IOException e) {
                logger.warn("DIRECTORY(@stage): cannot stat {}: {}", file, e.getMessage());
            }
            // FILE_URL is the file's stage file URL, as BUILD_STAGE_FILE_URL(@stage, RELATIVE_PATH) spells it.
            rows.add(new Row(Arrays.asList(relative, size, modified, digest, digest,
                executor.stageFileUrl(location, relative))));
        }
        return rows;
    }

    /** The MD5 of a file's bytes, in lower-case hex. */
    private static String md5Of(final Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
        final byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(buffer);
            while (read >= 0) {
                digest.update(buffer, 0, read);
                read = in.read(buffer);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** A DIRECTORY listing's column, its type as the account declares it. */
    private static TableColumn directoryColumn(final String name, final DataType type) {
        final TableColumn column = new TableColumn(name, type, true, null, false, false, false);
        column.setStaticallyTyped(true);
        return column;
    }

    /** The character set a CSV format's ENCODING names, UTF-8 when it names none. */
    private static Charset csvCharset(final String encoding) {
        final Charset charset = encoding == null ? null : StagedFileIo.charsetFor(encoding);
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    /**
     * A staged file as a refusal names it from the stage's storage: {@code stages/<the stage's id>/<file>}, the id
     * fixed for the stage's directory (live-verified shape).
     */
    private static String storedName(final StagePrefix staged, final String relativeName) {
        final String root = String.valueOf(staged.getRoot());
        return "stages/" + UUID.nameUUIDFromBytes(root.getBytes(StandardCharsets.UTF_8)) + "/" + relativeName;
    }

    /** A stage reference whose stage has a local directory behind it; any other cannot be read. */
    private static StagePrefix requireRoot(final StagePrefix staged, final String location) {
        if (staged.getRoot() == null) {
            throw new RuntimeException("Stage query: cannot resolve stage location " + location);
        }
        return staged;
    }
}
