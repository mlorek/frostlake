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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes the COPY subsystem — the stage-file load ({@code COPY INTO <table>}) and unload
 * ({@code COPY INTO <stage>}) paths, VALIDATION_MODE, COPY transformations, and the file/format dispatch —
 * on behalf of {@link QueryExecutor}. Extracted from the executor to keep its file I/O and COPY-local state
 * (load history, pipe-REFRESH filters) in one collaborator. The executor holds one instance and forwards its
 * COPY entry points here; shared stage helpers (base-dir resolution, stage-ref encoding, file listing) stay
 * on the executor and are called back through it.
 */
public final class CopyCommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CopyCommandExecutor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QueryExecutor executor;

    // COPY load history per fully-qualified table: keys of files already loaded, so a re-COPY / pipe REFRESH
    // skips them unless FORCE = TRUE (matching Snowflake). In-memory only (not persisted).
    private final Map<String, Set<String>> copyLoadHistory = new HashMap<>();

    // ALTER PIPE … REFRESH [PREFIX=…] [MODIFIED_AFTER=…] file filters. Set ONLY for the duration of the
    // pipe's COPY execution (executeCopyRefresh) and consulted by the COPY-into-table load loop; a plain
    // COPY leaves them null. Not thread-safe by design — a REFRESH is DDL and holds the engine write lock,
    // so no concurrent COPY observes them.
    private String copyRefreshPrefix;
    private Instant copyRefreshModifiedAfter;

    public CopyCommandExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    public Object executeCopyIntoFromContext(final FrostlakeParser.CopyIntoStatementContext ctx) {
        try {
            // Get target (table or stage)
            String targetName = null;
            boolean isLoadIntoTable = false;

            if (ctx.qualifiedName() != null) {
                // COPY INTO table (loading data)
                targetName = ParseTreeText.getQualifiedName(ctx.qualifiedName());
                isLoadIntoTable = true;
            } else if (ctx.stageRef() != null) {
                // COPY INTO @stage | @~ | @%table [/path] (unloading data to a stage)
                targetName = executor.stageRefToLocation(ctx.stageRef());
                isLoadIntoTable = false;
            } else if (ctx.STRING_LITERAL() != null) {
                // COPY INTO 'protocol://bucket/path' (unloading data to an external location)
                targetName = ParseTreeText.extractStringLiteral(ctx.STRING_LITERAL());
                isLoadIntoTable = false;
            }

            if (isLoadIntoTable) {
                // COPY INTO table FROM stage/location
                return executeCopyIntoTable(ctx, targetName);
            } else {
                // COPY INTO stage FROM table/query
                return executeCopyIntoStage(ctx, targetName);
            }

        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute COPY INTO: " + e.getMessage(), e);
        }
    }

    private Object executeCopyIntoTable(final FrostlakeParser.CopyIntoStatementContext ctx, final String tableName) {
        logger.info("Executing COPY INTO table: {}", tableName);

        // Resolve the table
        Table table = executor.getCatalog().resolveTable(tableName);
        String fullyQualifiedTableName = executor.getFullyQualifiedTableName(tableName);

        // Parse options
        String fromLocation = null;
        String fileFormat = "CSV";
        String pattern = null;
        String validationMode = null;
        String onError = "ABORT_STATEMENT";
        boolean force = false;
        List<String> files = null;
        List<String> columns = null;
        String fieldDelimiter = ",";
        int skipHeader = 0;
        String enclosedBy = null;
        boolean trimSpace = false;
        List<String> nullIf = null;
        FrostlakeParser.CopyTransformationContext transformation = null;

        for (final FrostlakeParser.CopyIntoTableClauseContext clause : ctx.copyIntoTableClause()) {
            if (clause.FROM() != null) {
                // Get source location
                FrostlakeParser.CopySourceContext sourceCtx = clause.copySource();
                if (sourceCtx.stageRef() != null) {
                    // FROM @stage | @~ | @%table [/path]
                    fromLocation = executor.stageRefToLocation(sourceCtx.stageRef());
                } else if (sourceCtx.STRING_LITERAL() != null) {
                    // FROM 's3://bucket/path/'
                    fromLocation = ParseTreeText.extractStringLiteral(sourceCtx.STRING_LITERAL());
                } else if (sourceCtx.copyTransformation() != null) {
                    // FROM (SELECT $1, $2, … FROM @stage) — column transformation over staged file fields.
                    transformation = sourceCtx.copyTransformation();
                    fromLocation = executor.stageRefToLocation(transformation.stageRef());
                }
            } else if (clause.FILE_FORMAT() != null) {
                // Parse FILE_FORMAT options
                if (clause.copyFormatOptions() != null) {
                    for (final FrostlakeParser.CopyFormatOptionContext option : clause.copyFormatOptions().copyFormatOption()) {
                        if (option.TYPE() != null) {
                            fileFormat = copyOptValue(option.copyOptionValue()).toUpperCase();
                        } else if (option.FIELD_DELIMITER() != null) {
                            fieldDelimiter = copyOptValue(option.copyOptionValue());
                        } else if (option.SKIP_HEADER() != null) {
                            skipHeader = Integer.parseInt(option.INTEGER_LITERAL().getText());
                        } else if (option.identifier() != null && option.copyOptionValue() != null
                                && "FIELD_OPTIONALLY_ENCLOSED_BY".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            enclosedBy = copyOptValue(option.copyOptionValue());
                        } else if (option.identifier() != null && option.copyOptionValue() != null
                                && "TRIM_SPACE".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            trimSpace = "TRUE".equalsIgnoreCase(copyOptValue(option.copyOptionValue()));
                        } else if (option.identifier() != null
                                && "NULL_IF".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            nullIf = copyNullIfValues(option);
                        } else if (option.identifier() != null && option.copyOptionValue() != null
                                && "FORMAT_NAME".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            // FILE_FORMAT = (FORMAT_NAME = 'ff') — resolve the named file format and adopt its options.
                            final FileFormat named = resolveFileFormat(copyOptValue(option.copyOptionValue()));
                            if (named != null) {
                                fileFormat = named.getType();
                                if (named.getOption("FIELD_DELIMITER") != null) {
                                    fieldDelimiter = named.getOption("FIELD_DELIMITER");
                                }
                                if (named.getOption("SKIP_HEADER") != null) {
                                    skipHeader = Integer.parseInt(named.getOption("SKIP_HEADER"));
                                }
                                if (named.getOption("FIELD_OPTIONALLY_ENCLOSED_BY") != null) {
                                    enclosedBy = named.getOption("FIELD_OPTIONALLY_ENCLOSED_BY");
                                }
                                if (named.getOption("TRIM_SPACE") != null) {
                                    trimSpace = "TRUE".equalsIgnoreCase(named.getOption("TRIM_SPACE"));
                                }
                                if (named.getOption("NULL_IF") != null && !named.getOption("NULL_IF").isEmpty()) {
                                    // Stored comma-joined by CREATE FILE FORMAT (see CreateInfrastructureHandler).
                                    nullIf = Arrays.asList(named.getOption("NULL_IF").split(","));
                                }
                            }
                        }
                    }
                }
            } else if (clause.PATTERN() != null) {
                pattern = ParseTreeText.extractStringLiteral(clause.STRING_LITERAL());
            } else if (clause.VALIDATION_MODE() != null) {
                validationMode = copyOptValue(clause.copyOptionValue());
            } else if (clause.ON_ERROR() != null) {
                onError = copyOptValue(clause.copyOptionValue());
            } else if (clause.FORCE() != null) {
                force = clause.booleanValue().TRUE() != null;
            } else if (clause.FILES() != null) {
                files = new ArrayList<>();
                for (final TerminalNode fileNode : clause.stringLiteralList().STRING_LITERAL()) {
                    files.add(ParseTreeText.extractStringLiteral(fileNode));
                }
            } else if (clause.LPAREN() != null && clause.identifierList() != null) {
                // Column mapping
                columns = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx : clause.identifierList().identifier()) {
                    columns.add(ParseTreeText.getIdentifier(idCtx));
                }
            }
        }

        logger.info("COPY INTO {} FROM {} FILE_FORMAT={} PATTERN={} ON_ERROR={} FORCE={}",
                tableName, fromLocation, fileFormat, pattern, onError, force);

        final List<ResultSetColumn> resultColumns = Arrays.asList(
            new ResultSetColumn("file", StringType.VARCHAR),
            new ResultSetColumn("status", StringType.VARCHAR),
            new ResultSetColumn("rows_parsed", NumericType.INTEGER),
            new ResultSetColumn("rows_loaded", NumericType.INTEGER),
            new ResultSetColumn("error_limit", NumericType.INTEGER),
            new ResultSetColumn("errors_seen", NumericType.INTEGER)
        );
        final List<Row> resultRows = new ArrayList<>();

        // Resolve the local directory backing the FROM location (@stage / s3:// / file://) and load CSV or
        // JSON files from it.
        final Path baseDir = executor.resolveCopyBaseDir(fromLocation);
        final boolean csv = fileFormat == null || "CSV".equals(fileFormat);
        // Record-based formats (JSON, XML, …) read through a StageFileReader; CSV keeps its positional path.
        final StageFileReader recordReader = StageReaderFactory.forType(fileFormat);
        // A format this engine cannot parse must fail loudly — silently "succeeding" while loading
        // nothing reads as a completed load. Avro/Parquet/ORC ship in the optional frostlake-formats
        // module (a StageFileReader SPI), so their absence gets a pointer rather than a bare rejection.
        if (fileFormat != null && !csv && recordReader == null) {
            final String upperType = fileFormat.toUpperCase();
            final boolean optionalFormat = upperType.equals("AVRO") || upperType.equals("PARQUET")
                || upperType.equals("ORC");
            final String hint = optionalFormat
                ? " — add the frostlake-formats module to the classpath to enable it"
                : "";
            throw new RuntimeException("COPY INTO: file format type '" + fileFormat
                + "' is not supported (supported: " + StageReaderFactory.supportedTypesDisplay() + ")" + hint);
        }
        if (baseDir != null && Files.isDirectory(baseDir) && (csv || recordReader != null)) {
            final List<TableColumn> tableCols = table.getColumns();
            final int[] fieldToCol = buildCopyColumnMapping(tableCols, columns);
            // COPY transformation setup: a synthetic COLUMN1..N table lets $1, $2, … resolve to staged fields.
            final List<FrostlakeParser.CopyTransformItemContext> transformItems =
                transformation != null ? transformation.copyTransformItem() : null;
            final int transformSlots = transformItems != null ? maxPositionalRef(transformItems) : 0;
            final ExpressionEvaluator transformEval = transformItems != null ? buildStageFieldEvaluator(transformSlots) : null;
            final char delimiter = (fieldDelimiter == null || fieldDelimiter.isEmpty()) ? ',' : fieldDelimiter.charAt(0);
            final Character enclosure = (enclosedBy == null || enclosedBy.isEmpty() || "NONE".equalsIgnoreCase(enclosedBy))
                ? null : enclosedBy.charAt(0);
            // Default ON_ERROR = ABORT_STATEMENT fails the whole COPY on any bad row; CONTINUE / SKIP_* load the good rows.
            final boolean abortOnError = !"CONTINUE".equalsIgnoreCase(onError) && !onError.toUpperCase().startsWith("SKIP");

            // VALIDATION_MODE: validate the staged files WITHOUT loading anything (Snowflake
            // semantics) — RETURN_ERRORS / RETURN_ALL_ERRORS list the rows that would fail;
            // RETURN_<N>_ROWS returns the first N rows exactly as they would load.
            if (validationMode != null) {
                return validateCopyFiles(validationMode, table, tableCols, fieldToCol,
                    transformItems, transformEval, transformSlots,
                    baseDir, pattern, files, recordReader, skipHeader, delimiter, enclosure, trimSpace, nullIf);
            }

            Set<String> loadedKeys = copyLoadHistory.get(fullyQualifiedTableName);
            if (loadedKeys == null) {
                loadedKeys = new HashSet<>();
                copyLoadHistory.put(fullyQualifiedTableName, loadedKeys);
            }

            // Rows are buffered and inserted only after every file is parsed + validated, so an
            // ABORT_STATEMENT failure leaves the table unchanged (atomic), matching Snowflake.
            final List<Row> pending = new ArrayList<>();
            for (final Path file : executor.listCopyFiles(baseDir, pattern, files)) {
                final String fileName = file.getFileName().toString();
                // ALTER PIPE … REFRESH PREFIX / MODIFIED_AFTER: narrow the loaded set to matching files
                // (these filters are set only for the duration of a pipe REFRESH; a plain COPY sees none).
                if (!passesRefreshFilters(file, baseDir)) {
                    continue;
                }
                final String fileKey = copyFileKey(file);
                // Snowflake skips already-loaded files unless FORCE = TRUE; keeps a re-COPY / pipe REFRESH idempotent.
                if (!force && loadedKeys.contains(fileKey)) {
                    resultRows.add(new Row(Arrays.asList(fileName, "LOAD_SKIPPED", 0, 0, 0, 0)));
                    continue;
                }
                final List<Row> fileRows = new ArrayList<>();
                int parsed = 0;
                int errors = 0;
                try {
                    if (recordReader != null) {
                        final List<JsonNode> records = recordReader.readRecords(file);
                        for (final JsonNode record : records) {
                            parsed++;
                            try {
                                final Row row = transformItems != null
                                    ? buildJsonTransformedRow(transformItems, tableCols.size(), fieldToCol, record, transformEval, transformSlots)
                                    : buildJsonRow(tableCols, record);
                                executor.enforceColumnConstraints(table, row);
                                fileRows.add(row);
                            } catch (final RuntimeException rowError) {
                                errors++;
                                if (abortOnError) {
                                    throw new RuntimeException("COPY INTO " + tableName + " failed on file "
                                        + fileName + ": " + rowError.getMessage(), rowError);
                                }
                            }
                        }
                    } else {
                        final List<String> lines = Files.readAllLines(file);
                        for (int ln = skipHeader; ln < lines.size(); ln++) {
                            final String line = lines.get(ln);
                            if (line.isEmpty()) {
                                continue;
                            }
                            parsed++;
                            try {
                                final List<String> rowFields = parseCsvLine(line, delimiter, enclosure);
                                applyCsvFieldOptions(rowFields, trimSpace, nullIf);
                                final Row row = transformItems != null
                                    ? buildTransformedRow(transformItems, tableCols.size(), fieldToCol, rowFields, transformEval, transformSlots)
                                    : buildCopyRow(tableCols.size(), fieldToCol, rowFields);
                                executor.enforceColumnConstraints(table, row);
                                fileRows.add(row);
                            } catch (final RuntimeException rowError) {
                                errors++;
                                if (abortOnError) {
                                    throw new RuntimeException("COPY INTO " + tableName + " failed on file "
                                        + fileName + " line " + (ln + 1) + ": " + rowError.getMessage(), rowError);
                                }
                            }
                        }
                    }
                } catch (final IOException io) {
                    throw new RuntimeException("COPY INTO failed reading file " + fileName + ": " + io.getMessage(), io);
                }
                pending.addAll(fileRows);
                loadedKeys.add(fileKey);
                resultRows.add(new Row(Arrays.asList(fileName,
                    errors == 0 ? "LOADED" : "PARTIALLY_LOADED", parsed, fileRows.size(), 0, errors)));
            }

            for (final Row row : pending) {
                executor.getStorageEngine().getTableStorage(fullyQualifiedTableName).insert(row);
            }
        }

        logger.info("COPY INTO table completed: {} file result(s)", resultRows.size());
        return new ResultSet(resultColumns, resultRows);
    }

    /**
     * COPY … VALIDATION_MODE: parse and validate the staged files WITHOUT loading any rows or
     * recording load history. RETURN_&lt;N&gt;_ROWS yields the first N rows exactly as they would
     * load (failing on the first bad record); RETURN_ERRORS / RETURN_ALL_ERRORS yield one row per
     * would-be-rejected record, with an empty result when every record is clean.
     */
    private ResultSet validateCopyFiles(final String validationMode, final Table table,
            final List<TableColumn> tableCols, final int[] fieldToCol,
            final List<FrostlakeParser.CopyTransformItemContext> transformItems,
            final ExpressionEvaluator transformEval, final int transformSlots,
            final Path baseDir, final String pattern, final List<String> files,
            final StageFileReader recordReader, final int skipHeader, final char delimiter, final Character enclosure,
            final boolean trimSpace, final List<String> nullIf) {
        final String mode = validationMode.toUpperCase().replace("'", "").trim();
        final Matcher nRowsMatcher = Pattern.compile("RETURN_(\\d+)_ROWS").matcher(mode);
        final int returnRows = nRowsMatcher.matches() ? Integer.parseInt(nRowsMatcher.group(1)) : -1;

        if (returnRows >= 0) {
            final List<ResultSetColumn> columns = new ArrayList<>();
            for (final TableColumn col : tableCols) {
                columns.add(new ResultSetColumn(col.getName(), col.getDataType()));
            }
            final List<Row> rows = new ArrayList<>();
            for (final Path file : executor.listCopyFiles(baseDir, pattern, files)) {
                for (final Row row : parseCopyFileRows(file, table, tableCols, fieldToCol,
                        transformItems, transformEval, transformSlots,
                        recordReader, skipHeader, delimiter, enclosure, trimSpace, nullIf, null)) {
                    rows.add(row);
                    if (rows.size() >= returnRows) {
                        return new ResultSet(columns, rows);
                    }
                }
            }
            return new ResultSet(columns, rows);
        }

        // RETURN_ERRORS / RETURN_ALL_ERRORS
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("ERROR", StringType.VARCHAR),
            new ResultSetColumn("FILE", StringType.VARCHAR),
            new ResultSetColumn("LINE", NumericType.INTEGER),
            new ResultSetColumn("REJECTED_RECORD", StringType.VARCHAR));
        final List<Row> errorRows = new ArrayList<>();
        for (final Path file : executor.listCopyFiles(baseDir, pattern, files)) {
            parseCopyFileRows(file, table, tableCols, fieldToCol, transformItems, transformEval,
                transformSlots, recordReader, skipHeader, delimiter, enclosure, trimSpace, nullIf, errorRows);
        }
        return new ResultSet(columns, errorRows);
    }

    /**
     * Parse one staged file into table rows. With {@code errorCollector} null, a bad record throws
     * (RETURN_N_ROWS semantics); otherwise each bad record appends an (ERROR, FILE, LINE,
     * REJECTED_RECORD) row to the collector and parsing continues.
     */
    private List<Row> parseCopyFileRows(final Path file, final Table table,
            final List<TableColumn> tableCols, final int[] fieldToCol,
            final List<FrostlakeParser.CopyTransformItemContext> transformItems,
            final ExpressionEvaluator transformEval, final int transformSlots,
            final StageFileReader recordReader, final int skipHeader, final char delimiter, final Character enclosure,
            final boolean trimSpace, final List<String> nullIf, final List<Row> errorCollector) {
        final String fileName = file.getFileName().toString();
        final List<Row> rows = new ArrayList<>();
        try {
            if (recordReader != null) {
                int recordNumber = 0;
                for (final JsonNode record : recordReader.readRecords(file)) {
                    recordNumber++;
                    try {
                        final Row row = transformItems != null
                            ? buildJsonTransformedRow(transformItems, tableCols.size(), fieldToCol, record, transformEval, transformSlots)
                            : buildJsonRow(tableCols, record);
                        executor.enforceColumnConstraints(table, row);
                        rows.add(row);
                    } catch (final RuntimeException rowError) {
                        if (errorCollector == null) {
                            throw new RuntimeException("COPY validation failed on file " + fileName
                                + " record " + recordNumber + ": " + rowError.getMessage(), rowError);
                        }
                        errorCollector.add(new Row(Arrays.asList(
                            rowError.getMessage(), fileName, recordNumber, record.toString())));
                    }
                }
            } else {
                final List<String> lines = Files.readAllLines(file);
                for (int ln = skipHeader; ln < lines.size(); ln++) {
                    final String line = lines.get(ln);
                    if (line.isEmpty()) {
                        continue;
                    }
                    try {
                        final List<String> rowFields = parseCsvLine(line, delimiter, enclosure);
                        applyCsvFieldOptions(rowFields, trimSpace, nullIf);
                        final Row row = transformItems != null
                            ? buildTransformedRow(transformItems, tableCols.size(), fieldToCol, rowFields, transformEval, transformSlots)
                            : buildCopyRow(tableCols.size(), fieldToCol, rowFields);
                        executor.enforceColumnConstraints(table, row);
                        rows.add(row);
                    } catch (final RuntimeException rowError) {
                        if (errorCollector == null) {
                            throw new RuntimeException("COPY validation failed on file " + fileName
                                + " line " + (ln + 1) + ": " + rowError.getMessage(), rowError);
                        }
                        errorCollector.add(new Row(Arrays.asList(
                            rowError.getMessage(), fileName, ln + 1, line)));
                    }
                }
            }
        } catch (final IOException io) {
            throw new RuntimeException("COPY validation failed reading file " + fileName + ": " + io.getMessage(), io);
        }
        return rows;
    }

    /**
     * ALTER PIPE … REFRESH: run the pipe's stored COPY, but restrict the files it loads to those whose
     * stage-relative path starts with {@code prefix} and/or whose last-modified time is strictly after
     * {@code modifiedAfter} (matching Snowflake's REFRESH PREFIX / MODIFIED_AFTER). Either filter may be
     * null (absent). The filters are scoped to this single COPY and cleared afterwards.
     */
    public Object executeCopyRefresh(final String copyStatement, final String prefix, final String modifiedAfter) {
        this.copyRefreshPrefix = prefix;
        this.copyRefreshModifiedAfter = modifiedAfter != null ? parseRefreshTimestamp(modifiedAfter) : null;
        try {
            return executor.execute(copyStatement);
        } finally {
            this.copyRefreshPrefix = null;
            this.copyRefreshModifiedAfter = null;
        }
    }

    /** Whether a staged file passes the active ALTER PIPE … REFRESH filters (always true when none are set). */
    private boolean passesRefreshFilters(final Path file, final Path baseDir) {
        if (copyRefreshPrefix != null) {
            final String relative = baseDir.relativize(file).toString().replace('\\', '/');
            if (!relative.startsWith(copyRefreshPrefix)) {
                return false;
            }
        }
        if (copyRefreshModifiedAfter != null) {
            try {
                if (!Files.getLastModifiedTime(file).toInstant().isAfter(copyRefreshModifiedAfter)) {
                    return false;
                }
            } catch (final IOException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse an ALTER PIPE … REFRESH MODIFIED_AFTER timestamp to an {@link Instant}. Accepts an ISO-8601
     * instant ({@code 2023-01-01T00:00:00Z}) or offset date-time ({@code 2023-01-01T00:00:00+00:00}); a
     * plain {@code yyyy-MM-dd HH:mm:ss} (space or 'T' separator, no zone) is read as UTC.
     */
    private static Instant parseRefreshTimestamp(final String ts) {
        final String trimmed = ts.trim();
        try {
            return Instant.parse(trimmed);
        } catch (final DateTimeParseException ignored) {
            // not a bare instant — try the other forms
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (final DateTimeParseException ignored) {
            // not an offset date-time — try a zoneless local date-time as UTC
        }
        try {
            return LocalDateTime.parse(trimmed.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (final DateTimeParseException e) {
            throw new RuntimeException("Invalid MODIFIED_AFTER timestamp (expected ISO-8601): " + ts, e);
        }
    }

    /** The string value of a copy option: quotes stripped for a STRING_LITERAL, else the literal token text (CSV, GZIP, CONTINUE, …). */
    private String copyOptValue(final FrostlakeParser.CopyOptionValueContext v) {
        if (v == null) {
            return null;
        }
        if (v.STRING_LITERAL() != null) {
            return ParseTreeText.extractStringLiteral(v.STRING_LITERAL());
        }
        return v.getText();
    }

    /** Resolve a named file format (FORMAT_NAME) from the current schema; null if it doesn't exist. */
    private FileFormat resolveFileFormat(final String name) {
        try {
            return executor.getCatalog().getFileFormat(name);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Map each CSV field position to a target-table column index (explicit column list, else positional). */
    private int[] buildCopyColumnMapping(final List<TableColumn> tableCols, final List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            final int[] positional = new int[tableCols.size()];
            for (int i = 0; i < positional.length; i++) {
                positional[i] = i;
            }
            return positional;
        }
        final int[] mapped = new int[columns.size()];
        for (int f = 0; f < columns.size(); f++) {
            mapped[f] = -1;
            for (int c = 0; c < tableCols.size(); c++) {
                if (tableCols.get(c).getName().equalsIgnoreCase(columns.get(f))) {
                    mapped[f] = c;
                    break;
                }
            }
            if (mapped[f] < 0) {
                throw new RuntimeException("COPY INTO column not found in target table: " + columns.get(f));
            }
        }
        return mapped;
    }

    /** NULL_IF values from a COPY FILE_FORMAT option: the parenthesized string list, or a single scalar value. */
    private List<String> copyNullIfValues(final FrostlakeParser.CopyFormatOptionContext option) {
        final List<String> values = new ArrayList<>();
        if (option.stringLiteralList() != null) {
            for (final TerminalNode node : option.stringLiteralList().STRING_LITERAL()) {
                values.add(ParseTreeText.extractStringLiteral(node));
            }
        } else if (option.copyOptionValue() != null) {
            values.add(copyOptValue(option.copyOptionValue()));
        }
        return values;
    }

    /**
     * Apply TRIM_SPACE and NULL_IF to a parsed CSV row in place: trim leading/trailing whitespace from each
     * field (when enabled), then null out any field whose (trimmed) value matches a NULL_IF token. A null in
     * the field list is loaded as SQL NULL by {@code buildCopyRow} / {@code inferStageFieldValue}.
     */
    private static void applyCsvFieldOptions(final List<String> fields, final boolean trimSpace,
            final List<String> nullIf) {
        if (!trimSpace && (nullIf == null || nullIf.isEmpty())) {
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            String value = fields.get(i);
            if (value == null) {
                continue;
            }
            if (trimSpace) {
                value = value.trim();
            }
            if (nullIf != null && nullIf.contains(value)) {
                value = null;
            }
            fields.set(i, value);
        }
    }

    private static Row buildCopyRow(final int numCols, final int[] fieldToCol, final List<String> fields) {
        final List<Object> values = new ArrayList<>(numCols);
        for (int i = 0; i < numCols; i++) {
            values.add(null);
        }
        for (int f = 0; f < fields.size() && f < fieldToCol.length; f++) {
            final int colIdx = fieldToCol[f];
            if (colIdx >= 0 && colIdx < numCols) {
                final String value = fields.get(f);
                values.set(colIdx, (value == null || value.isEmpty()) ? null : value);
            }
        }
        return new Row(values);
    }

    /**
     * Infer a staged CSV field's value: numeric literals become numbers so $n arithmetic (e.g. {@code $3 * 2})
     * works, anything else stays a string for text functions (e.g. {@code UPPER($2)}); empty → NULL. The final
     * value is still coerced to the target column type on insert.
     */
    private static Object inferStageFieldValue(final String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(field);
        } catch (final NumberFormatException notLong) {
            try {
                return Double.valueOf(field);
            } catch (final NumberFormatException notDouble) {
                return field;
            }
        }
    }

    /** Highest positional reference ($1, $2, …) across a transformation's projection expressions (0 if none). */
    private int maxPositionalRef(final List<FrostlakeParser.CopyTransformItemContext> items) {
        int max = 0;
        final Pattern p = Pattern.compile("\\$(\\d+)");
        for (final FrostlakeParser.CopyTransformItemContext item : items) {
            final Matcher m = p.matcher(item.expression().getText());
            while (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max;
    }

    /** Evaluator over a synthetic COLUMN1..COLUMN&lt;slots&gt; table, so COPY transformation expressions can resolve $1, $2, …. */
    private ExpressionEvaluator buildStageFieldEvaluator(final int slots) {
        final List<TableColumn> slotCols = new ArrayList<>();
        for (int i = 1; i <= slots; i++) {
            slotCols.add(new TableColumn("COLUMN" + i, StringType.VARCHAR, true, null, false, false, false));
        }
        final ExpressionEvaluator evaluator =
            new ExpressionEvaluator(new Table("COPY_STAGE", slotCols, false), executor.getFunctionRegistry(), executor.getCatalog(), executor);
        evaluator.setOuterLateralContext(executor.getProceduralVariablesAsContext());
        return evaluator;
    }

    /**
     * CSV transformation row: bind the staged file's fields positionally to $1, $2, … (missing fields → NULL),
     * then evaluate the projection. Projected values map to target columns via the explicit column list, else
     * positionally.
     */
    private Row buildTransformedRow(final List<FrostlakeParser.CopyTransformItemContext> items, final int numCols,
            final int[] itemToCol, final List<String> rowFields, final ExpressionEvaluator transformEval, final int slots) {
        final List<Object> slotValues = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            slotValues.add(i < rowFields.size() ? inferStageFieldValue(rowFields.get(i)) : null);
        }
        return projectTransform(items, numCols, itemToCol, new Row(slotValues), transformEval);
    }

    /**
     * JSON transformation row: bind the whole parsed document to $1 — its text is navigable by path, so
     * {@code $1:id} / {@code $1:addr.city} resolve — then evaluate the projection. Higher $n are unused for JSON.
     */
    private Row buildJsonTransformedRow(final List<FrostlakeParser.CopyTransformItemContext> items, final int numCols,
            final int[] itemToCol, final JsonNode record, final ExpressionEvaluator transformEval, final int slots) {
        final List<Object> slotValues = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            slotValues.add(i == 0 ? record.toString() : null);
        }
        return projectTransform(items, numCols, itemToCol, new Row(slotValues), transformEval);
    }

    /** Evaluate each transformation expression against the bound $-row, placing results into the mapped target columns. */
    private Row projectTransform(final List<FrostlakeParser.CopyTransformItemContext> items, final int numCols,
            final int[] itemToCol, final Row stagedRow, final ExpressionEvaluator transformEval) {
        final List<Object> values = new ArrayList<>(numCols);
        for (int i = 0; i < numCols; i++) {
            values.add(null);
        }
        for (int i = 0; i < items.size() && i < itemToCol.length; i++) {
            final int colIdx = itemToCol[i];
            if (colIdx >= 0 && colIdx < numCols) {
                values.set(colIdx, transformEval.evaluate(ParseTreeText.getOriginalText(items.get(i).expression()), stagedRow));
            }
        }
        return new Row(values);
    }

    /** A file's load-history identity: name + size + last-modified (re-staging a changed file reloads it). */
    private static String copyFileKey(final Path file) {
        final File f = file.toFile();
        return f.getName() + "|" + f.length() + "|" + f.lastModified();
    }

    /** Split a CSV line on {@code delimiter}, honoring an optional enclosing char (with {@code ""} escaping). */
    /** Split one CSV line on {@code delimiter}, honoring an optional enclosure character. Public so the
     *  stage-query path (SELECT ... FROM @stage) reads fields exactly the way COPY INTO does. */
    public static List<String> parseCsvLine(final String line, final char delimiter, final Character enclosure) {
        final List<String> fields = new ArrayList<>();
        final StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (enclosure != null && c == enclosure) {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == enclosure) {
                    cur.append(enclosure);   // escaped enclosure ("")
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    /** Map a JSON record to a row: a single (VARIANT) column gets the whole element; otherwise object fields map to columns by name. */
    private static Row buildJsonRow(final List<TableColumn> tableCols, final JsonNode record) {
        final List<Object> values = new ArrayList<>(tableCols.size());
        if (tableCols.size() == 1) {
            values.add(jsonNodeToValue(record));
            return new Row(values);
        }
        for (final TableColumn col : tableCols) {
            values.add(jsonNodeToValue(jsonField(record, col.getName())));
        }
        return new Row(values);
    }

    /** Look up a JSON object field by column name, tolerating case (JSON keys are case-sensitive, SQL columns are not). */
    private static JsonNode jsonField(final JsonNode record, final String columnName) {
        if (record == null || !record.isObject()) {
            return null;
        }
        JsonNode value = record.get(columnName);
        if (value == null) {
            value = record.get(columnName.toLowerCase());
        }
        if (value == null) {
            value = record.get(columnName.toUpperCase());
        }
        return value;
    }

    /** A JSON scalar becomes its text (coerced later by the column type); an object/array becomes its compact JSON string (VARIANT). */
    private static Object jsonNodeToValue(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private Object executeCopyIntoStage(final FrostlakeParser.CopyIntoStatementContext ctx, final String location) {
        logger.info("Executing COPY INTO location: {}", location);

        String fromTable = null;
        FrostlakeParser.SelectStatementContext fromSelect = null;
        String fileFormat = "CSV";
        boolean header = false;
        FrostlakeParser.ExpressionContext partitionExpr = null;

        for (final FrostlakeParser.CopyIntoStageClauseContext clause : ctx.copyIntoStageClause()) {
            if (clause.FROM() != null) {
                final FrostlakeParser.CopyTableSourceContext sourceCtx = clause.copyTableSource();
                if (sourceCtx.qualifiedName() != null) {
                    fromTable = ParseTreeText.getQualifiedName(sourceCtx.qualifiedName());
                } else if (sourceCtx.selectStatement() != null) {
                    fromSelect = sourceCtx.selectStatement();
                }
            } else if (clause.FILE_FORMAT() != null && clause.copyFormatOptions() != null) {
                for (final FrostlakeParser.CopyFormatOptionContext opt : clause.copyFormatOptions().copyFormatOption()) {
                    if (opt.TYPE() != null) {
                        fileFormat = copyOptValue(opt.copyOptionValue()).toUpperCase();
                    }
                }
            } else if (clause.PARTITION() != null) {
                partitionExpr = clause.expression();
            } else if (clause.HEADER() != null) {
                // Bare HEADER (no "= TRUE/FALSE") means HEADER = TRUE, per Snowflake.
                header = clause.booleanValue() == null || clause.booleanValue().TRUE() != null;
            }
        }

        // Resolve the data to unload — either a table scan or a query result.
        final ResultSet data;
        if (fromSelect != null) {
            data = executor.executeSelectFromContext(fromSelect);
        } else if (fromTable != null) {
            final Table sourceTable = executor.getCatalog().resolveTable(fromTable);
            final List<ResultSetColumn> cols = new ArrayList<>();
            for (final TableColumn tc : sourceTable.getColumns()) {
                cols.add(new ResultSetColumn(tc.getName(), tc.getDataType()));
            }
            data = new ResultSet(cols, executor.getStorageEngine().getTableStorage(executor.getFullyQualifiedTableName(fromTable)).scan());
        } else {
            throw new RuntimeException("COPY INTO " + location + " requires a FROM clause");
        }

        final boolean json = "JSON".equalsIgnoreCase(fileFormat);
        final int rowsUnloaded = data.getRows().size();

        // Resolve the target directory (file:// stage, mapped s3:// stage, or external location). An
        // unmapped target formats but writes nothing. PARTITION BY splits the rows across per-key files.
        final Path dir = executor.resolveCopyBaseDir(location);
        final int outputBytes = partitionExpr != null
            ? writePartitionedUnload(data, partitionExpr, dir, json, header, location)
            : writeUnloadFile(dir, "data_0_0_0." + (json ? "json" : "csv"),
                json ? formatRowsAsJson(data) : formatRowsAsCsv(data, header), location, rowsUnloaded);

        final List<ResultSetColumn> resultColumns = Arrays.asList(
            new ResultSetColumn("rows_unloaded", NumericType.INTEGER),
            new ResultSetColumn("input_bytes", NumericType.INTEGER),
            new ResultSetColumn("output_bytes", NumericType.INTEGER)
        );
        final List<Row> resultRows = new ArrayList<>();
        resultRows.add(new Row(Arrays.asList(rowsUnloaded, outputBytes, outputBytes)));
        return new ResultSet(resultColumns, resultRows);
    }

    /** Write one unload file's content into {@code dir} (created if needed); returns bytes written, or 0 if the target has no local dir. */
    private int writeUnloadFile(final Path dir, final String fileName, final String content,
            final String location, final int rowsForLog) {
        if (dir == null) {
            logger.info("COPY INTO {}: target has no local directory; {} rows formatted but not written", location, rowsForLog);
            return 0;
        }
        try {
            Files.createDirectories(dir);
            final Path out = dir.resolve(fileName);
            final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(out, bytes);
            logger.info("COPY INTO {}: wrote {} rows ({} bytes) to {}", location, rowsForLog, bytes.length, out);
            return bytes.length;
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write unload file to " + location + ": " + e.getMessage(), e);
        }
    }

    /**
     * PARTITION BY {@code <expr>} unload: evaluate the expression per row and write each distinct value's rows
     * to {@code data_0_0_0.<ext>} under a subdirectory named for that value (Snowflake builds a per-partition
     * directory structure). Returns total bytes written.
     */
    private int writePartitionedUnload(final ResultSet data, final FrostlakeParser.ExpressionContext partitionExpr,
            final Path dir, final boolean json, final boolean header, final String location) {
        // A synthetic table over the unload columns lets the partition expression resolve them by name.
        final List<TableColumn> partCols = new ArrayList<>();
        for (final ResultSetColumn rc : data.getColumns()) {
            partCols.add(new TableColumn(rc.getName(), rc.getDataType(), true, null, false, false, false));
        }
        final Table partTable = new Table("UNLOAD_SRC", partCols, false);
        final ExpressionEvaluator partEval = new ExpressionEvaluator(partTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        partEval.setOuterLateralContext(executor.getProceduralVariablesAsContext());
        final String exprText = ParseTreeText.getOriginalText(partitionExpr);

        // Group rows by partition-key string, preserving first-seen order.
        final Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (final Row row : data.getRows()) {
            final Object key = partEval.evaluate(exprText, row);
            final String keyStr = key == null ? "__NULL__" : key.toString();
            List<Row> bucket = groups.get(keyStr);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(keyStr, bucket);
            }
            bucket.add(row);
        }

        int total = 0;
        final String ext = json ? "json" : "csv";
        for (final Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            final ResultSet part = new ResultSet(data.getColumns(), entry.getValue());
            final String content = json ? formatRowsAsJson(part) : formatRowsAsCsv(part, header);
            final String subDir = sanitizePartitionDir(entry.getKey());
            final Path partDir = dir != null ? dir.resolve(subDir) : null;
            total += writeUnloadFile(partDir, "data_0_0_0." + ext, content, location + "/" + subDir, entry.getValue().size());
        }
        return total;
    }

    /** Make a partition-key value safe to use as a directory name (non-alphanumerics → underscore). */
    private String sanitizePartitionDir(final String key) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '=' ? c : '_');
        }
        return sb.length() == 0 ? "_" : sb.toString();
    }

    /** Format a result set as delimited CSV text (one row per line), optionally with a header line. */
    private String formatRowsAsCsv(final ResultSet data, final boolean header) {
        final StringBuilder sb = new StringBuilder();
        if (header) {
            for (int c = 0; c < data.getColumns().size(); c++) {
                if (c > 0) sb.append(',');
                sb.append(data.getColumns().get(c).getName());
            }
            sb.append('\n');
        }
        for (final Row row : data.getRows()) {
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) sb.append(',');
                sb.append(csvCell(row.getValue(c)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Render a single CSV cell, quoting (and doubling embedded quotes) when it contains a comma, quote, or newline. */
    private String csvCell(final Object v) {
        if (v == null) {
            return "";
        }
        final String s = v.toString();
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /**
     * Format a result set as newline-delimited JSON (one document per row). A single semi-structured
     * column — the Snowflake TYPE=JSON unload contract of exactly one VARIANT/OBJECT/ARRAY column — is
     * written as the raw document itself, not wrapped in an object keyed by the column name. Anything
     * else keeps the wrapped form as a lenient extension (Snowflake rejects multi-column JSON unloads).
     */
    private String formatRowsAsJson(final ResultSet data) {
        final StringBuilder sb = new StringBuilder();
        if (isSingleVariantColumn(data)) {
            for (final Row row : data.getRows()) {
                final Object v = row.getValues().isEmpty() ? null : row.getValue(0);
                sb.append(variantDocumentText(v)).append('\n');
            }
            return sb.toString();
        }
        for (final Row row : data.getRows()) {
            sb.append('{');
            for (int c = 0; c < data.getColumns().size(); c++) {
                if (c > 0) sb.append(',');
                sb.append('"').append(data.getColumns().get(c).getName()).append("\":");
                final Object v = c < row.getValues().size() ? row.getValue(c) : null;
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof Number || v instanceof Boolean) {
                    sb.append(v.toString());
                } else {
                    sb.append('"').append(v.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                }
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    /** True when the unload result is one column whose first non-null value is a JSON document (Map, List, or JSON text). */
    private boolean isSingleVariantColumn(final ResultSet data) {
        if (data.getColumns().size() != 1) {
            return false;
        }
        for (final Row row : data.getRows()) {
            final Object v = row.getValues().isEmpty() ? null : row.getValue(0);
            if (v == null) {
                continue;
            }
            if (v instanceof Map || v instanceof List) {
                return true;
            }
            if (v instanceof String) {
                final String t = ((String) v).trim();
                if (!t.startsWith("{") && !t.startsWith("[")) {
                    return false;
                }
                try {
                    MAPPER.readTree(t);
                    return true;
                } catch (final RuntimeException e) {
                    return false;
                }
            }
            return false;
        }
        return false;
    }

    /** Render one variant value as its raw JSON document text. */
    private String variantDocumentText(final Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String) {
            return ((String) v).trim();
        }
        return MAPPER.writeValueAsString(v);
    }
}
