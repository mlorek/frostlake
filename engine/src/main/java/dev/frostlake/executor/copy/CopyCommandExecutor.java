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

import dev.frostlake.executor.ColumnLengthException;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantValue;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;

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
import java.util.LinkedHashSet;
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
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

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

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    // The summary a COPY INTO <table> returns when it produced no per-file rows at all. Snowflake replaces the
    // whole per-file result with a single column named "status" holding exactly this one row — the trailing
    // period included. Live-verified (see the shape rule on executeCopyIntoTable).
    private static final String NO_FILES_PROCESSED_STATUS = "Copy executed with 0 files processed.";

    // The first_error a LOAD_SKIPPED row carries — the whole reason the file was passed over, reported in
    // the same column a rejected record would use. Live-verified, the trailing period included.
    private static final String ALREADY_LOADED_ERROR = "File was loaded before.";

    // ON_ERROR's SKIP_FILE family — SKIP_FILE, SKIP_FILE_<n> and SKIP_FILE_<n>% — matched against the
    // upper-cased value with its quotes already stripped, so one pattern covers the quoted and unquoted
    // spellings alike. See requireValidOnError and fileErrorLimit.
    private static final Pattern SKIP_FILE_ON_ERROR = Pattern.compile("SKIP_FILE(?:_(\\d+)(%)?)?");

    // VALIDATION_MODE's row-returning family, RETURN_<n>_ROWS — matched against the upper-cased value with
    // its quotes stripped. Everything else (RETURN_ERRORS, RETURN_ALL_ERRORS) lists errors instead.
    private static final Pattern RETURN_N_ROWS_MODE = Pattern.compile("RETURN_(\\d+)_ROWS");

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
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * {@code COPY INTO <table>} — load staged files. The result set has TWO shapes, and which one comes back
     * is decided by whether any per-file row was produced. Live-verified against a real account,
     * every case below observed directly with a control run beside it:
     *
     * <ul>
     *   <li>at least one per-file row → the wide per-file shape, TEN columns: {@code file}, {@code status},
     *       {@code rows_parsed}, {@code rows_loaded}, {@code error_limit}, {@code errors_seen},
     *       {@code first_error}, {@code first_error_line}, {@code first_error_character} and
     *       {@code first_error_column_name} — the first two and the last VARCHAR, the rest NUMBER, in that
     * order (live-verified off the account's own result metadata). A file counts
     *       even when it contributes no data: a zero-byte file and a header-only file both come back
     *       {@code LOADED} with 0 rows parsed and 0 loaded, and a wholly rejected file comes back
     *       {@code LOAD_FAILED} with 0 loaded — so the rule is NOT "rows were loaded";</li>
     *   <li>no per-file row at all → a ONE-column ({@code status VARCHAR}), ONE-row summary holding
     *       {@link #NO_FILES_PROCESSED_STATUS}. Reached by an empty stage, by a PATTERN that matches nothing
     *       on a NON-empty stage, and by a stage whose every file is already in the load history.</li>
     * </ul>
     *
     * A file skipped by the load history is normally left out of the result entirely — a stage holding one
     * already-loaded file answers with the summary, and in a mixed run only the newly loaded file gets a row.
     * The ONE exception: a file the statement named explicitly in {@code FILES = (…)} does get a
     * {@code LOAD_SKIPPED} row. Naming an already-loaded file that way is the only probed case that yields the
     * wide shape with nothing loaded; selecting the very same file by PATTERN yields the summary instead.
     *
     * <p>Naming a file that is NOT on the stage is the sharper form of the same asymmetry: it is an error,
     * not a no-op, whereas a PATTERN matching nothing collapses to the summary as above. See
     * {@link #missingNamedFiles} for the full rule and the ON_ERROR interaction.
     *
     * <p>Two neighbours deliberately do NOT share this rule, each confirmed separately: VALIDATION_MODE keeps
     * its own shapes (the RETURN_ERRORS error columns / the table's own columns for RETURN_<i>n</i>_ROWS) and
     * returns them empty rather than collapsing, and the unload path ({@code COPY INTO @stage}) always answers
     * {@code rows_unloaded / input_bytes / output_bytes}, reporting 0/0/0 for an empty source table.
     */
    private Object executeCopyIntoTable(final FrostlakeParser.CopyIntoStatementContext ctx, final String tableName) {
        logger.info("Executing COPY INTO table: {}", tableName);

        // Resolve the table
        Table table = executor.getCatalog().resolveTable(tableName);
        String fullyQualifiedTableName = executor.getFullyQualifiedTableName(tableName);

        // Parse options
        String fromLocation = null;
        String fileFormat = "CSV";
        String pattern = null;
        String matchByColumnName = null;
        String validationMode = null;
        String onError = "ABORT_STATEMENT";
        boolean force = false;
        boolean purge = false;
        List<String> files = null;
        List<String> columns = null;
        String fieldDelimiter = ",";
        int skipHeader = 0;
        String enclosedBy = null;
        boolean trimSpace = false;
        List<String> nullIf = null;
        // CSV's ERROR_ON_COLUMN_COUNT_MISMATCH, whose Snowflake default is TRUE — a record whose field count
        // differs from the target table's column count is REJECTED, in either direction. See
        // requireCsvColumnCount for the whole rule; FALSE restores the lenient truncate/NULL-pad reshaping.
        boolean errorOnColumnCountMismatch = true;
        // CSV's SKIP_BLANK_LINES, whose Snowflake default is FALSE — a blank line is a one-empty-field record
        // and so a column-count error on any table wider than one column, not a line to pass over.
        boolean skipBlankLines = false;
        // CSV's EMPTY_FIELD_AS_NULL, whose Snowflake default is TRUE — an unenclosed empty field loads as SQL
        // NULL. FALSE loads it as the empty string instead. See applyCsvFieldOptions for the whole rule.
        boolean emptyFieldAsNull = true;
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
                                && "ERROR_ON_COLUMN_COUNT_MISMATCH".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            errorOnColumnCountMismatch = !"FALSE".equalsIgnoreCase(copyOptValue(option.copyOptionValue()));
                        } else if (option.identifier() != null && option.copyOptionValue() != null
                                && "SKIP_BLANK_LINES".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            skipBlankLines = "TRUE".equalsIgnoreCase(copyOptValue(option.copyOptionValue()));
                        } else if (option.identifier() != null && option.copyOptionValue() != null
                                && "EMPTY_FIELD_AS_NULL".equalsIgnoreCase(ParseTreeText.getIdentifier(option.identifier()))) {
                            emptyFieldAsNull = !"FALSE".equalsIgnoreCase(copyOptValue(option.copyOptionValue()));
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
                                if (named.getOption("ERROR_ON_COLUMN_COUNT_MISMATCH") != null) {
                                    errorOnColumnCountMismatch =
                                        !"FALSE".equalsIgnoreCase(named.getOption("ERROR_ON_COLUMN_COUNT_MISMATCH"));
                                }
                                if (named.getOption("SKIP_BLANK_LINES") != null) {
                                    skipBlankLines = "TRUE".equalsIgnoreCase(named.getOption("SKIP_BLANK_LINES"));
                                }
                                if (named.getOption("EMPTY_FIELD_AS_NULL") != null) {
                                    emptyFieldAsNull =
                                        !"FALSE".equalsIgnoreCase(named.getOption("EMPTY_FIELD_AS_NULL"));
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
                onError = requireValidOnError(clause.copyOptionValue());
            } else if (clause.FORCE() != null) {
                force = clause.booleanValue().TRUE() != null;
            } else if (clause.PURGE() != null) {
                purge = clause.booleanValue().TRUE() != null;
            } else if (clause.FILES() != null) {
                files = new ArrayList<>();
                for (final TerminalNode fileNode : clause.stringLiteralList().STRING_LITERAL()) {
                    files.add(ParseTreeText.extractStringLiteral(fileNode));
                }
            } else if (clause.MATCH_BY_COLUMN_NAME() != null) {
                matchByColumnName = copyOptValue(clause.copyOptionValue());
            } else if (clause.LPAREN() != null && clause.identifierList() != null) {
                // Column mapping
                columns = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx : clause.identifierList().identifier()) {
                    columns.add(ParseTreeText.getIdentifier(idCtx));
                }
            }
        }

        // No FROM clause: the source defaults to the target table's OWN stage, @%<table> — the documented
        // local-file load path (PUT a file into @%t, then COPY INTO t). Live-verified on a real account
        //: the no-FROM form loads from the table stage and is otherwise indistinguishable from
        // an explicit FROM @%t — the load-history dedup, FORCE, PATTERN, FILES and an explicit column list
        // all behave identically, and an empty table stage is a zero-row success, not an error. A qualified
        // target reads ITS own stage, not a same-named table in the current schema, so the target name is
        // carried through as written and normalized by the @% resolver.
        if (fromLocation == null) {
            fromLocation = "@%" + tableName;
        }

        // A semi-structured file yields ONE semi-structured value per record, so it can only load into a
        // single VARIANT / OBJECT / ARRAY column — unless MATCH_BY_COLUMN_NAME splits it, or a COPY
        // transformation projects the fields. Live-verified on a real account: COPY INTO a
        // two-column table with FILE_FORMAT = (TYPE = 'JSON') fails "JSON file format can produce one and
        // only one column of type variant, object, or array. Load data into separate columns using the
        // MATCH_BY_COLUMN_NAME copy option or copy with transformation." — PARQUET reports the same.
        rejectMultiColumnSemiStructuredLoad(fileFormat, table, columns, matchByColumnName, transformation);

        logger.info("COPY INTO {} FROM {} FILE_FORMAT={} PATTERN={} ON_ERROR={} FORCE={} PURGE={}",
                tableName, fromLocation, fileFormat, pattern, onError, force, purge);

        final List<ResultSetColumn> resultColumns = Arrays.asList(
            new ResultSetColumn("file", StringType.VARCHAR),
            new ResultSetColumn("status", StringType.VARCHAR),
            new ResultSetColumn("rows_parsed", NumericType.INTEGER),
            new ResultSetColumn("rows_loaded", NumericType.INTEGER),
            new ResultSetColumn("error_limit", NumericType.INTEGER),
            new ResultSetColumn("errors_seen", NumericType.INTEGER),
            new ResultSetColumn("first_error", StringType.VARCHAR),
            new ResultSetColumn("first_error_line", NumericType.INTEGER),
            new ResultSetColumn("first_error_character", NumericType.INTEGER),
            new ResultSetColumn("first_error_column_name", StringType.VARCHAR)
        );
        final List<Row> resultRows = new ArrayList<>();
        // FILES = (…) names its files explicitly, which is what makes a load-history skip visible in the
        // result (see the shape rule above). listCopyFiles already narrows the listing to these names, so
        // every file the load loop sees under a non-empty FILES list was named by the statement.
        final boolean explicitFileList = files != null && !files.isEmpty();
        // ON_ERROR = ABORT_STATEMENT (the default) fails the whole COPY on the first bad record; CONTINUE
        // and the SKIP_FILE family read the file to the end and settle its fate afterwards, per
        // fileErrorLimit. requireValidOnError has already upper-cased the value and rejected any other.
        final boolean abortOnError = "ABORT_STATEMENT".equals(onError);

        // Resolve the local directory backing the FROM location (@stage / s3:// / file://) and load CSV or
        // JSON files from it.
        final Path baseDir = executor.resolveCopyBaseDir(fromLocation);
        // Every name in FILES = (…) that is not on the stage. Settled BEFORE anything is read, so an
        // aborting statement leaves the table untouched (see missingNamedFiles for the whole rule).
        final List<String> missingFiles = missingNamedFiles(baseDir, files);
        if (!missingFiles.isEmpty() && (validationMode != null || abortOnError)) {
            // The two paths that refuse to load rather than report: VALIDATION_MODE = RETURN_<n>_ROWS, and
            // the default ON_ERROR. RETURN_ERRORS is the exception — it reports the miss as a row and is
            // handled in validateCopyFiles.
            if (validationMode == null) {
                throw new RuntimeException(remoteFileNotFound(fromLocation, missingFiles.get(0)));
            }
            if (!isReturnErrorsMode(validationMode)) {
                throw new RuntimeException(remoteFileMissingDetail(fromLocation, missingFiles.get(0)));
            }
        }
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
            final boolean checkColumnCount = csv
                && columnCountChecked(errorOnColumnCountMismatch, columns, matchByColumnName, transformItems);

            // VALIDATION_MODE: validate the staged files WITHOUT loading anything (Snowflake
            // semantics) — RETURN_ERRORS / RETURN_ALL_ERRORS list the rows that would fail;
            // RETURN_<N>_ROWS returns the first N rows exactly as they would load.
            if (validationMode != null) {
                return validateCopyFiles(validationMode, table, tableCols, fieldToCol,
                    transformItems, transformEval, transformSlots,
                    baseDir, pattern, files, recordReader, skipHeader, delimiter, enclosure, trimSpace, nullIf,
                    emptyFieldAsNull, checkColumnCount, skipBlankLines, fromLocation, missingFiles);
            }

            Set<String> loadedKeys = copyLoadHistory.get(fullyQualifiedTableName);
            if (loadedKeys == null) {
                loadedKeys = new HashSet<>();
                copyLoadHistory.put(fullyQualifiedTableName, loadedKeys);
            }

            // Rows are buffered and inserted only after every file is parsed + validated, so an
            // ABORT_STATEMENT failure leaves the table unchanged (atomic), matching Snowflake.
            final List<Row> pending = new ArrayList<>();
            // PURGE = TRUE: the staged files to delete once the load is committed. Only files this
            // statement actually consumed land here — see purgeLoadedFiles for the exact rule.
            final List<Path> purgeCandidates = new ArrayList<>();
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
                    // A skipped file is reported only when the statement named it in FILES = (…); one merely
                    // found by listing the stage (or picked by PATTERN) leaves no trace in the result.
                    // Its counters are NOT zeros: the account reports a NULL error_limit (no budget was ever
                    // applied — nothing was read) beside errors_seen = 1 and the skip itself as the file's
                    // first_error. Live-verified and stable across ON_ERROR (default, CONTINUE,
                    // SKIP_FILE_5) and stage kind (table stage, named stage), with a FORCE = TRUE control
                    // re-loading the same file as LOADED.
                    if (explicitFileList) {
                        resultRows.add(new Row(Arrays.asList(fileName, "LOAD_SKIPPED", 0, 0, null, 1,
                            ALREADY_LOADED_ERROR, null, null, null)));
                    }
                    continue;
                }
                final List<Row> fileRows = new ArrayList<>();
                int parsed = 0;
                int errors = 0;
                // The file's FIRST rejected record — the trailing first_error quartet. Only the first is
                // kept: later rejects raise errors_seen but never overwrite this (see CopyFileError).
                CopyFileError firstError = null;
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
                                if (firstError == null) {
                                    // A record-based format has no line-and-column geometry: the account
                                    // reported the record's 1-based ordinal as the line and left the
                                    // character and column-name null.
                                    firstError = new CopyFileError(copyErrorMessage(rowError), parsed, null, null);
                                }
                                if (abortOnError) {
                                    throw new RuntimeException("COPY INTO " + tableName + " failed on file "
                                        + fileName + ": " + copyErrorMessage(rowError), rowError);
                                }
                            }
                        }
                    } else {
                        final List<String> lines = Files.readAllLines(file);
                        // The record's 1-based ordinal among the ones actually read, which decides WHICH
                        // column-count message a mismatch gets (see requireCsvColumnCount). Distinct from
                        // `parsed`, which also counts a blank line SKIP_BLANK_LINES passed over.
                        int recordNumber = 0;
                        for (int ln = skipHeader; ln < lines.size(); ln++) {
                            final String line = lines.get(ln);
                            parsed++;
                            if (line.isEmpty() && skipBlankLines) {
                                continue;
                            }
                            recordNumber++;
                            Row row = null;
                            try {
                                final List<Boolean> enclosedEmpty = new ArrayList<>();
                                final List<String> rowFields = parseCsvLine(line, delimiter, enclosure, enclosedEmpty);
                                applyCsvFieldOptions(rowFields, trimSpace, nullIf, emptyFieldAsNull, enclosedEmpty);
                                if (checkColumnCount) {
                                    requireCsvColumnCount(rowFields, table, line, ln + 1,
                                        ln == lines.size() - 1, recordNumber == 1, delimiter, enclosure);
                                }
                                row = transformItems != null
                                    ? buildTransformedRow(transformItems, tableCols.size(), fieldToCol, rowFields, transformEval, transformSlots)
                                    : buildCopyRow(tableCols, fieldToCol, rowFields);
                                executor.enforceColumnConstraints(table, row);
                                fileRows.add(row);
                            } catch (final RuntimeException rowError) {
                                errors++;
                                if (firstError == null) {
                                    firstError = describeCsvError(rowError, table, row, line, ln + 1,
                                        fieldToCol, delimiter, enclosure);
                                }
                                if (abortOnError) {
                                    throw new RuntimeException("COPY INTO " + tableName + " failed on file "
                                        + fileName + " line " + (ln + 1) + ": " + copyErrorMessage(rowError), rowError);
                                }
                            }
                        }
                    }
                } catch (final IOException io) {
                    throw new RuntimeException("COPY INTO failed reading file " + fileName + ": " + io.getMessage(), io);
                }
                // ON_ERROR's per-file budget (fileErrorLimit): once a file's rejected-record count reaches it
                // the WHOLE file is dropped, and the records that parsed cleanly go with it — under
                // SKIP_FILE a file with one bad record out of two loads neither.
                final int errorLimit = fileErrorLimit(onError, parsed);
                final boolean fileSkipped = errors >= errorLimit;
                final int rowsLoaded = fileSkipped ? 0 : fileRows.size();
                if (!fileSkipped) {
                    pending.addAll(fileRows);
                }
                final boolean loadFailed = fileLoadFailed(rowsLoaded, errors, fileSkipped);
                // Only a LOAD_FAILED file is left out of the load history, so re-COPYing it retries the file
                // instead of skipping it as already done. LOADED and PARTIALLY_LOADED are both remembered —
                // including a LOADED file that held no records at all. Live-verified: a re-COPY
                // reported LOAD_FAILED a second time for the same file, but answered "0 files processed"
                // for a partially loaded one and for a header-only one.
                if (!loadFailed) {
                    loadedKeys.add(fileKey);
                }
                // A file counts as loaded — and so as purgeable — unless it is LOAD_FAILED. A clean file
                // with no records at all (header-only, or empty) still counts: it is LOADED with zero rows.
                if (purge && !loadFailed) {
                    purgeCandidates.add(file);
                }
                // A file dropped by a budget it never tripped on rejected records — SKIP_FILE_0 over a
                // wholly CLEAN file — reports the quartet all-null beside errors_seen = 0: there was no
                // first error to describe. A file dropped by a budget it DID trip still describes it.
                resultRows.add(new Row(Arrays.asList(fileName,
                    loadFailed ? "LOAD_FAILED" : (errors == 0 ? "LOADED" : "PARTIALLY_LOADED"),
                    parsed, rowsLoaded, errorLimit, errors,
                    firstError == null ? null : firstError.getMessage(),
                    firstError == null ? null : Integer.valueOf(firstError.getLine()),
                    firstError == null ? null : firstError.getCharacter(),
                    firstError == null ? null : firstError.getColumnName())));
            }

            for (final Row row : pending) {
                executor.getStorageEngine().getTableStorage(fullyQualifiedTableName).insert(row);
            }

            purgeLoadedFiles(purgeCandidates);
        }

        // The files FILES = (…) named but the stage does not hold. Reaching here means ON_ERROR tolerates
        // them (the aborting default already threw above), so each is reported as its own LOAD_FAILED row,
        // AFTER the files that were really read — the order the account listed them in. The file is left out
        // of the load history, so a later COPY picks it up once it appears (live-verified: a re-COPY of the
        // same name loaded it, LOADED, as soon as the file was staged).
        for (final String missing : missingFiles) {
            resultRows.add(new Row(Arrays.asList(missing, "LOAD_FAILED", 0, 0,
                fileErrorLimit(onError, 0), 1, remoteFileMissingDetail(fromLocation, missing),
                null, null, null)));
        }

        // No per-file row means no file was processed — answer with the one-column summary rather than an
        // empty per-file result. VALIDATION_MODE is excluded: it keeps its own (possibly empty) shape, and on
        // a stage with no readable directory it never reaches validateCopyFiles above.
        if (validationMode == null && resultRows.isEmpty()) {
            logger.info("COPY INTO table completed: no files processed");
            return new ResultSet(
                Arrays.asList(new ResultSetColumn("status", StringType.VARCHAR)),
                Arrays.asList(new Row(NO_FILES_PROCESSED_STATUS)));
        }

        logger.info("COPY INTO table completed: {} file result(s)", resultRows.size());
        return new ResultSet(resultColumns, resultRows);
    }

    /**
     * {@code COPY … PURGE = TRUE}: delete the staged source files this statement loaded, once their rows are
     * committed to the table. Live-verified against a real account — every case below was
     * observed directly, with a no-PURGE control run beside it to confirm the file would otherwise survive:
     *
     * <ul>
     *   <li>a clean load (LOADED) purges the file — including a header-only or zero-byte file, which loads
     *       zero rows and is still purged, so the rule is NOT "rows were loaded";</li>
     *   <li>a partial load (PARTIALLY_LOADED, ON_ERROR = CONTINUE with some bad records) purges it;</li>
     *   <li>a wholly rejected file (LOAD_FAILED — every record bad under CONTINUE, or any bad record under
     *       SKIP_FILE) is KEPT;</li>
     *   <li>a failed statement (ON_ERROR = ABORT_STATEMENT) purges NOTHING — not even the files that had
     *       already loaded cleanly;</li>
     *   <li>an already-loaded file (skipped by load history) is KEPT; FORCE = TRUE reloads and purges it;</li>
     *   <li>VALIDATION_MODE purges nothing — it does not load;</li>
     *   <li>files the statement never selected (excluded by PATTERN / FILES) are KEPT — PURGE empties the
     *       loaded set, never the stage.</li>
     * </ul>
     *
     * The COPY result set is unchanged either way: the account reported the same columns and rows with and
     * without PURGE, so only a subsequent LIST reveals the deletion.
     *
     * <p>Deliberately NOT gated behind {@code command.removeEnabled} (the guard on the bare REMOVE / RM
     * command), for three reasons. The flag defaults to false, so gating would leave PURGE silently doing
     * nothing on a default engine — which is precisely the defect this implements away. PURGE is scoped in a
     * way REMOVE is not: it can only delete files this very statement just read into the table, whereas
     * REMOVE takes out a whole stage (or everything matching a pattern) on its own. And Snowflake honours
     * {@code PURGE = TRUE} unconditionally, so a local toggle over a documented per-statement option would be
     * a fidelity divergence.
     *
     * <p>A delete that fails is LOGGED, never thrown. Snowflake documents that a failed purge returns no
     * error — the load succeeded, and the statement reports that. Frostlake previously threw here on the
     * reasoning that a local unlink failure is repeatable rather than transient, but that made the engine
     * fail a statement Snowflake completes, which is the same class of divergence as accepting SQL
     * Snowflake rejects. The warning keeps the operator informed without changing the statement's outcome.
     */
    private void purgeLoadedFiles(final List<Path> loadedFiles) {
        final List<String> failures = new ArrayList<>();
        for (final Path file : loadedFiles) {
            try {
                // Already gone (concurrently removed) is not a failure — the file is off the stage either way.
                Files.deleteIfExists(file);
            } catch (final IOException e) {
                failures.add(file.getFileName().toString() + " (" + e.getMessage() + ")");
            }
        }
        if (!failures.isEmpty()) {
            logger.warn("COPY INTO … PURGE = TRUE: the data was loaded, but {} staged file(s) could not be"
                + " deleted and remain on the stage: {}", failures.size(), String.join(", ", failures));
        }
    }

    /**
     * COPY … VALIDATION_MODE: parse and validate the staged files WITHOUT loading any rows or
     * recording load history. RETURN_&lt;N&gt;_ROWS yields the first N rows exactly as they would
     * load (failing on the first bad record); RETURN_ERRORS / RETURN_ALL_ERRORS yield one row per
     * would-be-rejected record, with an empty result when every record is clean.
     *
     * <p>A file {@code FILES = (…)} named but the stage does not hold is reported here as one more
     * would-be-rejected record ({@code missingFiles}, appended after the record-level errors) rather than
     * failing the statement — live-verified: {@code VALIDATION_MODE = RETURN_ERRORS} over a
     * missing file answered with an error ROW carrying the miss as its ERROR text, while
     * {@code RETURN_<n>_ROWS} over the same file threw. The RETURN_&lt;n&gt;_ROWS throw happens before this
     * method is reached.
     */
    private ResultSet validateCopyFiles(final String validationMode, final Table table,
            final List<TableColumn> tableCols, final int[] fieldToCol,
            final List<FrostlakeParser.CopyTransformItemContext> transformItems,
            final ExpressionEvaluator transformEval, final int transformSlots,
            final Path baseDir, final String pattern, final List<String> files,
            final StageFileReader recordReader, final int skipHeader, final char delimiter, final Character enclosure,
            final boolean trimSpace, final List<String> nullIf, final boolean emptyFieldAsNull,
            final boolean checkColumnCount, final boolean skipBlankLines,
            final String fromLocation, final List<String> missingFiles) {
        final String mode = validationMode.toUpperCase().replace("'", "").trim();
        final Matcher nRowsMatcher = RETURN_N_ROWS_MODE.matcher(mode);
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
                        recordReader, skipHeader, delimiter, enclosure, trimSpace, nullIf, emptyFieldAsNull,
                        checkColumnCount, skipBlankLines, null)) {
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
                transformSlots, recordReader, skipHeader, delimiter, enclosure, trimSpace, nullIf,
                emptyFieldAsNull, checkColumnCount, skipBlankLines, errorRows);
        }
        for (final String missing : missingFiles) {
            // The miss has no record geometry behind it — no line, and nothing rejected to quote back.
            errorRows.add(new Row(Arrays.asList(
                remoteFileMissingDetail(fromLocation, missing), missing, null, null)));
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
            final boolean trimSpace, final List<String> nullIf, final boolean emptyFieldAsNull,
            final boolean checkColumnCount, final boolean skipBlankLines, final List<Row> errorCollector) {
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
                                + " record " + recordNumber + ": " + copyErrorMessage(rowError), rowError);
                        }
                        errorCollector.add(new Row(Arrays.asList(
                            copyErrorMessage(rowError), fileName, recordNumber, record.toString())));
                    }
                }
            } else {
                final List<String> lines = Files.readAllLines(file);
                int recordNumber = 0;
                for (int ln = skipHeader; ln < lines.size(); ln++) {
                    final String line = lines.get(ln);
                    if (line.isEmpty() && skipBlankLines) {
                        continue;
                    }
                    recordNumber++;
                    try {
                        final List<Boolean> enclosedEmpty = new ArrayList<>();
                        final List<String> rowFields = parseCsvLine(line, delimiter, enclosure, enclosedEmpty);
                        applyCsvFieldOptions(rowFields, trimSpace, nullIf, emptyFieldAsNull, enclosedEmpty);
                        if (checkColumnCount) {
                            requireCsvColumnCount(rowFields, table, line, ln + 1,
                                ln == lines.size() - 1, recordNumber == 1, delimiter, enclosure);
                        }
                        final Row row = transformItems != null
                            ? buildTransformedRow(transformItems, tableCols.size(), fieldToCol, rowFields, transformEval, transformSlots)
                            : buildCopyRow(tableCols, fieldToCol, rowFields);
                        executor.enforceColumnConstraints(table, row);
                        rows.add(row);
                    } catch (final RuntimeException rowError) {
                        if (errorCollector == null) {
                            throw new RuntimeException("COPY validation failed on file " + fileName
                                + " line " + (ln + 1) + ": " + copyErrorMessage(rowError), rowError);
                        }
                        // A column-count reject reports the position just past the record, which can be the
                        // FOLLOWING line — the account's RETURN_ERRORS row does the same (its LINE was 3 for
                        // a bad record on line 2, with the record's own line carried separately).
                        final int errorLine = rowError instanceof CsvColumnCountException
                            ? ((CsvColumnCountException) rowError).getLine() : ln + 1;
                        errorCollector.add(new Row(Arrays.asList(
                            copyErrorMessage(rowError), fileName, errorLine, line)));
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

    /**
     * ON_ERROR's value, upper-cased and validated. Snowflake accepts exactly {@code ABORT_STATEMENT},
     * {@code CONTINUE}, {@code SKIP_FILE}, {@code SKIP_FILE_<n>} and {@code 'SKIP_FILE_<n>%'} (quoted or
     * unquoted, any case) and rejects everything else outright — live-verified on a real account
     *: {@code ON_ERROR = KEEP_GOING}, {@code = 2}, {@code = '2'} and {@code = 1} each fail
     * compilation with "invalid value [&lt;as written&gt;] for parameter 'ON_ERROR'", so a bare error count is
     * NOT a spelling of the threshold — {@code SKIP_FILE_<n>} is. (The {@code %} form must be quoted: the
     * account rejects a bare {@code %} as a syntax error before the value is ever examined.) Accepting a
     * value Snowflake refuses would silently downgrade it here to the aborting default.
     */
    private String requireValidOnError(final FrostlakeParser.CopyOptionValueContext valueCtx) {
        final String value = copyOptValue(valueCtx);
        if (value != null) {
            final String upper = value.toUpperCase();
            if (upper.equals("ABORT_STATEMENT") || upper.equals("CONTINUE")
                    || SKIP_FILE_ON_ERROR.matcher(upper).matches()) {
                return upper;
            }
        }
        throw new RuntimeException("SQL compilation error:\ninvalid value ["
            + ParseTreeText.getOriginalText(valueCtx) + "] for parameter 'ON_ERROR'");
    }

    /**
     * ON_ERROR's per-file error budget: how many rejected records ONE file may hold before the WHOLE file is
     * dropped, the records that parsed cleanly along with it — and, identically, the {@code error_limit} the
     * per-file result reports. Live-verified on a real account, every row of this table observed
     * directly with a control run beside it:
     *
     * <ul>
     *   <li>{@code CONTINUE} — the record count itself, {@code max(rows_parsed, 1)}: a 4-record file reported
     *       4, a 2-record file 2, and a header-only file with nothing parsed reported 1 rather than 0. NOT
     *       unlimited, though it behaves so — a budget equal to the record count can only be reached by a
     *       file whose every record was rejected, which loads nothing and is LOAD_FAILED on that ground
     *       alone (see {@link #fileLoadFailed}), so no clean record is ever dropped by it;</li>
     *   <li>{@code ABORT_STATEMENT} — 1, whatever the file holds: clean loads of a 0-, 1- and 2-record file
     *       all reported 1. It can never be reached, the statement having already failed on the first bad
     *       record, so this value only ever shows up in the result;</li>
     *   <li>{@code SKIP_FILE} — 1: a single bad record drops the file (a 2-record file with 1 bad record
     *       loaded NOTHING and reported LOAD_FAILED, not the good record);</li>
     *   <li>{@code SKIP_FILE_<n>} — n, taken as-is and NOT clamped: a 4-record file with 1 bad record
     *       survived {@code SKIP_FILE_2} (PARTIALLY_LOADED, 3 loaded) and was dropped by
     *       {@code SKIP_FILE_1}, and {@code SKIP_FILE_0} dropped a wholly CLEAN file — 0 rejected records
     *       already reaches a budget of 0;</li>
     *   <li>{@code 'SKIP_FILE_<n>%'} — n percent of the records parsed, rounded DOWN, but never below 1.
     *       Pinned by three readings of the account's own reported error_limit: 70% of 4 records = 2 (not 3,
     *       so not rounded up), 50% of 3 = 1 (not 2, so not rounded to nearest), and 0% of 2 = 1 (not 0, so
     *       floored at one) — the last confirmed by a clean file loading normally under
     *       {@code 'SKIP_FILE_0%'} while {@code SKIP_FILE_0} dropped it.</li>
     * </ul>
     *
     * The budget is per FILE, not per statement: one run over four files under {@code SKIP_FILE_2} reported
     * LOADED / PARTIALLY_LOADED (1 error) / LOAD_FAILED (2 errors) / LOAD_FAILED (2 errors) side by side,
     * so the count never carries from one file to the next.
     */
    private static int fileErrorLimit(final String onError, final int rowsParsed) {
        final Matcher skipFile = SKIP_FILE_ON_ERROR.matcher(onError);
        if (!skipFile.matches()) {
            // ABORT_STATEMENT reports a flat 1; CONTINUE reports the record count, floored at one.
            return "CONTINUE".equals(onError) ? Math.max(rowsParsed, 1) : 1;
        }
        if (skipFile.group(1) == null) {
            return 1;
        }
        final int count = skipFileErrorCount(skipFile.group(1));
        if (skipFile.group(2) == null) {
            return count;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) count * rowsParsed / 100L));
    }

    /**
     * Describe one rejected CSV record for the {@code first_error} quartet: the engine's own message, the
     * record's 1-based PHYSICAL line, the 1-based character at which the offending field starts in that raw
     * line, and the offending column rendered {@code "TABLE"["COLUMN":ordinal]}.
     *
     * <p>The column is found by re-checking the built row column by column ({@link #firstFailingColumn}) —
     * the write-constraint enforcement that rejected it reports no position of its own. A record that failed
     * before a row even existed (a COPY transformation expression that threw) keeps the message and line and
     * leaves the character and column null, as does a column no staged field maps onto.
     */
    private CopyFileError describeCsvError(final RuntimeException rowError, final Table table, final Row row,
            final String line, final int lineNumber, final int[] fieldToCol,
            final char delimiter, final Character enclosure) {
        if (rowError instanceof CsvColumnCountException) {
            // A count reject worked out its own geometry when it was raised: it happens before a row exists,
            // and the position it reports is off the end of the fields rather than at one of them.
            final CsvColumnCountException countError = (CsvColumnCountException) rowError;
            return new CopyFileError(countError.getMessage(), countError.getLine(),
                countError.getCharacter(), countError.getColumnName());
        }
        final int colIdx = row == null ? -1 : firstFailingColumn(table, row);
        if (colIdx < 0) {
            return new CopyFileError(copyErrorMessage(rowError), lineNumber, null, null);
        }
        Integer character = null;
        for (int f = 0; f < fieldToCol.length; f++) {
            if (fieldToCol[f] == colIdx) {
                character = Integer.valueOf(csvFieldStart(line, delimiter, enclosure, f));
                break;
            }
        }
        return new CopyFileError(copyErrorMessage(rowError), lineNumber, character,
            errorColumnName(table.getName(), table.getColumns().get(colIdx).getName(), colIdx + 1));
    }

    /**
     * The message a COPY reports for one rejected record. Almost always the write path's own — a NOT NULL
     * breach ({@code NULL result in a non-nullable column}) and a bad numeric
     * ({@code Numeric value 'BADX' is not recognized}) reach the account's COPY result and its DML errors
     * with identical wording, live-verified on both.
     *
     * <p>The ONE violation Snowflake words differently per path is an over-long string, so it is carried as
     * parts and rendered here: COPY says {@code User character length limit (3) exceeded by string
     * 'abcdefgh'} where DML says {@code String 'abcdefgh' is too long and would be truncated}. See
     * {@link ColumnLengthException}.
     */
    private static String copyErrorMessage(final RuntimeException rowError) {
        if (rowError instanceof ColumnLengthException) {
            final ColumnLengthException tooLong = (ColumnLengthException) rowError;
            return "User character length limit (" + tooLong.getLimit() + ") exceeded by string '"
                + tooLong.getValue() + "'";
        }
        return rowError.getMessage();
    }

    /**
     * The index of the first column of {@code row} that violates a write constraint, or -1 if none does.
     * Mirrors {@code enforceColumnConstraints}' own two passes exactly — every column is type-coerced before
     * any NOT NULL is checked — so the column named here is the one whose failure produced the message
     * reported beside it. Each probe runs the real enforcement over a one-column table, which keeps the
     * coercion rules in one place and honours the same {@code constraints.enforce.types} switch; the first
     * pass nulls out the column's NOT NULL so only a coercion failure can surface in it.
     */
    private int firstFailingColumn(final Table table, final Row row) {
        final List<TableColumn> cols = table.getColumns();
        for (int c = 0; c < cols.size() && c < row.getValues().size(); c++) {
            final TableColumn col = cols.get(c);
            final TableColumn nullable = new TableColumn(col.getName(), col.getDataType(), true, null, false, false, false);
            try {
                executor.enforceColumnConstraints(new Table(table.getName(), Arrays.asList(nullable), false),
                    new Row(Arrays.asList(row.getValue(c))));
            } catch (final RuntimeException coercionFailed) {
                return c;
            }
        }
        for (int c = 0; c < cols.size() && c < row.getValues().size(); c++) {
            if (!cols.get(c).isNullable() && row.getValue(c) == null) {
                return c;
            }
        }
        return -1;
    }

    /**
     * The 1-based character at which CSV field {@code fieldIndex} (0-based) starts in {@code line}, counted
     * over the RAW text — enclosure characters included. Live-verified: a bad third field reported
     * 7 in {@code 2,bob,BADAGE} and 10 in {@code 2,"b,ob",BADAGE}, so the offset is the real position in the
     * line and not one recomputed from the parsed field widths. Splits exactly the way
     * {@link #parseCsvLine} does, so a delimiter inside an enclosure does not start a field.
     */
    private static int csvFieldStart(final String line, final char delimiter, final Character enclosure,
            final int fieldIndex) {
        if (fieldIndex <= 0) {
            return 1;
        }
        int field = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (enclosure != null && c == enclosure) {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == enclosure) {
                    i++;                       // escaped enclosure ("") — not a boundary
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                field++;
                if (field == fieldIndex) {
                    return i + 2;              // the character just past this delimiter, 1-based
                }
            }
        }
        return 1;
    }

    /** Snowflake's {@code first_error_column_name} rendering: {@code "TABLE"["COLUMN":ordinal]}, 1-based. */
    private static String errorColumnName(final String tableName, final String columnName, final int ordinal) {
        return "\"" + tableName + "\"[\"" + columnName + "\":" + ordinal + "]";
    }

    /**
     * Whether this COPY checks each CSV record's field count against the target table at all. Three clauses
     * switch the check OFF entirely — each live-verified on a real account against a file the
     * same statement rejects without them:
     *
     * <ul>
     *   <li>an explicit column list. {@code COPY INTO t (ID, NAME)} loaded a ONE-field file, and
     *       {@code COPY INTO t (ID, NAME, AGE)} loaded a FIVE-field one, both {@code LOADED} with zero errors;
     *       {@code COPY INTO t (ID, NAME)} over a 3-field file into a 3-column table loaded 2 rows and left
     *       the third column NULL. So the list does not merely re-point the expected count at itself — it
     *       suspends the check in BOTH directions;</li>
     *   <li>a COPY transformation. {@code FROM (SELECT $1, $2 FROM @stage)} loaded the very files a plain
     *       COPY rejected — the too-many file whole (extra field dropped) and the too-few file with
     *       {@code $2} NULL — which is exactly the workaround Snowflake's own docs point at;</li>
     *   <li>MATCH_BY_COLUMN_NAME, which matches the file's HEADER against the table instead of counting
     *       fields. Its rejection is a different rule with its own wording ("Error with CSV header: error
     *       caused more fields in data than fields in header"), so raising the table-count message there
     *       would be a fabricated one. Frostlake does not implement header matching for CSV, so a
     *       MATCH_BY_COLUMN_NAME load still reads positionally here.</li>
     * </ul>
     *
     * On top of those, {@code ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE} switches it off by request.
     */
    private static boolean columnCountChecked(final boolean errorOnColumnCountMismatch, final List<String> columns,
            final String matchByColumnName,
            final List<FrostlakeParser.CopyTransformItemContext> transformItems) {
        return errorOnColumnCountMismatch
            && transformItems == null
            && (columns == null || columns.isEmpty())
            && (matchByColumnName == null || "NONE".equalsIgnoreCase(matchByColumnName));
    }

    /**
     * {@code ERROR_ON_COLUMN_COUNT_MISMATCH} (Snowflake default TRUE): reject a CSV record whose field count
     * differs from the target table's column count, in EITHER direction. Without it a malformed file loads
     * silently reshaped — an extra field dropped, a missing one NULL-padded — and reports a clean
     * {@code LOADED}, which is the whole reason the option defaults to TRUE.
     *
     * <p>Live-verified on a real account, the option's two settings run side by side over the
     * same staged files. With {@code = FALSE} the account loads them exactly the lenient way: a
     * {@code 1,a,extra} record into a two-column table lands {@code (1, a)} and a bare {@code 1} record lands
     * {@code (1, NULL)}, {@code LOADED} with {@code errors_seen = 0} and the whole first_error quartet null.
     * With the default the same records are rejected and counted as ordinary errors — they spend the
     * {@code ON_ERROR} budget like any other bad record ({@code SKIP_FILE} dropped a 3-record file over one
     * such record, {@code SKIP_FILE_2} let it through PARTIALLY_LOADED), and the aborting default fails the
     * statement outright (SQLSTATE 22000, error 100080) leaving the table empty.
     *
     * <p>WHICH message a rejected record gets turns on ONE thing: whether it is the file's FIRST data record.
     * Pinned by varying every other dimension against it — direction (too many / too few), table width (2 and
     * 3 columns), field delimiter (comma and pipe), file length (1, 2 and 3 records), and the record's
     * position (first / middle / last, with a one-record file making first and last the same record):
     *
     * <ul>
     *   <li><b>the first data record</b> — {@code Number of columns in file (F) does not match that of the
     *       corresponding table (T), use file format option error_on_column_count_mismatch=false to ignore
     *       this error}, either direction, and the delimiter never appears in it. Reported at the position
     *       just PAST the record: the start of the following line when the file has one (line + 1, character
     *       1), else the end of the record's own line (character length + 1) — the same physical spot,
     *       rendered whichever way the file allows. Its column is the ordinal F, named when the table has an
     *       F-th column ({@code "T"["ID":1]} for one field into two columns) and bare when it does not
     *       ({@code "T"[3]} for three fields into two, {@code "T"[5]} for five into three) — the only
     *       first_error_column_name shape in the whole COPY result that carries no name;</li>
     *   <li><b>a later record with TOO MANY fields</b> — {@code Field delimiter '<d>' found while expecting
     *       record delimiter '\n'}, quoting the file format's OWN delimiter (a pipe-delimited file said
     *       {@code '|'}). Reported at the offending delimiter itself — the one that should have ended the
     *       record — and against the table's LAST column;</li>
     *   <li><b>a later record with TOO FEW fields</b> — {@code End of record reached while expected to parse
     *       column '<col>'}, naming the first column the record never reached, in the same rendering the
     *       column is reported in. Reported just past the record's end.</li>
     * </ul>
     *
     * A wholly BLANK line is one empty field, so on any table wider than one column it is a too-few record
     * and rejected exactly like that — live-verified, and the reason {@code SKIP_BLANK_LINES} exists.
     */
    private static void requireCsvColumnCount(final List<String> fields, final Table table, final String line,
            final int lineNumber, final boolean lastLine, final boolean firstRecord,
            final char delimiter, final Character enclosure) {
        final int fileColumns = fields.size();
        final int tableColumns = table.getColumns().size();
        if (fileColumns == tableColumns) {
            return;
        }
        if (firstRecord) {
            throw new CsvColumnCountException("Number of columns in file (" + fileColumns
                + ") does not match that of the corresponding table (" + tableColumns
                + "), use file format option error_on_column_count_mismatch=false to ignore this error",
                lastLine ? lineNumber : lineNumber + 1,
                Integer.valueOf(lastLine ? line.length() + 1 : 1),
                countMismatchColumnName(table, fileColumns));
        }
        if (fileColumns > tableColumns) {
            throw new CsvColumnCountException("Field delimiter '" + delimiter
                + "' found while expecting record delimiter '\\n'", lineNumber,
                Integer.valueOf(csvFieldStart(line, delimiter, enclosure, tableColumns) - 1),
                errorColumnName(table.getName(), table.getColumns().get(tableColumns - 1).getName(), tableColumns));
        }
        final String missingColumn = errorColumnName(table.getName(),
            table.getColumns().get(fileColumns).getName(), fileColumns + 1);
        throw new CsvColumnCountException("End of record reached while expected to parse column '"
            + missingColumn + "'", lineNumber, Integer.valueOf(line.length() + 1), missingColumn);
    }

    /**
     * The column a first-record count mismatch is reported against: always the ordinal of the file's own
     * field count, named when the table reaches that far and bare when it does not (see
     * {@link #requireCsvColumnCount}).
     */
    private static String countMismatchColumnName(final Table table, final int fileColumns) {
        if (fileColumns > table.getColumns().size()) {
            return "\"" + table.getName() + "\"[" + fileColumns + "]";
        }
        return errorColumnName(table.getName(), table.getColumns().get(fileColumns - 1).getName(), fileColumns);
    }

    /** The {@code <n>} of a SKIP_FILE_&lt;n&gt;: a budget too wide for an int is wider than any file here. */
    private static int skipFileErrorCount(final String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (final NumberFormatException tooWide) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Did this file end up {@code LOAD_FAILED}? Snowflake's per-file status is one of {@code LOADED},
     * {@code PARTIALLY_LOADED} and {@code LOAD_FAILED}, and the boundary between them is NOT "were any rows
     * loaded" — live-verified on a real account:
     *
     * <ul>
     *   <li>a header-only file and a zero-byte file both load ZERO rows and are still {@code LOADED} — no
     *       record was rejected, so nothing failed;</li>
     *   <li>a file that loses only SOME of its records is {@code PARTIALLY_LOADED};</li>
     *   <li>a file that loads nothing because every record was rejected is {@code LOAD_FAILED} — and so is
     *       one that loads nothing because ON_ERROR's budget dropped it, whose records may well have been
     *       loadable ({@code SKIP_FILE} over a file with one bad record out of two: LOAD_FAILED, 0 loaded).</li>
     * </ul>
     *
     * Hence the two-part test: nothing loaded, AND a reason for it. The two routes into the status are
     * genuinely distinct — a wholly rejected file is LOAD_FAILED even under a budget it never reached
     * (2 rejected records out of 2 under {@code SKIP_FILE_5}), and a wholly CLEAN file is LOAD_FAILED when
     * the budget is 0 ({@code SKIP_FILE_0}), which no rule phrased over rejected records alone would give.
     */
    private static boolean fileLoadFailed(final int rowsLoaded, final int errors, final boolean fileSkipped) {
        return rowsLoaded == 0 && (errors > 0 || fileSkipped);
    }

    /**
     * The names {@code FILES = (…)} promised but the stage does not hold, in the order the statement wrote
     * them. Naming a file that is not there is an ERROR on Snowflake, not a quiet no-op — the whole point of
     * the clause is that the caller asserted those files exist. Live-verified on a real account,
     * each case paired with a control that loaded the very same stage cleanly:
     *
     * <ul>
     *   <li>the default {@code ON_ERROR = ABORT_STATEMENT} fails the statement outright (SQLSTATE 22000,
     *       error 91016) and loads NOTHING — a mixed {@code FILES = ('good1.csv','nosuch.csv')} left the
     *       table empty, in either list order, so the check precedes the load and is atomic;</li>
     *   <li>{@code CONTINUE} and every {@code SKIP_FILE} spelling suppress the failure entirely and report
     *       the miss as a {@code LOAD_FAILED} per-file row instead, while the files that ARE there load
     *       normally beside it — the one probed case where ON_ERROR changes whether a statement throws at
     *       all, rather than how many records it tolerates;</li>
     *   <li>an EMPTY stage plus {@code FILES = ('x.csv')} errors too: the promise outranks the "0 files
     *       processed" summary an empty stage would otherwise answer with;</li>
     *   <li>{@code FORCE = TRUE} does not suppress it, and neither does a PATTERN — a statement carrying
     *       both a FILES list and a PATTERN ignores the PATTERN outright (see
     *       {@code QueryExecutor.listCopyFiles}), so no pattern can excuse a named file;</li>
     *   <li>a file that IS there but is already in the load history keeps its {@code LOAD_SKIPPED} row; only
     *       a genuinely absent name produces this. When a statement names one of each, the miss wins under
     *       the aborting default and both rows come back under CONTINUE.</li>
     * </ul>
     *
     * A PATTERN matching nothing stays a legitimate no-op ({@code Copy executed with 0 files processed.}) —
     * a pattern merely filters what is on the stage, it promises nothing. That asymmetry is the whole rule.
     *
     * <p>Returns empty when no FILES list was given, and also when the FROM location resolves to no
     * directory at all: an unknown stage is a different failure on the account ("Stage '…' does not exist or
     * not authorized", raised whether or not FILES is present), so it is not reported as a missing file here.
     *
     * <p>Order is the statement's own, which is a DELIBERATE divergence where a statement misses several
     * files at once: the account names exactly one of them and picks it by neither rule. Reversing the FILES
     * list never changed its pick ({@code ('zmissing','amissing')} and {@code ('amissing','zmissing')} both
     * reported amissing; {@code ('m3','m1','m2')} and {@code ('m2','m3','m1')} both reported m1), so it is
     * not list order — yet {@code ('zzz','m9')} reported zzz, so it is not sorted order either. The pick is
     * stable per file set across repeated runs, but by something internal to the account (a partition of the
     * file set across its loaders) with no analogue here. Naming the first one the statement wrote is
     * deterministic and explicable, which is the most that can be matched.
     */
    private List<String> missingNamedFiles(final Path baseDir, final List<String> files) {
        if (files == null || files.isEmpty() || baseDir == null) {
            return new ArrayList<>();
        }
        final List<String> missing = new ArrayList<>();
        for (final String name : new LinkedHashSet<>(files)) {
            final Path resolved = executor.resolveStagedFile(baseDir, name);
            if (resolved == null || !Files.isRegularFile(resolved)) {
                missing.add(name);
            }
        }
        return missing;
    }

    /**
     * How a missing staged file is addressed back to the caller: the FROM location as written, then the name
     * as {@code FILES = (…)} wrote it. The account names it by its physical location instead — {@code
     * '@st146/nosuch.csv'} for a named stage (which this matches but for identifier case), and an internal
     * {@code 'tables/<id>/…'} / {@code 'users/<id>/…'} path for a table or user stage, whose numeric object
     * ids have no analogue here. The stage-relative form is the reproducible part and the part that tells the
     * caller which file it was.
     */
    private static String stagedFileRef(final String fromLocation, final String fileName) {
        if (fromLocation == null || fromLocation.isEmpty()) {
            return fileName;
        }
        return fromLocation.endsWith("/") ? fromLocation + fileName : fromLocation + "/" + fileName;
    }

    /**
     * The message the account fails an aborting COPY with (SQLSTATE 22000, error 91016) — verbatim but for
     * the file reference, see {@link #stagedFileRef}. Live-verified across the table stage, the
     * user stage {@code @~} and a named stage, and identical under {@code FORCE = TRUE}, under a PATTERN, and
     * for a COPY transformation source.
     */
    private static String remoteFileNotFound(final String fromLocation, final String fileName) {
        return "Remote file '" + stagedFileRef(fromLocation, fileName) + "' was not found."
            + " If you are running a copy command, please make sure files are not deleted when they are being"
            + " loaded or files are not being loaded into two different tables concurrently with auto purge"
            + " option.";
    }

    /**
     * The LONGER wording of the same miss, which the account uses wherever the file is reported rather than
     * thrown — the {@code first_error} of a {@code LOAD_FAILED} row and the {@code ERROR} of a
     * {@code VALIDATION_MODE} result. Live-verified: the two texts really are different, the
     * reported one naming three candidate causes the thrown one leaves out, so they are kept apart here too.
     */
    private static String remoteFileMissingDetail(final String fromLocation, final String fileName) {
        return "Remote file '" + stagedFileRef(fromLocation, fileName) + "' was not found."
            + " There are several potential causes. The file might not exist. The required credentials may be"
            + " missing or invalid. If you are running a copy command, please make sure files are not deleted"
            + " when they are being loaded or files are not being loaded into two different tables"
            + " concurrently with auto purge option.";
    }

    /** Whether a VALIDATION_MODE value is the error-listing family rather than RETURN_&lt;n&gt;_ROWS. */
    private static boolean isReturnErrorsMode(final String validationMode) {
        return !RETURN_N_ROWS_MODE.matcher(validationMode.toUpperCase().replace("'", "").trim()).matches();
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

    /**
     * Reject a semi-structured COPY into more than one column. A JSON / XML / AVRO / PARQUET / ORC record
     * is ONE semi-structured value, so Snowflake insists the target be a single VARIANT / OBJECT / ARRAY
     * column unless MATCH_BY_COLUMN_NAME splits the record or a COPY transformation projects its fields.
     */
    /** The load formats whose record is ONE semi-structured value (see
     *  {@link #rejectMultiColumnSemiStructuredLoad}). */
    private static final Set<String> SEMI_STRUCTURED_LOAD_FORMATS = new HashSet<>(
        Arrays.asList("JSON", "XML", "AVRO", "PARQUET", "ORC"));

    private void rejectMultiColumnSemiStructuredLoad(final String fileFormat, final Table table,
                                                     final List<String> columns,
                                                     final String matchByColumnName,
                                                     final FrostlakeParser.CopyTransformationContext transformation) {
        if (fileFormat == null || matchByColumnName != null && !"NONE".equalsIgnoreCase(matchByColumnName)
                || transformation != null) {
            return;
        }
        final String type = fileFormat.toUpperCase();
        if (!SEMI_STRUCTURED_LOAD_FORMATS.contains(type)) {
            return;
        }
        final int targetColumns = columns != null ? columns.size() : table.getColumns().size();
        if (targetColumns <= 1) {
            return;
        }
        throw new RuntimeException("SQL compilation error:\n" + type + " file format can produce one and"
            + " only one column of type variant, object, or array. Load data into separate columns using"
            + " the MATCH_BY_COLUMN_NAME copy option or copy with transformation.");
    }

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
     * Apply TRIM_SPACE, NULL_IF and EMPTY_FIELD_AS_NULL to a parsed CSV row in place, in that order: trim
     * leading/trailing whitespace from each field (when enabled), null out any field whose (trimmed) value
     * matches a NULL_IF token, then — under EMPTY_FIELD_AS_NULL, whose Snowflake default is TRUE — null out
     * any field left empty. A null in the field list is loaded as SQL NULL by {@code buildCopyRow} /
     * {@code inferStageFieldValue}; an empty string survives as the empty string.
     *
     * <p>The three settle in that order because each was live-verified against a real account
     * against a control run producing the other outcome:
     *
     * <ul>
     *   <li>TRIM_SPACE runs FIRST, so a field of nothing but spaces is empty by the time this rule sees it:
     *       {@code '   '} under {@code TRIM_SPACE = TRUE} loaded as NULL by default and as the empty string
     *       under {@code EMPTY_FIELD_AS_NULL = FALSE}, while the same field without TRIM_SPACE kept its three
     *       spaces under both;</li>
     *   <li>NULL_IF WINS over the option: {@code NULL_IF = ('')} with {@code EMPTY_FIELD_AS_NULL = FALSE}
     *       loaded NULL. The two are independent rather than alternatives — {@code NULL_IF = ()} with the
     *       default still loaded an empty field as NULL, and {@code NULL_IF = ('x')} with the default nulled
     *       both the empty field and the literal {@code x};</li>
     *   <li>an ENCLOSED empty field is NOT an empty field: with {@code FIELD_OPTIONALLY_ENCLOSED_BY = '"'} a
     *       {@code ""} loaded as the empty string even under the default TRUE, beside a bare {@code ,,} in
     *       the same record that loaded NULL. (The option defaults to NONE, so without it {@code ""} is an
     *       ordinary two-character string and this exemption cannot arise — {@code enclosedEmpty} is then
     *       empty.)</li>
     * </ul>
     *
     * <p>{@code enclosedEmpty} carries {@link #parseCsvLine}'s per-field "this empty field came from an
     * enclosure" flags and may be shorter than {@code fields} (it is filled only while an enclosure is set).
     */
    private static void applyCsvFieldOptions(final List<String> fields, final boolean trimSpace,
            final List<String> nullIf, final boolean emptyFieldAsNull, final List<Boolean> enclosedEmpty) {
        if (!trimSpace && !emptyFieldAsNull && (nullIf == null || nullIf.isEmpty())) {
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
            } else if (emptyFieldAsNull && value.isEmpty()
                    && !(i < enclosedEmpty.size() && enclosedEmpty.get(i).booleanValue())) {
                value = null;
            }
            fields.set(i, value);
        }
    }

    /**
     * Build one row from a CSV record's fields, placing each field in the column it maps onto (unmapped
     * columns stay NULL). The fields arrive already settled by {@link #applyCsvFieldOptions}, so a null is a
     * SQL NULL and an empty string is a real empty string — with ONE exception: a semi-structured target
     * column takes NULL for an empty field whatever EMPTY_FIELD_AS_NULL says. Live-verified with
     * a control beside it: {@code EMPTY_FIELD_AS_NULL = FALSE} loaded an empty field as NULL into both a
     * VARIANT and an ARRAY column (LOADED, no error) where the same statement loaded the empty string into a
     * VARCHAR one.
     */
    private static Row buildCopyRow(final List<TableColumn> tableCols, final int[] fieldToCol,
            final List<String> fields) {
        final int numCols = tableCols.size();
        final List<Object> values = new ArrayList<>(numCols);
        for (int i = 0; i < numCols; i++) {
            values.add(null);
        }
        for (int f = 0; f < fields.size() && f < fieldToCol.length; f++) {
            final int colIdx = fieldToCol[f];
            if (colIdx >= 0 && colIdx < numCols) {
                final String value = fields.get(f);
                final boolean emptyIntoSemiStructured = value != null && value.isEmpty()
                    && isSemiStructured(tableCols.get(colIdx).getDataType());
                values.set(colIdx, emptyIntoSemiStructured ? null : value);
            }
        }
        return new Row(values);
    }

    /** Whether a target column holds semi-structured data (VARIANT / OBJECT / ARRAY). */
    private static boolean isSemiStructured(final DataType type) {
        return type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType;
    }

    /**
     * Infer a staged CSV field's value: numeric literals become numbers so $n arithmetic (e.g. {@code $3 * 2})
     * works, anything else stays a string for text functions (e.g. {@code UPPER($2)}). The field arrives
     * already settled by {@link #applyCsvFieldOptions} — a null is a SQL NULL, and an empty string is
     * passed through as itself, which is what makes a COPY transformation honour EMPTY_FIELD_AS_NULL the
     * way a plain load does ({@code COPY INTO t FROM (SELECT $1, $2 FROM @s)}
     * loaded the empty string under {@code = FALSE} and NULL under the default). The value is still
     * coerced to the target column type on insert.
     */
    private static Object inferStageFieldValue(final String field) {
        if (field == null || field.isEmpty()) {
            return field;
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
        // COPY is an embedded SQL statement, so no scripting variable is in scope as a bare name here.
        return new ExpressionEvaluator(new Table("COPY_STAGE", slotCols, false),
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
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
            // $1 is the whole document as a TYPED variant, so paths extract typed values and a bare
            // $1 lands in a VARIANT column as a typed cell.
            slotValues.add(i == 0 ? VariantValue.ofNode(record) : null);
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

    /**
     * Split one CSV line on {@code delimiter}, honoring an optional enclosure character (with {@code ""}
     * escaping). Public so the stage-query path (SELECT … FROM @stage) reads fields exactly the way COPY
     * INTO does.
     *
     * <p>{@code enclosedEmptyOut} collects, alongside each field, whether it is an ENCLOSED empty — a field
     * that ended up empty having been written {@code ""} rather than left blank. The two are the same empty
     * string once split, but Snowflake keeps them apart: EMPTY_FIELD_AS_NULL nulls only the blank one (see
     * {@link #applyCsvFieldOptions}). It is filled in field order and stays empty when no enclosure is
     * configured, since the distinction cannot arise then.
     */
    public static List<String> parseCsvLine(final String line, final char delimiter, final Character enclosure,
            final List<Boolean> enclosedEmptyOut) {
        final List<String> fields = new ArrayList<>();
        final StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean sawEnclosure = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (enclosure != null && c == enclosure) {
                sawEnclosure = true;
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == enclosure) {
                    cur.append(enclosure);   // escaped enclosure ("")
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(cur.toString());
                if (enclosure != null) {
                    enclosedEmptyOut.add(Boolean.valueOf(sawEnclosure && cur.length() == 0));
                }
                cur.setLength(0);
                sawEnclosure = false;
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        if (enclosure != null) {
            enclosedEmptyOut.add(Boolean.valueOf(sawEnclosure && cur.length() == 0));
        }
        return fields;
    }

    /** Map a JSON record to a row: a single (VARIANT) column gets the whole element; otherwise object fields map to columns by name. */
    private static Row buildJsonRow(final List<TableColumn> tableCols, final JsonNode record) {
        final List<Object> values = new ArrayList<>(tableCols.size());
        if (tableCols.size() == 1) {
            values.add(jsonNodeToValue(record, tableCols.get(0)));
            return new Row(values);
        }
        for (final TableColumn col : tableCols) {
            values.add(jsonNodeToValue(jsonField(record, col.getName()), col));
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

    /**
     * A loaded JSON field as a cell value. Into a semi-structured column the node becomes a TYPED
     * variant of its canonical form — so a scalar keeps its variant type (a JSON {@code 123} is a
     * variant NUMBER, a JSON {@code "abc"} a variant STRING, exactly as Snowflake loads them).
     * Into any other column a scalar becomes its text (coerced later by the column type) and an
     * object/array its compact JSON string.
     */
    private static Object jsonNodeToValue(final JsonNode node, final TableColumn target) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (isSemiStructured(target.getDataType())) {
            return ArrayFunctionHelper.toCanonicalVariant(node);
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
            if (v instanceof VariantValue) {
                return ((VariantValue) v).isJsonObject() || ((VariantValue) v).isJsonArray();
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
        if (v instanceof VariantValue) {
            return ((VariantValue) v).text();
        }
        if (v instanceof String) {
            return ((String) v).trim();
        }
        return MAPPER.writeValueAsString(v);
    }
}
