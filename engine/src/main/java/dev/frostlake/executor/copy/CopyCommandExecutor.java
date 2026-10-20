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
import dev.frostlake.executor.FileFormatClause;
import dev.frostlake.executor.FileFormatReference;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SessionInputFormats;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.executor.StagePrefix;
import dev.frostlake.executor.StageQueryArguments;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.executor.StorageIntegrationReference;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.LoadRecord;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SequenceDefault;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.persistence.CheckpointDebt;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionOutcome;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StatementResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.CodePointText;
import dev.frostlake.values.VariantValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
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

    /** A partitioned unload's NULL partition: its name, and the directory its files go under, is {@code \\N}. */
    private static final String NULL_PARTITION = "\\N";

    private static final Logger logger = LoggerFactory.getLogger(CopyCommandExecutor.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    // The summary a COPY INTO <table> returns when it produced no per-file rows at all. Snowflake replaces the
    // whole per-file result with a single column named "status" holding exactly this one row — the trailing
    // period included. Live-verified (see the shape rule on executeCopyIntoTable).
    private static final String NO_FILES_PROCESSED_STATUS = "Copy executed with 0 files processed.";

    // The first_error a LOAD_SKIPPED row carries — the whole reason the file was passed over, reported in
    // the same column a rejected record would use. Live-verified, the trailing period included.
    private static final String ALREADY_LOADED_ERROR = "File was loaded before.";

    // VALIDATION_MODE's row-returning family, RETURN_<n>_ROWS — matched against the upper-cased value with
    // its quotes stripped. Everything else (RETURN_ERRORS, RETURN_ALL_ERRORS) lists errors instead.
    private static final Pattern RETURN_N_ROWS_MODE = Pattern.compile("RETURN_(\\d+)_ROWS");

    private final QueryExecutor executor;

    // COPY load history per fully-qualified table: keys of files already loaded, so a re-COPY / pipe REFRESH
    // skips them unless FORCE = TRUE (matching Snowflake). A permanent table's live in the catalog, which a snapshot,
    // a clone and a restored state carry; a temporary table keeps a history apart from a permanent table of its name,
    // here, which ends with its session.
    private final Map<String, Set<String>> temporaryLoadHistory = new HashMap<>();

    // The files the loads of transactions still open have read, one entry per COPY statement, by transaction. The
    // load history holds committed loads alone: a file another transaction loaded is not loaded yet, so a COPY that
    // meets it waits for that transaction rather than skip it, and loads it once that one rolls back. Kept in the
    // shared history straight away, another session's COPY answered "0 files processed" for a file whose load then
    // rolled back, and nothing loaded it. Read and written under the engine's write lock, as every COPY into a table
    // runs.
    private final Map<Transaction, List<UncommittedLoad>> uncommittedLoads = new IdentityHashMap<>();

    // Set while ALTER PIPE … REFRESH runs its COPY, which loads in a session of its own inside the refreshing
    // statement and so does not wait for another session's transaction (see executeCopyRefresh).
    private boolean refreshing;

    /** How a load key names a file under the engine's internal stage root, which a copy of the engine moves. */
    private static final String INTERNAL_ROOT = "@internal/";

    /**
     * TRUNCATE and CREATE OR REPLACE / DROP of the table discard its history; DELETE never does.
     *
     * @param fullyQualifiedTableName the table's qualified name
     * @param temporary               whether the table is the temporary one of that name
     */
    public void forgetLoadHistory(final String fullyQualifiedTableName, final boolean temporary) {
        loadHistory(temporary).remove(fullyQualifiedTableName);
        final String history = historyKey(fullyQualifiedTableName, temporary);
        final Iterator<List<UncommittedLoad>> open = uncommittedLoads.values().iterator();
        while (open.hasNext()) {
            final List<UncommittedLoad> loads = open.next();
            final Iterator<UncommittedLoad> each = loads.iterator();
            while (each.hasNext()) {
                if (each.next().history().equals(history)) {
                    each.remove();
                }
            }
            if (loads.isEmpty()) {
                open.remove();
            }
        }
        executor.getCatalog().getLoadHistory().forget(fullyQualifiedTableName, temporary);
    }

    /**
     * A table renamed or moved keeps its load history: the history follows it to its new name, and a file of its own
     * stage, which moved with it, keeps the identity it was loaded under, so a COPY of the renamed table skips what the
     * table already loaded (live-verified).
     *
     * @param oldTable  the table's qualified name before
     * @param newTable  its qualified name after
     * @param oldStage  its stage's directory before
     * @param newStage  its stage's directory after
     * @param temporary whether the table is temporary
     */
    public void followMovedTable(final String oldTable, final String newTable, final Path oldStage,
                                 final Path newStage, final boolean temporary) {
        final Map<String, Set<String>> histories = loadHistory(temporary);
        final Set<String> loaded = histories.remove(oldTable);
        if (loaded == null) {
            return;
        }
        final String from = portable(oldStage.toAbsolutePath().normalize() + File.separator);
        final String to = portable(newStage.toAbsolutePath().normalize() + File.separator);
        final Set<String> followed = new HashSet<>();
        for (final String key : loaded) {
            followed.add(key.startsWith(from) ? to + key.substring(from.length()) : key);
        }
        histories.put(newTable, followed);
    }

    /** A table's key in {@link #uncommittedLoads}: a temporary table's history is apart from a permanent one's. */
    private static String historyKey(final String fullyQualifiedTableName, final boolean temporary) {
        return (temporary ? "TEMPORARY " : "") + fullyQualifiedTableName;
    }

    /**
     * Whether a transaction's loads into a table have read a file, which then counts as loaded for its own later COPY.
     *
     * @param transaction the transaction
     * @param history     the table's {@link #historyKey}
     * @param fileKey     the file's load key
     * @return whether one did
     */
    private boolean loadedUncommitted(final Transaction transaction, final String history, final String fileKey) {
        final List<UncommittedLoad> loads = uncommittedLoads.get(transaction);
        if (loads != null) {
            for (final UncommittedLoad load : loads) {
                if (load.history().equals(history) && load.keys().contains(fileKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The open transaction other than the running one whose load into a table has read a file, or null.
     *
     * @param own     the running statement's transaction
     * @param history the table's {@link #historyKey}
     * @param fileKey the file's load key
     * @return that transaction, or null
     */
    private Transaction otherLoaderOf(final Transaction own, final String history, final String fileKey) {
        for (final Transaction other : uncommittedLoads.keySet()) {
            if (other != own && loadedUncommitted(other, history, fileKey)) {
                return other;
            }
        }
        return null;
    }

    /**
     * Ends a COPY statement's uncommitted load as its transaction ends, or as the statement fails inside it.
     *
     * @param transaction the statement's transaction
     * @param load        the load
     * @return whether the load was still there: a TRUNCATE or a DROP of the table since forgot it
     */
    private boolean endUncommittedLoad(final Transaction transaction, final UncommittedLoad load) {
        final List<UncommittedLoad> loads = uncommittedLoads.get(transaction);
        if (loads == null) {
            return false;
        }
        final boolean held = loads.remove(load);
        if (loads.isEmpty()) {
            uncommittedLoads.remove(transaction);
        }
        return held;
    }

    /** The load histories of the tables of one persistence, each under its table's qualified name. */
    private Map<String, Set<String>> loadHistory(final boolean temporary) {
        return temporary ? temporaryLoadHistory : executor.getCatalog().getCopyLoadedFiles();
    }

    /**
     * A path as a load key spells it: one under the engine's internal stage root relative to the root, so a clone or
     * a restored state, whose stages hold the same files under a root of its own, still knows them.
     */
    private String portable(final String path) {
        final String root = executor.internalStageRootPath().toAbsolutePath().normalize() + File.separator;
        return path.startsWith(root) ? INTERNAL_ROOT + path.substring(root.length()) : path;
    }

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
            String targetDisplay = null;
            boolean isLoadIntoTable = false;

            if (ctx.qualifiedName() != null) {
                // COPY INTO table (loading data)
                targetName = ParseTreeText.getQualifiedName(ctx.qualifiedName());
                isLoadIntoTable = true;
            } else if (ctx.stageRef() != null) {
                // COPY INTO @stage | @~ | @%table [/path] (unloading data to a stage)
                targetName = executor.stageRefToLocation(ctx.stageRef());
                targetDisplay = ParseTreeText.getOriginalText(ctx.stageRef());
                executor.requireStagePrivilege(Privilege.WRITE, ctx.stageRef());
                isLoadIntoTable = false;
            } else if (ctx.STRING_LITERAL() != null) {
                // COPY INTO 'protocol://bucket/path' (unloading data to an external location)
                targetName = ParseTreeText.extractStringLiteral(ctx.STRING_LITERAL());
                targetDisplay = targetName;
                isLoadIntoTable = false;
            }

            if (isLoadIntoTable) {
                // COPY INTO table FROM stage/location, which reads its fields by its file format's formats.
                final Map<String, String> statementInputs = SessionInputFormats.pinned();
                try {
                    return executeCopyIntoTable(ctx, targetName);
                } finally {
                    SessionInputFormats.restore(statementInputs);
                }
            } else if (StatementClock.isReplay()) {
                // A replayed unload wrote its files when it first ran, and they are on disk as the statements after it
                // left them: writing them again would refuse over them, or bring back files removed since.
                return null;
            } else {
                // COPY INTO stage FROM table/query
                return executeCopyIntoStage(ctx, targetName, targetDisplay);
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
        requireStorageIntegration(ctx);

        // COPY refuses a missing target in words of its own: the name as the statement wrote it, and no
        // "or not authorized" tail. A name the session cannot place, with no current database, is missing
        // in the same words (live-verified, with a current database and without one).
        final QualifiedName written = QualifiedName.parse(tableName);
        // A load is an INSERT to the account: a view, a stream or an event table named as its target is refused
        // as an INSERT's is.
        executor.requireInsertTarget(tableName);
        executor.requirePrivilege(Privilege.INSERT, "TABLE", tableName);
        if (!executor.getCatalog().hasTableAsWritten(written)) {
            throw new RuntimeException(SqlCompilationError.of("Table '"
                + SqlIdentifiers.spellAlreadyCanonicalPath(String.join(".", written.parts())) + "' does not exist"));
        }
        final Table table = executor.getCatalog().resolveTable(tableName);
        final String fullyQualifiedTableName = executor.getFullyQualifiedTableName(tableName);

        // Parse options
        String fromLocation = null;
        // The FROM location exactly as the statement wrote it — error messages echo THIS, not the
        // canonical name (a stage created as st and read as @st refuses naming '@st/x.csv').
        String fromLocationDisplay = null;
        String pattern = null;
        String matchByColumnName = null;
        String validationMode = null;
        OnErrorOption onError = OnErrorOption.ABORT_STATEMENT;
        boolean force = false;
        boolean purge = false;
        List<String> files = null;
        List<String> columns = null;
        FrostlakeParser.CopyTransformationContext transformation = null;
        StageQueryArguments transformationArguments = null;
        // The format the files are read by, at the account's defaults until a format says otherwise: the stage's
        // own first, then the statement's FILE_FORMAT - the format it names, then the options written beside it
        // (see FileFormatOptions).
        final FileFormatOptions format = new FileFormatOptions();
        // Repeating a copy option is a conflict, live-verified; the inverse pair
        // ENFORCE_LENGTH/TRUNCATECOLUMNS additionally conflicts when both carry one polarity.
        final Set<String> seenCopyOptions = new HashSet<>();
        Boolean enforceLength = null;
        Boolean truncateColumns = null;
        long sizeLimit = -1L;

        rejectCredentialsOnInternalStage(ctx);

        // A COPY that writes no FILE_FORMAT reads the files by the format their stage keeps — a named stage's
        // FILE_FORMAT, a table stage's STAGE_FILE_FORMAT — as a staged-file query and an unload read them
        // (documented): its header is skipped, its delimiter splits the fields.
        final FileFormat stageFormat = stageFormatOf(ctx, tableName);
        if (stageFormat != null) {
            format.takeFormat(stageFormat);
        }

        for (final FrostlakeParser.CopyIntoTableClauseContext clause : ctx.copyIntoTableClause()) {
            if (clause.FROM() != null) {
                // Get source location
                final FrostlakeParser.CopySourceContext sourceCtx = clause.copySource();
                if (sourceCtx.stageRef() != null) {
                    // FROM @stage | @~ | @%table [/path]
                    fromLocation = executor.stageRefToLocation(sourceCtx.stageRef());
                    fromLocationDisplay = ParseTreeText.getOriginalText(sourceCtx.stageRef());
                    executor.requireStagePrivilege(Privilege.READ, sourceCtx.stageRef());
                } else if (sourceCtx.STRING_LITERAL() != null) {
                    // FROM 's3://bucket/path/'
                    fromLocation = ParseTreeText.extractStringLiteral(sourceCtx.STRING_LITERAL());
                    fromLocationDisplay = fromLocation;
                } else if (sourceCtx.copyTransformation() != null) {
                    // FROM (SELECT $1, $2, … FROM @stage) — column transformation over staged file fields.
                    transformation = sourceCtx.copyTransformation();
                    validateTransformationFunctions(transformation);
                    transformationArguments = StageQueryArguments.read(executor, transformation.stageQueryParams());
                    fromLocation = executor.stageRefToLocation(transformation.stageRef());
                    fromLocationDisplay = ParseTreeText.getOriginalText(transformation.stageRef());
                    executor.requireStagePrivilege(Privilege.READ, transformation.stageRef());
                }
            } else if (clause.FILE_FORMAT() != null) {
                requireDistinctCopyOption(seenCopyOptions, "FILE_FORMAT");
                if (clause.copyFormatOptions() != null) {
                    takeWrittenFormat(format, clause.copyFormatOptions().copyFormatOption());
                }
            } else if (clause.PATTERN() != null) {
                pattern = ParseTreeText.extractStringLiteral(clause.STRING_LITERAL());
            } else if (clause.VALIDATION_MODE() != null) {
                requireDistinctCopyOption(seenCopyOptions, "VALIDATION_MODE");
                validationMode = copyOptValue(clause.copyOptionValue());
                if (!validationMode.toUpperCase(Locale.ROOT).matches("RETURN_\\d+_ROWS|RETURN_ERRORS|RETURN_ALL_ERRORS")) {
                    throw invalidCopyOptionValue("VALIDATION_MODE", clause.copyOptionValue());
                }
            } else if (clause.ON_ERROR() != null) {
                requireDistinctCopyOption(seenCopyOptions, "ON_ERROR");
                onError = requireValidOnError(clause.copyOptionValue());
            } else if (clause.FORCE() != null) {
                requireDistinctCopyOption(seenCopyOptions, "FORCE");
                force = requireBareBoolean("FORCE", clause.copyOptionValue());
            } else if (clause.PURGE() != null) {
                requireDistinctCopyOption(seenCopyOptions, "PURGE");
                purge = requireBareBoolean("PURGE", clause.copyOptionValue());
            } else if (clause.SIZE_LIMIT() != null) {
                requireDistinctCopyOption(seenCopyOptions, "SIZE_LIMIT");
                final String limitText = (clause.MINUS() != null ? "-" : "")
                    + clause.INTEGER_LITERAL().getText();
                if (limitText.startsWith("-")) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid value [" + limitText + "] for parameter 'SIZE_LIMIT'"));
                }
                sizeLimit = Long.parseLong(limitText);
            } else if (clause.FILES() != null) {
                files = new ArrayList<>();
                for (final TerminalNode fileNode : clause.stringLiteralList().STRING_LITERAL()) {
                    files.add(ParseTreeText.extractStringLiteral(fileNode));
                }
            } else if (clause.MATCH_BY_COLUMN_NAME() != null) {
                requireDistinctCopyOption(seenCopyOptions, "MATCH_BY_COLUMN_NAME");
                matchByColumnName = copyOptValue(clause.copyOptionValue());
                final String matchMode = matchByColumnName.toUpperCase(Locale.ROOT);
                if (!"CASE_SENSITIVE".equals(matchMode) && !"CASE_INSENSITIVE".equals(matchMode)
                        && !"NONE".equals(matchMode)) {
                    throw invalidCopyOptionValue("MATCH_BY_COLUMN_NAME", clause.copyOptionValue());
                }
            } else if (clause.LPAREN() != null && clause.identifierList() != null) {
                // Column mapping
                columns = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx : clause.identifierList().identifier()) {
                    columns.add(ParseTreeText.getIdentifier(idCtx));
                }
            } else if (clause.identifier() != null && clause.copyOptionValue() != null) {
                // The generic key=value options: ENFORCE_LENGTH / TRUNCATECOLUMNS /
                // RETURN_FAILED_ONLY / LOAD_UNCERTAIN_FILES / INCLUDE_METADATA — an unknown NAME
                // refuses as an invalid parameter, live-verified.
                final String optionName = ParseTreeText.getIdentifier(clause.identifier()).toUpperCase(Locale.ROOT);
                if (StorageIntegrationReference.PROPERTY.equals(optionName)) {
                    // Judged before the table was looked for (see requireStorageIntegration).
                    continue;
                }
                if (!GENERIC_COPY_OPTIONS.contains(optionName)) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid parameter '" + optionName + "'"));
                }
                requireDistinctCopyOption(seenCopyOptions, optionName);
                if ("ENFORCE_LENGTH".equals(optionName)) {
                    enforceLength = requireBareBoolean(optionName, clause.copyOptionValue());
                } else if ("TRUNCATECOLUMNS".equals(optionName)) {
                    truncateColumns = requireBareBoolean(optionName, clause.copyOptionValue());
                } else if ("RETURN_FAILED_ONLY".equals(optionName)
                        || "LOAD_UNCERTAIN_FILES".equals(optionName)) {
                    requireBareBoolean(optionName, clause.copyOptionValue());
                }
            }
        }

        // A transformation's staged-file arguments read the files where the statement writes no FILE_FORMAT or
        // PATTERN of its own.
        if (transformationArguments != null) {
            if (!seenCopyOptions.contains("FILE_FORMAT") && transformationArguments.format() != null) {
                format.takeFormat(transformationArguments.format());
            }
            if (pattern == null && transformationArguments.pattern() != null) {
                pattern = transformationArguments.pattern();
            }
        }
        // The effective format, settled once every clause is read (see FileFormatOptions): the CSV delimiters (NONE
        // as the word), the enclosure and the escapes as characters, NULL_IF (the
        // text \N when no format names one), the header, blank-line, width and field rules, the character set,
        // a JSON format's strip flags, and the input formats the fields' text is read by.
        final String fileFormat = format.getType();
        final String fieldDelimiter = format.getFieldDelimiter() == null ? "NONE" : format.getFieldDelimiter();
        final String recordDelimiter = format.getRecordDelimiter() == null ? "NONE" : format.getRecordDelimiter();
        final int skipHeader = format.getSkipHeader();
        final boolean errorOnColumnCountMismatch = format.checksColumnCount();
        final boolean parseHeader = format.parsesHeader();
        final String encoding = format.getEncoding();
        final boolean replaceInvalidCharacters = format.replacesInvalidCharacters();
        final boolean stripNullValues = format.stripsNullValues();
        final boolean stripOuterArray = format.stripsOuterArray();
        final Map<String, String> loadInputFormats = format.loadInputFormats();

        // ENFORCE_LENGTH and TRUNCATECOLUMNS are each other's inverse; declaring both with the
        // SAME polarity is the same conflict a repeated option raises, named on TRUNCATECOLUMNS.
        if (enforceLength != null && truncateColumns != null
                && enforceLength.booleanValue() == truncateColumns.booleanValue()) {
            throw conflictingCopyOption("TRUNCATECOLUMNS");
        }
        // TRUNCATECOLUMNS = TRUE, or its inverse ENFORCE_LENGTH = FALSE, cuts a text longer than its column to the
        // column's length where the default refuses it (documented).
        final boolean truncateToColumns = truncateColumns != null ? truncateColumns.booleanValue()
            : enforceLength != null && !enforceLength.booleanValue();
        // MATCH_BY_COLUMN_NAME needs a header to match against — CSV only carries one under
        // PARSE_HEADER = TRUE (live's own lowercase wording).
        if (matchByColumnName != null && !"NONE".equalsIgnoreCase(matchByColumnName)
                && "CSV".equalsIgnoreCase(fileFormat) && !parseHeader) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " match_by_column_name option"
                + " is not supported for file format CSV without PARSE_HEADER = TRUE");
        }
        // VALIDATION_MODE and a transformation cannot combine, live-verified.
        if (validationMode != null && transformation != null) {
            throw new RuntimeException(SqlCompilationError.of(
                "VALIDATION_MODE does not support COPY with transform."));
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
            fromLocationDisplay = fromLocation;
        } else if (fromLocation.startsWith("@%") && validationMode == null) {
            // Another table's stage is no source for this one; a validating COPY is not refused for it.
            executor.requireOwnTableStage(fromLocation, table);
        }

        // A semi-structured file yields ONE semi-structured value per record, so it can only load into a
        // single VARIANT / OBJECT / ARRAY column — unless MATCH_BY_COLUMN_NAME splits it, or a COPY
        // transformation projects the fields. Live-verified on a real account: COPY INTO a
        // two-column table with FILE_FORMAT = (TYPE = 'JSON') fails "JSON file format can produce one and
        // only one column of type variant, object, or array. Load data into separate columns using the
        // MATCH_BY_COLUMN_NAME copy option or copy with transformation." — PARQUET reports the same.
        rejectMultiColumnSemiStructuredLoad(fileFormat, table, columns, matchByColumnName, transformation);

        logger.info("COPY INTO {} FROM {} FILE_FORMAT={} PATTERN={} ON_ERROR={} FORCE={} PURGE={}",
                tableName, fromLocation, fileFormat, pattern, onError.spelled(), force, purge);

        // The fields are read as the file format says from here on: its DATE_FORMAT, TIME_FORMAT and TIMESTAMP_FORMAT
        // take the session's input formats' place — AUTO, or none written, leaves the session's own — and its
        // BINARY_FORMAT reads a BINARY column's text (documented). The caller puts the statement's own back.
        SessionInputFormats.override(loadInputFormats);

        final List<ResultSetColumn> resultColumns = Arrays.asList(
            new ResultSetColumn("file", StringType.VARCHAR),
            new ResultSetColumn("status", StringType.VARCHAR),
            new ResultSetColumn("rows_parsed", StatementResultWidths.LOAD_COUNT),
            new ResultSetColumn("rows_loaded", StatementResultWidths.LOAD_COUNT),
            new ResultSetColumn("error_limit", StatementResultWidths.LOAD_COUNT),
            new ResultSetColumn("errors_seen", StatementResultWidths.LOAD_COUNT),
            new ResultSetColumn("first_error", StringType.VARCHAR),
            new ResultSetColumn("first_error_line", StatementResultWidths.LOAD_COUNT),
            new ResultSetColumn("first_error_character", StatementResultWidths.LOAD_COUNT),
            new ResultSetColumn("first_error_column_name", StringType.VARCHAR)
        );
        final List<Row> resultRows = new ArrayList<>();
        // FILES = (…) names its files explicitly, which is what makes a load-history skip visible in the
        // result (see the shape rule above). listCopyFiles already narrows the listing to these names, so
        // every file the load loop sees under a non-empty FILES list was named by the statement.
        final boolean explicitFileList = files != null && !files.isEmpty();
        // ON_ERROR = ABORT_STATEMENT (the default) fails the whole COPY on the first bad record; CONTINUE
        // and the SKIP_FILE family read the file to the end and settle its fate afterwards, per the budget
        // OnErrorOption.errorLimit gives.
        final boolean abortOnError = onError.aborts();

        // Resolve the local directory backing the FROM location (@stage / s3:// / file://) and load CSV or
        // JSON files from it.
        // A stage reference reads its path as a PREFIX of the staged names (see StagePrefix); a URL or a
        // local path keeps its directory reading.
        final StagePrefix staged = executor.resolveStagePrefix(fromLocation);
        final Path baseDir = staged != null ? staged.getRoot() : executor.resolveCopyBaseDir(fromLocation);
        // Every name in FILES = (…) that is not on the stage. Settled BEFORE anything is read, so an
        // aborting statement leaves the table untouched (see missingNamedFiles for the whole rule).
        final List<String> missingFiles = missingNamedFiles(staged, baseDir, files);
        if (!missingFiles.isEmpty() && (validationMode != null || abortOnError)) {
            // The two paths that refuse to load rather than report: VALIDATION_MODE = RETURN_<n>_ROWS, and
            // the default ON_ERROR. RETURN_ERRORS is the exception — it reports the miss as a row and is
            // handled in validateCopyFiles.
            if (validationMode == null) {
                throw new RuntimeException(remoteFileNotFound(fromLocationDisplay, missingFiles.get(0)));
            }
            if (!isReturnErrorsMode(validationMode)) {
                throw new RuntimeException(remoteFileMissingDetail(fromLocationDisplay, missingFiles.get(0)));
            }
        }
        final boolean csv = fileFormat == null || "CSV".equals(fileFormat);
        // Record-based formats (JSON, XML, …) read through a StageFileReader; CSV keeps its positional path.
        final StageFileReader recordReader = StageReaderFactory.forType(fileFormat);
        // A format this engine cannot parse must fail loudly — silently "succeeding" while loading
        // nothing reads as a completed load. Avro/Parquet/ORC ship in the optional frostlake-formats
        // module (a StageFileReader SPI), so their absence gets a pointer rather than a bare rejection.
        if (fileFormat != null && !csv && recordReader == null) {
            final String upperType = fileFormat.toUpperCase(Locale.ROOT);
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
            final LoadedRowRules rowRules = new LoadedRowRules(loadCharset(encoding), replaceInvalidCharacters,
                stripNullValues, stripOuterArray, truncateToColumns, omittedColumns(tableCols, columns, fieldToCol),
                fullyQualifiedTableName, fieldDelimiter, recordDelimiter, matchByColumnName);
            // COPY transformation setup: a synthetic COLUMN1..N table lets $1, $2, … resolve to staged fields.
            final List<FrostlakeParser.CopyTransformItemContext> transformItems =
                transformation != null ? transformation.copyTransformItem() : null;
            final int transformSlots = transformItems != null ? maxPositionalRef(transformItems) : 0;
            final ExpressionEvaluator transformEval = transformItems != null ? buildStageFieldEvaluator(transformSlots) : null;
            final boolean checkColumnCount = csv
                && columnCountChecked(errorOnColumnCountMismatch, columns, matchByColumnName, transformItems);
            // The files this statement reads, in path order — the same list for a load and a validation.
            final List<Path> candidates = staged != null ? stagedCandidates(staged, pattern, files)
                : executor.listCopyFiles(baseDir, pattern, files, executor.copyPatternPrefix(fromLocation));

            // VALIDATION_MODE: validate the staged files WITHOUT loading anything (Snowflake
            // semantics) — RETURN_ERRORS / RETURN_ALL_ERRORS list the rows that would fail;
            // RETURN_<N>_ROWS returns the first N rows exactly as they would load.
            if (validationMode != null) {
                return validateCopyFiles(validationMode, table, tableCols, fieldToCol,
                    transformItems, transformEval, transformSlots,
                    candidates, recordReader, format, checkColumnCount, fromLocationDisplay, missingFiles, rowRules);
            }
            // The load reads the stage's files as they are now, which a replay of its text would not find again.
            CheckpointDebt.owe();

            final Map<String, Set<String>> histories = loadHistory(table.isTemporary());
            final Set<String> committedKeys = histories.get(fullyQualifiedTableName);
            final Set<String> loadedKeys = committedKeys == null ? Collections.<String>emptySet() : committedKeys;
            // The transaction's own loads count as done for it; another open transaction's are not done yet. An
            // autocommit statement's transaction begins with its first write, so it has none of its own yet.
            final Transaction running = executor.getTransactionManager().getCurrentTransaction();
            final String history = historyKey(fullyQualifiedTableName, table.isTemporary());

            // Rows are buffered and inserted only after every file is parsed + validated, so an
            // ABORT_STATEMENT failure leaves the table unchanged (atomic), matching Snowflake.
            final List<Row> pending = new ArrayList<>();
            // PURGE = TRUE: the staged files to delete once the load is committed. Only files this
            // statement actually consumed land here — see purgeLoadedFiles for the exact rule.
            final List<Path> purgeCandidates = new ArrayList<>();
            // The files this statement loaded that the table had not, which count as loaded once it commits.
            final Set<String> addedKeys = new LinkedHashSet<>();
            // The files read, for the load history once the whole COPY has run (an aborted one records none).
            final List<LoadRecord> completedLoads = new ArrayList<>();
            final Schema owningSchema = executor.getCatalog().resolveOwningSchema(tableName);
            // SIZE_LIMIT counts the bytes of the files this statement has read so far; the next file
            // starts only while that total does not EXCEED the limit — so at least one file always
            // loads, and a total exactly at the limit still admits one more.
            long bytesRead = 0L;
            for (final Path file : candidates) {
                final String fileName = StagePathSegments.diskName(file);
                if (sizeLimit >= 0L && bytesRead > sizeLimit) {
                    break;
                }
                // ALTER PIPE … REFRESH PREFIX / MODIFIED_AFTER: narrow the loaded set to matching files
                // (these filters are set only for the duration of a pipe REFRESH; a plain COPY sees none).
                if (!passesRefreshFilters(file, staged != null ? staged.relativeToPath(file)
                        : baseDir.relativize(file).toString().replace('\\', '/'))) {
                    continue;
                }
                final String fileKey = copyFileKey(file);
                // Snowflake skips already-loaded files unless FORCE = TRUE; keeps a re-COPY / pipe REFRESH idempotent.
                if (!force && (loadedKeys.contains(fileKey) || loadedUncommitted(running, history, fileKey)
                        || addedKeys.contains(fileKey))) {
                    // A skipped file is reported only when the statement named it in FILES = (…); one merely
                    // found by listing the stage (or picked by PATTERN) leaves no trace in the result.
                    // Its counters are NOT zeros: the account reports a NULL error_limit (no budget was ever
                    // applied — nothing was read) beside errors_seen = 1 and the skip itself as the file's
                    // first_error. Live-verified and stable across ON_ERROR (default, CONTINUE,
                    // SKIP_FILE_5) and stage kind (table stage, named stage), with a FORCE = TRUE control
                    // re-loading the same file as LOADED.
                    if (explicitFileList) {
                        resultRows.add(new Row(Arrays.asList(executor.stagedDisplayName(fromLocation, file),
                            "LOAD_SKIPPED", 0, 0, null, 1,
                            ALREADY_LOADED_ERROR, null, null, null)));
                    }
                    continue;
                }
                final Transaction loader = force ? null : otherLoaderOf(running, history, fileKey);
                if (loader != null && !refreshing) {
                    // Another session's open transaction loaded the file: the COPY waits for it, as a writer waits
                    // for a table's holder, and runs again over what it left (see TableLockGate). Raised before
                    // anything is read or kept. Where the statement may not wait it loads the file itself, since the
                    // other load is not done until it commits.
                    executor.requireLoadUnheld(loader, fullyQualifiedTableName, table.getName());
                }
                final List<Row> fileRows = new ArrayList<>();
                int parsed = 0;
                int errors = 0;
                // The file's FIRST rejected record — the trailing first_error quartet. Only the first is
                // kept: later rejects raise errors_seen but never overwrite this (see CopyFileError).
                CopyFileError firstError = null;
                try {
                    if (recordReader != null) {
                        final List<JsonNode> records = loadedRecords(recordReader, file, rowRules);
                        for (final JsonNode record : records) {
                            parsed++;
                            try {
                                final Row row = transformItems != null
                                    ? buildJsonTransformedRow(transformItems, tableCols.size(), fieldToCol, record, transformEval, transformSlots)
                                    : buildJsonRow(tableCols, record, rowRules.matchByColumnName());
                                enforceLoadedRow(table, row, rowRules);
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
                        // Streamed line read — materializing the whole file first doubled peak memory
                        // for a large CSV. The one-line lookahead keeps the last-line flag available,
                        // and is advanced BEFORE the blank-line skip so a `continue` can't stall it.
                        try (final BufferedReader reader = StagedFileIo.reader(file, rowRules.charset())) {
                            // The file is split as a staged-file query splits it (CsvRecordReader), so a record
                            // that does not parse is rejected here as the query refuses it.
                            final CsvRecordReader records = CsvRecordReader.forLoad(reader, fileName, format)
                                .expectingWidth(checkColumnCount ? tableCols.size() : -1);
                            records.skipLines(skipHeader);
                            // MATCH_BY_COLUMN_NAME over CSV reads each file's PARSE_HEADER line and
                            // maps fields onto columns by the names it carries, not by position.
                            int[] fileFieldMap = fieldToCol;
                            if (parseHeader && matchByColumnName != null && !"NONE".equalsIgnoreCase(matchByColumnName)) {
                                final List<String> header = records.header(new ArrayList<Boolean>());
                                if (header != null) {
                                    fileFieldMap = headerFieldToCol(header, tableCols,
                                        "CASE_SENSITIVE".equalsIgnoreCase(matchByColumnName));
                                }
                            }
                            // The record's 1-based ordinal among the ones actually read, which decides WHICH
                            // column-count message a mismatch gets (see loadWidthRefusal). Distinct from
                            // `parsed`, which also counts a blank line SKIP_BLANK_LINES passed over.
                            int recordNumber = 0;
                            while (true) {
                                final List<Boolean> enclosed = new ArrayList<>();
                                final List<String> rowFields;
                                try {
                                    rowFields = records.next(-1, recordNumber + 1, enclosed);
                                } catch (final CsvParseFault fault) {
                                    parsed += records.blankLinesSkipped() + 1;
                                    recordNumber++;
                                    errors++;
                                    final CopyFileError described = describeCsvFault(fault, table);
                                    if (firstError == null) {
                                        firstError = described;
                                    }
                                    if (abortOnError) {
                                        throw new RuntimeException(abortStatementError(described, recordNumber,
                                            fileName), fault);
                                    }
                                    records.skipRecordRest();
                                    continue;
                                }
                                parsed += records.blankLinesSkipped();
                                if (rowFields == null) {
                                    break;
                                }
                                parsed++;
                                recordNumber++;
                                Row row = null;
                                try {
                                    settleUnreadableText(rowFields, rowRules.replacesInvalidCharacters());
                                    format.settle(rowFields, enclosed);
                                    if (checkColumnCount) {
                                        final CsvColumnCountException count = records.loadWidthRefusal(
                                            rowFields.size(), table.getName(), columnNames(tableCols),
                                            recordNumber == 1);
                                        if (count != null) {
                                            throw count;
                                        }
                                    }
                                    row = transformItems != null
                                        ? buildTransformedRow(transformItems, tableCols.size(), fieldToCol, rowFields, transformEval, transformSlots)
                                        : buildCopyRow(tableCols, fileFieldMap, rowFields);
                                    enforceLoadedRow(table, row, rowRules);
                                    fileRows.add(row);
                                } catch (final RuntimeException rowError) {
                                    errors++;
                                    final CopyFileError described = describeCsvError(rowError, table, row, records,
                                        fileFieldMap);
                                    if (firstError == null) {
                                        firstError = described;
                                    }
                                    if (abortOnError) {
                                        throw new RuntimeException(abortStatementError(described, recordNumber,
                                            fileName), rowError);
                                    }
                                }
                            }
                        }
                    }
                } catch (final IOException io) {
                    throw new RuntimeException("COPY INTO failed reading file " + fileName + ": " + io.getMessage(), io);
                }
                bytesRead += file.toFile().length();
                // ON_ERROR's per-file budget (OnErrorOption.errorLimit): once a file's rejected-record count reaches
                // it the WHOLE file is dropped, and the records that parsed cleanly go with it — under SKIP_FILE a
                // file with one bad record out of two loads neither.
                final int errorLimit = onError.errorLimit(parsed);
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
                if (!loadFailed && !loadedKeys.contains(fileKey) && !loadedUncommitted(running, history, fileKey)) {
                    addedKeys.add(fileKey);
                }
                // A file counts as loaded — and so as purgeable — unless it is LOAD_FAILED. A clean file
                // with no records at all (header-only, or empty) still counts: it is LOADED with zero rows.
                if (purge && !loadFailed) {
                    purgeCandidates.add(file);
                }
                // A file dropped by a budget it never tripped on rejected records — SKIP_FILE_0 over a
                // wholly CLEAN file — reports the quartet all-null beside errors_seen = 0: there was no
                // first error to describe. A file dropped by a budget it DID trip still describes it.
                final String fileStatus = loadFailed ? "LOAD_FAILED" : (errors == 0 ? "LOADED" : "PARTIALLY_LOADED");
                resultRows.add(new Row(Arrays.asList(executor.stagedDisplayName(fromLocation, file),
                    fileStatus,
                    parsed, rowsLoaded, errorLimit, errors,
                    firstError == null ? null : firstError.getMessage(),
                    firstError == null ? null : Integer.valueOf(firstError.getLine()),
                    firstError == null ? null : firstError.getCharacter(),
                    firstError == null ? null : firstError.getColumnName())));
                completedLoads.add(new LoadRecord(fullyQualifiedTableName, table.isTemporary(),
                    owningSchema.getDatabaseName(), owningSchema.getName(), table.getName(),
                    executor.stagedDisplayName(fromLocation, file), fromLocationDisplay,
                    executor.currentStatementInstant(), fileStatus, parsed, rowsLoaded, sizeOf(file), errors,
                    errorLimit, firstError == null ? null : firstError.getMessage(),
                    firstError == null ? null : Integer.valueOf(firstError.getLine()),
                    firstError == null ? null : firstError.getCharacter(),
                    firstError == null ? null : firstError.getColumnName()));
            }

            // A load is DML (documented): its rows belong to the statement's transaction, seen by other sessions
            // once it commits and gone when it rolls back. The files it loaded, the load history and the purge wait
            // for the commit too: until then the files count as loaded for the transaction alone, and a rollback
            // forgets them, so the next COPY loads them again.
            executor.storeLoadedRows(table, fullyQualifiedTableName, pending);
            final Transaction own = executor.getTransactionManager().getCurrentTransaction();
            final boolean temporary = table.isTemporary();
            final UncommittedLoad load = new UncommittedLoad(history, addedKeys);
            List<UncommittedLoad> ownLoads = uncommittedLoads.get(own);
            if (ownLoads == null) {
                ownLoads = new ArrayList<>();
                uncommittedLoads.put(own, ownLoads);
            }
            ownLoads.add(load);
            own.onOutcome(new TransactionOutcome() {
                @Override
                public void committed() {
                    if (endUncommittedLoad(own, load)) {
                        final Map<String, Set<String>> committed = loadHistory(temporary);
                        Set<String> keys = committed.get(fullyQualifiedTableName);
                        if (keys == null) {
                            keys = new HashSet<>();
                            committed.put(fullyQualifiedTableName, keys);
                        }
                        keys.addAll(addedKeys);
                    }
                    executor.getCatalog().getLoadHistory().add(completedLoads);
                    purgeLoadedFiles(purgeCandidates);
                }

                @Override
                public void rolledBack() {
                    endUncommittedLoad(own, load);
                }
            });
        }

        // The files FILES = (…) named but the stage does not hold. Reaching here means ON_ERROR tolerates
        // them (the aborting default already threw above), so each is reported as its own LOAD_FAILED row,
        // AFTER the files that were really read — the order the account listed them in. The file is left out
        // of the load history, so a later COPY picks it up once it appears (live-verified: a re-COPY of the
        // same name loaded it, LOADED, as soon as the file was staged).
        for (final String missing : missingFiles) {
            resultRows.add(new Row(Arrays.asList(missing, "LOAD_FAILED", 0, 0,
                onError.errorLimit(0), 1, remoteFileMissingDetail(fromLocationDisplay, missing),
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
     * A loaded row held to its table's constraints, once the load's rules have had it (see {@link LoadedRowRules}): a
     * column the column list leaves out takes its DEFAULT or next IDENTITY value, and where the load truncates a text
     * longer than its column is cut to the column's length, counted in characters as the width is — a CSV field and a
     * semi-structured string loaded into a column of its own alike (documented).
     */
    private void enforceLoadedRow(final Table table, final Row row, final LoadedRowRules rules) {
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size() && i < row.getValues().size(); i++) {
            final Object value = row.getValue(i);
            if (rules.isOmitted(i)) {
                row.setValue(i, executor.declaredDefaultFor(table, i, rules.storageName()));
            } else if (rules.truncatesToColumns() && value instanceof String
                    && cols.get(i).getDataType() instanceof StringType) {
                final int width = ((StringType) cols.get(i).getDataType()).getMaxLength();
                if (width > 0 && CodePointText.length((String) value) > width) {
                    row.setValue(i, CodePointText.slice((String) value, 0, width));
                }
            } else {
                // A number too wide for its column is the record's refusal, as an INSERT of it is.
                executor.rejectLoadedValuePastColumnRange(value, cols.get(i));
            }
        }
        executor.enforceColumnConstraints(table, row);
    }

    /** A staged file's size in bytes, as the load history reports it; 0 when the file cannot be measured. */
    private static long sizeOf(final Path file) {
        try {
            return Files.size(file);
        } catch (final IOException unmeasured) {
            return 0L;
        }
    }

    /**
     * A file's records as the load reads them: a JSON file's top-level array split into its elements only under
     * STRIP_OUTER_ARRAY = TRUE, without their null values under STRIP_NULL_VALUES = TRUE (see
     * {@link JsonNullStripping}), and a byte that is no UTF-8 refusing the file unless REPLACE_INVALID_CHARACTERS =
     * TRUE reads it as U+FFFD, as a staged-file query reads it.
     */
    private static List<JsonNode> loadedRecords(final StageFileReader reader, final Path file,
                                                final LoadedRowRules rules) throws IOException {
        final List<JsonNode> records = reader instanceof JsonStageReader
            ? ((JsonStageReader) reader).readRecords(file, rules.stripsOuterArray(), rules.replacesInvalidCharacters(),
                String.valueOf(file.getFileName()))
            : reader.readRecords(file);
        if (rules.stripsNullValues()) {
            for (final JsonNode record : records) {
                JsonNullStripping.strip(record);
            }
        }
        return records;
    }

    /**
     * The character set a load's ENCODING names, UTF-8 when it names none (documented).
     *
     * @param encoding the ENCODING as written, or null
     * @return the set
     */
    private static Charset loadCharset(final String encoding) {
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        final Charset charset = StagedFileIo.charsetFor(encoding);
        if (charset == null) {
            throw new RuntimeException(SqlCompilationError.of("invalid value ['" + encoding
                + "'] for parameter 'ENCODING'"));
        }
        return charset;
    }

    /**
     * A record's fields with the byte sequences the file's encoding could not read settled: each becomes U+FFFD under
     * REPLACE_INVALID_CHARACTERS = TRUE, and otherwise the record is refused (documented).
     */
    private static void settleUnreadableText(final List<String> fields, final boolean replaceInvalidCharacters) {
        for (int i = 0; i < fields.size(); i++) {
            final String field = fields.get(i);
            if (field != null && StagedFileIo.firstUnreadable(field) >= 0) {
                if (!replaceInvalidCharacters) {
                    // The bytes spelled as the account spells them reading a staged file: 'caf0xE9'.
                    throw new RuntimeException("Invalid UTF8 detected in string '"
                        + StagedFileIo.unreadableShown(field) + "'");
                }
                fields.set(i, StagedFileIo.unreadableReplaced(field));
            }
        }
    }

    /**
     * The table columns a COPY's column list leaves out, by position, or null when it writes no list. They take their
     * DEFAULT, or the next IDENTITY value, as an INSERT's would; a sequence cannot be the DEFAULT of one (documented).
     */
    private static boolean[] omittedColumns(final List<TableColumn> tableCols, final List<String> columns,
                                            final int[] fieldToCol) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        final boolean[] omitted = new boolean[tableCols.size()];
        Arrays.fill(omitted, true);
        for (final int column : fieldToCol) {
            if (column >= 0 && column < omitted.length) {
                omitted[column] = false;
            }
        }
        for (int i = 0; i < omitted.length; i++) {
            if (omitted[i] && tableCols.get(i).getDefaultValue() instanceof SequenceDefault) {
                throw new RuntimeException(SqlCompilationError.of("Column " + tableCols.get(i).getName()
                    + " is left out of the COPY column list, and a column left out cannot take a sequence as its"
                    + " default value."));
            }
        }
        return omitted;
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
        if (StatementClock.isReplay()) {
            // The files a replayed load purged are gone already; one staged again under their name since stays.
            return;
        }
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
            final List<Path> candidates,
            final StageFileReader recordReader, final FileFormatOptions format, final boolean checkColumnCount,
            final String fromLocation, final List<String> missingFiles, final LoadedRowRules rowRules) {
        final String mode = validationMode.toUpperCase(Locale.ROOT).replace("'", "").trim();
        final Matcher nRowsMatcher = RETURN_N_ROWS_MODE.matcher(mode);
        final int returnRows = nRowsMatcher.matches() ? Integer.parseInt(nRowsMatcher.group(1)) : -1;

        if (returnRows >= 0) {
            final List<ResultSetColumn> columns = new ArrayList<>();
            for (final TableColumn col : tableCols) {
                columns.add(new ResultSetColumn(col.getName(), col.getDataType()));
            }
            final List<Row> rows = new ArrayList<>();
            for (final Path file : candidates) {
                for (final Row row : parseCopyFileRows(file, table, tableCols, fieldToCol,
                        transformItems, transformEval, transformSlots,
                        recordReader, format, checkColumnCount, null, rowRules)) {
                    rows.add(row);
                    if (rows.size() >= returnRows) {
                        return new ResultSet(columns, rows);
                    }
                }
            }
            return new ResultSet(columns, rows);
        }

        // RETURN_ERRORS / RETURN_ALL_ERRORS — live's twelve columns.
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("ERROR", StringType.VARCHAR),
            new ResultSetColumn("FILE", StringType.VARCHAR),
            new ResultSetColumn("LINE", NumericType.INTEGER),
            new ResultSetColumn("CHARACTER", NumericType.INTEGER),
            new ResultSetColumn("BYTE_OFFSET", NumericType.INTEGER),
            new ResultSetColumn("CATEGORY", StringType.VARCHAR),
            new ResultSetColumn("CODE", NumericType.INTEGER),
            new ResultSetColumn("SQL_STATE", StringType.VARCHAR),
            new ResultSetColumn("COLUMN_NAME", StringType.VARCHAR),
            new ResultSetColumn("ROW_NUMBER", NumericType.INTEGER),
            new ResultSetColumn("ROW_START_LINE", NumericType.INTEGER),
            new ResultSetColumn("REJECTED_RECORD", StringType.VARCHAR));
        final List<Row> errorRows = new ArrayList<>();
        for (final Path file : candidates) {
            parseCopyFileRows(file, table, tableCols, fieldToCol, transformItems, transformEval,
                transformSlots, recordReader, format, checkColumnCount, errorRows, rowRules);
        }
        for (final String missing : missingFiles) {
            // The miss has no record geometry behind it — every positional column is null while the
            // row counters sit at zero, under the other/100112/22000 taxonomy.
            final CopyErrorTaxonomy taxonomy = CopyErrorTaxonomy.missingFile();
            errorRows.add(new Row(Arrays.asList(
                remoteFileMissingDetail(fromLocation, missing), missing, null, null, null,
                taxonomy.getCategory(), taxonomy.getCode(), taxonomy.getSqlState(), null, 0, 0, null)));
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
            final StageFileReader recordReader, final FileFormatOptions format, final boolean checkColumnCount,
            final List<Row> errorCollector, final LoadedRowRules rowRules) {
        final String fileName = StagePathSegments.diskName(file);
        final List<Row> rows = new ArrayList<>();
        try {
            if (recordReader != null) {
                int recordNumber = 0;
                for (final JsonNode record : loadedRecords(recordReader, file, rowRules)) {
                    recordNumber++;
                    try {
                        final Row row = transformItems != null
                            ? buildJsonTransformedRow(transformItems, tableCols.size(), fieldToCol, record, transformEval, transformSlots)
                            : buildJsonRow(tableCols, record, rowRules.matchByColumnName());
                        enforceLoadedRow(table, row, rowRules);
                        rows.add(row);
                    } catch (final RuntimeException rowError) {
                        if (errorCollector == null) {
                            throw new RuntimeException("COPY validation failed on file " + fileName
                                + " record " + recordNumber + ": " + copyErrorMessage(rowError), rowError);
                        }
                        final String message = copyErrorMessage(rowError);
                        final CopyErrorTaxonomy taxonomy = CopyErrorTaxonomy.of(rowError, message);
                        // A record format has no character geometry: the ordinal stands in for every
                        // line-shaped column and the positional ones stay null.
                        errorCollector.add(new Row(Arrays.asList(
                            message, fileName, recordNumber, null, null,
                            taxonomy.getCategory(), taxonomy.getCode(), taxonomy.getSqlState(),
                            null, recordNumber, recordNumber, record.toString())));
                    }
                }
            } else {
                // Streamed line read with a one-line lookahead — see the load path's CSV loop.
                try (final BufferedReader reader = StagedFileIo.reader(file, rowRules.charset())) {
                    // Split as the load splits it (see the load's CSV loop).
                    final CsvRecordReader records = CsvRecordReader.forLoad(reader, fileName, format)
                        .expectingWidth(checkColumnCount ? tableCols.size() : -1);
                    records.skipLines(format.getSkipHeader());
                    int recordNumber = 0;
                    while (true) {
                        final List<Boolean> enclosed = new ArrayList<>();
                        List<String> rowFields = null;
                        RuntimeException rowError = null;
                        Row row = null;
                        try {
                            rowFields = records.next(-1, recordNumber + 1, enclosed);
                        } catch (final CsvParseFault fault) {
                            records.skipRecordRest();
                            rowError = fault;
                        }
                        if (rowError == null && rowFields == null) {
                            break;
                        }
                        recordNumber++;
                        if (rowError == null) {
                            try {
                                settleUnreadableText(rowFields, rowRules.replacesInvalidCharacters());
                                format.settle(rowFields, enclosed);
                                if (checkColumnCount) {
                                    final CsvColumnCountException count = records.loadWidthRefusal(rowFields.size(),
                                        table.getName(), columnNames(tableCols), recordNumber == 1);
                                    if (count != null) {
                                        throw count;
                                    }
                                }
                                row = transformItems != null
                                    ? buildTransformedRow(transformItems, tableCols.size(), fieldToCol, rowFields,
                                        transformEval, transformSlots)
                                    : buildCopyRow(tableCols, fieldToCol, rowFields);
                                enforceLoadedRow(table, row, rowRules);
                                rows.add(row);
                            } catch (final RuntimeException rejected) {
                                rowError = rejected;
                            }
                        }
                        if (rowError == null) {
                            continue;
                        }
                        if (errorCollector == null) {
                            throw new RuntimeException("COPY validation failed on file " + fileName
                                + " line " + records.recordLine() + ": " + copyErrorMessage(rowError), rowError);
                        }
                        // A column-count reject reports the position just past the record, which can be the
                        // FOLLOWING line — the account's RETURN_ERRORS row does the same (its LINE was 3 for
                        // a bad record on line 2, with the record's own line carried separately).
                        final CopyFileError described = rowError instanceof CsvParseFault
                            ? describeCsvFault((CsvParseFault) rowError, table)
                            : describeCsvError(rowError, table, row, records, fieldToCol);
                        final CopyErrorTaxonomy taxonomy = CopyErrorTaxonomy.of(rowError, described.getMessage());
                        // The rejected record is quoted back raw, KEEPING its line terminator —
                        // except for the file's last record, which is quoted without one.
                        final String rejectedRecord = records.goesOn() ? records.rawRecord() + "\n"
                            : records.rawRecord();
                        errorCollector.add(new Row(Arrays.asList(
                            described.getMessage(), fileName, described.getLine(),
                            described.getCharacter(),
                            described.getCharacter() == null
                                ? null : Long.valueOf(records.recordByte() + described.getCharacter() - 1L),
                            taxonomy.getCategory(), taxonomy.getCode(), taxonomy.getSqlState(),
                            described.getColumnName(), recordNumber, records.recordLine(), rejectedRecord)));
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
        this.refreshing = true;
        try {
            // Snowpipe loads serverlessly: the load is no statement of the caller's session. It runs in a session
            // of its own, outside the caller's transaction, takes no query ID and commits by itself.
            final Object[] loaded = new Object[1];
            executor.runInOwnSession(new Runnable() {
                @Override
                public void run() {
                    loaded[0] = executor.executeUnrecorded(copyStatement);
                    executor.getTransactionManager().autocommitStatementEnd();
                }
            });
            return loaded[0];
        } finally {
            this.refreshing = false;
            this.copyRefreshPrefix = null;
            this.copyRefreshModifiedAfter = null;
        }
    }

    /**
     * Whether a staged file passes the active ALTER PIPE … REFRESH filters (always true when none are set). The
     * PREFIX is matched against the file's name relative to the pipe's own COPY location.
     */
    private boolean passesRefreshFilters(final Path file, final String relative) {
        if (copyRefreshPrefix != null) {
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
     * ON_ERROR's value, read as {@link OnErrorOption} reads it; every other value is refused outright, echoed as
     * written (live-verified: {@code ON_ERROR = KEEP_GOING}, {@code = 2}, {@code = '2'} and {@code = 1} each fail
     * compilation with "invalid value [&lt;as written&gt;] for parameter 'ON_ERROR'", so a bare error count is NOT
     * a spelling of the threshold — {@code SKIP_FILE_<n>} is; the {@code %} form must be quoted, the account
     * rejecting a bare {@code %} as a syntax error before the value is ever examined). Accepting a value Snowflake
     * refuses would silently downgrade it here to the aborting default.
     */
    private OnErrorOption requireValidOnError(final FrostlakeParser.CopyOptionValueContext valueCtx) {
        final OnErrorOption option = OnErrorOption.parse(copyOptValue(valueCtx));
        if (option == null) {
            throw new RuntimeException("SQL compilation error:\ninvalid value ["
                + ParseTreeText.getOriginalText(valueCtx) + "] for parameter 'ON_ERROR'");
        }
        return option;
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
    /**
     * The message an ABORT_STATEMENT (default ON_ERROR) COPY fails with, live-verified verbatim:
     * the record's own error on the first line, then two-space-indented {@code File}/{@code Row}
     * lines carrying the geometry the result row would have reported, then the fixed advice
     * sentence. {@code Row} counts data records where {@code line} counts physical lines.
     */
    private static String abortStatementError(final CopyFileError err, final int recordNumber,
            final String fileName) {
        final StringBuilder sb = new StringBuilder(err.getMessage());
        sb.append("\n  File '").append(fileName).append("', line ").append(err.getLine());
        if (err.getCharacter() != null) {
            sb.append(", character ").append(err.getCharacter());
        }
        sb.append("\n  Row ").append(recordNumber);
        if (err.getColumnName() != null) {
            sb.append(", column ").append(err.getColumnName());
        }
        sb.append("\n  If you would like to continue loading when an error is encountered, use other"
            + " values such as 'SKIP_FILE' or 'CONTINUE' for the ON_ERROR option. For more information"
            + " on loading options, please run 'info loading_data' in a SQL client.");
        return sb.toString();
    }

    /**
     * A record the file's splitting refused (see {@link CsvRecordReader}), described as the load's result reports it:
     * the sentence, the place the fault was found, and the column of the field it was found in — named when the
     * table has one there.
     */
    private static CopyFileError describeCsvFault(final CsvParseFault fault, final Table table) {
        final List<TableColumn> cols = table.getColumns();
        final int field = fault.getField();
        final String column = field >= 1 && field <= cols.size()
            ? errorColumnName(table.getName(), cols.get(field - 1).getName(), field)
            : "\"" + table.getName() + "\"[" + field + "]";
        return new CopyFileError(fault.getDetail(), fault.getLine(), Integer.valueOf(fault.getCharacter()), column);
    }

    /** A table's column names, in order. */
    private static List<String> columnNames(final List<TableColumn> cols) {
        final List<String> names = new ArrayList<>(cols.size());
        for (final TableColumn col : cols) {
            names.add(col.getName());
        }
        return names;
    }

    private CopyFileError describeCsvError(final RuntimeException rowError, final Table table, final Row row,
            final CsvRecordReader records, final int[] fieldToCol) {
        final int lineNumber = records.recordLine();
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
                // The field's own start, counted over the record's raw text — enclosure characters included.
                character = Integer.valueOf(records.fieldCharacter(f));
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
     * The files a load from a stage reference reads: every name {@code FILES = (…)} lists that is there, in
     * the order written — each entry appended to the written path as one string — or else every file the
     * path selects, PATTERN-filtered. A statement carrying both ignores the PATTERN, as before.
     */
    private static List<Path> stagedCandidates(final StagePrefix staged, final String pattern,
                                               final List<String> files) {
        if (files == null || files.isEmpty()) {
            return staged.files(pattern);
        }
        final List<Path> named = new ArrayList<>();
        for (final String name : new LinkedHashSet<>(files)) {
            final Path file = staged.named(name);
            if (file != null && Files.isRegularFile(file)) {
                named.add(file);
            }
        }
        return named;
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
    private List<String> missingNamedFiles(final StagePrefix staged, final Path baseDir, final List<String> files) {
        if (files == null || files.isEmpty() || baseDir == null) {
            return new ArrayList<>();
        }
        final List<String> missing = new ArrayList<>();
        for (final String name : new LinkedHashSet<>(files)) {
            final Path resolved = staged != null ? staged.named(name) : executor.resolveStagedFile(baseDir, name);
            if (resolved == null || !Files.isRegularFile(resolved)) {
                missing.add(name);
            }
        }
        return missing;
    }

    /** The generic key=value COPY load options a real account accepts; anything else is 1006. */
    private static final Set<String> GENERIC_COPY_OPTIONS = Set.of(
        "ENFORCE_LENGTH", "TRUNCATECOLUMNS", "RETURN_FAILED_ONLY", "LOAD_UNCERTAIN_FILES",
        "INCLUDE_METADATA");

    /** A repeated copy option conflicts with itself — live's 2306 wording, trailing newline. */
    private void requireDistinctCopyOption(final Set<String> seen, final String name) {
        if (!seen.add(name)) {
            throw conflictingCopyOption(name);
        }
    }

    private static RuntimeException conflictingCopyOption(final String name) {
        return new RuntimeException(SqlCompilationError.conflictingCopyOption(name));
    }

    /**
     * The invalid-value shape with live's three renderings: a quoted string echoes with its
     * quotes, a bareword or negative integer echoes bare.
     */
    private static RuntimeException invalidCopyOptionValue(final String name,
            final FrostlakeParser.CopyOptionValueContext value) {
        final String raw = value.getText();
        final String rendered = value.STRING_LITERAL() != null ? raw : raw.toUpperCase(Locale.ROOT);
        return new RuntimeException(SqlCompilationError.invalidValueForParameter(rendered, name));
    }

    /** A boolean copy option takes a BARE TRUE/FALSE — a quoted or bareword value refuses. */
    private static Boolean requireBareBoolean(final String name,
            final FrostlakeParser.CopyOptionValueContext value) {
        if (value.booleanValue() != null) {
            return value.booleanValue().TRUE() != null;
        }
        throw invalidCopyOptionValue(name, value);
    }

    /**
     * The functions a COPY transformation may call, live-measured: string shaping (SUBSTR, TRIM
     * family, LEFT/RIGHT, REPLACE, SPLIT_PART, pads, LENGTH), the conditional family (IFF,
     * COALESCE, NVL, NULLIF), CONCAT and {@code ||}, every conversion ({@code TO_*} /
     * {@code TRY_TO_*}, TRY_CAST — CAST and {@code ::} are their own node kind and always pass),
     * and PARSE_JSON. UPPER, LOWER, SEQ8 and plain arithmetic are refused — the account's own
     * boundary, quirky as it is.
     */
    private static final Set<String> TRANSFORM_FUNCTION_ALLOWLIST = Set.of(
        "TO_VARCHAR", "SUBSTR", "SUBSTRING", "TRIM", "IFF", "CONCAT", "COALESCE", "NVL",
        "LEFT", "RIGHT", "REPLACE", "LENGTH", "LEN", "NULLIF", "LPAD", "RPAD", "LTRIM",
        "RTRIM", "SPLIT_PART", "PARSE_JSON", "TRY_CAST");

    /**
     * Refuse a COPY transformation that calls a function outside the account's allowlist — at
     * compile time, before any file is read, with live's own casing and wording (a capital-C
     * {@code SQL Compilation error:} unlike every other refusal, and no trailing period).
     * Arithmetic operators are refused by their symbol; unrecognized node kinds pass.
     */
    private void validateTransformationFunctions(final FrostlakeParser.CopyTransformationContext ctx) {
        for (final FrostlakeParser.CopyTransformItemContext item : ctx.copyTransformItem()) {
            final Expression parsed =
                AntlrExpressionParser.parse(ParseTreeText.getOriginalText(item.expression()));
            final String offender = firstDisallowedTransformCall(parsed);
            if (offender != null) {
                throw new RuntimeException(
                    "SQL Compilation error: Function '" + offender + "' not supported within a COPY");
            }
        }
    }

    /** The first disallowed function (or arithmetic operator symbol) in the item's tree, or null. */
    private String firstDisallowedTransformCall(final Expression expr) {
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if (!TRANSFORM_FUNCTION_ALLOWLIST.contains(name)
                    && !name.startsWith("TO_") && !name.startsWith("TRY_TO_")) {
                return call.getFunctionName();
            }
            for (final Expression argument : call.getArguments()) {
                final String inner = firstDisallowedTransformCall(argument);
                if (inner != null) {
                    return inner;
                }
            }
            return null;
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            final String symbol = arithmeticSymbol(binary.getOperator());
            if (symbol != null) {
                return symbol;
            }
            final String left = firstDisallowedTransformCall(binary.getLeft());
            if (left != null) {
                return left;
            }
            return firstDisallowedTransformCall(binary.getRight());
        }
        if (expr instanceof UnaryOperationExpression) {
            return firstDisallowedTransformCall(((UnaryOperationExpression) expr).getOperand());
        }
        if (expr instanceof CastExpression) {
            return firstDisallowedTransformCall(((CastExpression) expr).getExpression());
        }
        if (expr instanceof CaseExpression) {
            final CaseExpression caseExpr = (CaseExpression) expr;
            for (final WhenClause when : caseExpr.getWhenClauses()) {
                final String condition = firstDisallowedTransformCall(when.getCondition());
                if (condition != null) {
                    return condition;
                }
                final String result = firstDisallowedTransformCall(when.getResult());
                if (result != null) {
                    return result;
                }
            }
            return caseExpr.getElseExpression() != null
                ? firstDisallowedTransformCall(caseExpr.getElseExpression()) : null;
        }
        return null;
    }

    /** The SQL symbol of an arithmetic operator, or null for every operator a transform may use. */
    private static String arithmeticSymbol(final BinaryOperator operator) {
        switch (operator) {
            case ADD: return "+";
            case SUBTRACT: return "-";
            case MULTIPLY: return "*";
            case DIVIDE: return "/";
            case MODULO: return "%";
            default: return null;
        }
    }

    /**
     * How a missing staged file is addressed back to the caller: the FROM location as written, then the name
     * as {@code FILES = (…)} wrote it. The account names it by its physical location instead — {@code
     * '@st146/nosuch.csv'} for a named stage, echoed exactly as the statement wrote it, and an internal
     * {@code 'tables/<id>/…'} / {@code 'users/<id>/…'} path for a table or user stage, whose numeric object
     * ids have no analogue here. The stage-relative form is the reproducible part and the part that tells the
     * caller which file it was.
     */
    private static String stagedFileRef(final String fromLocation, final String fileName) {
        if (fromLocation == null || fromLocation.isEmpty()) {
            return fileName;
        }
        // A stage reference that wrote a path names the file by appending the entry to that path as one
        // string — '@st/dir' with 'g' is '@st/dirg', and with '/g' it is '@st/dir/g' — while a bare stage
        // puts one slash between (a bare '@sp' with '/dir/g' is '@sp//dir/g'). Live-verified.
        if (fromLocation.startsWith("@") && stagePathStart(fromLocation) >= 0) {
            return fromLocation + fileName;
        }
        return fromLocation.endsWith("/") ? fromLocation + fileName : fromLocation + "/" + fileName;
    }

    /** Where the path of a written stage reference begins: its first slash outside a quoted name, or -1. */
    private static int stagePathStart(final String reference) {
        boolean quoted = false;
        for (int i = 0; i < reference.length(); i++) {
            final char c = reference.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '/' && !quoted) {
                return i;
            }
        }
        return -1;
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
        return !RETURN_N_ROWS_MODE.matcher(validationMode.toUpperCase(Locale.ROOT).replace("'", "").trim()).matches();
    }

    /**
     * A load that names a STORAGE_INTEGRATION has it looked up before anything else it names: the target table,
     * the stage and its credentials come after, and only another option's own value is judged first — ON_ERROR's,
     * and the parameters an ENCRYPTION group's TYPE takes. Under a VALIDATION_MODE the name is not looked up at all
     * (live-verified).
     *
     * @param ctx the COPY INTO &lt;table&gt; statement
     */
    private void requireStorageIntegration(final FrostlakeParser.CopyIntoStatementContext ctx) {
        FrostlakeParser.CopyOptionValueContext named = null;
        for (final FrostlakeParser.CopyIntoTableClauseContext clause : ctx.copyIntoTableClause()) {
            if (clause.VALIDATION_MODE() != null) {
                return;
            }
            if (named == null && clause.identifier() != null && clause.copyOptionValue() != null
                    && StorageIntegrationReference.PROPERTY.equalsIgnoreCase(
                        ParseTreeText.getIdentifier(clause.identifier()))) {
                named = clause.copyOptionValue();
            }
        }
        if (named == null) {
            return;
        }
        for (final FrostlakeParser.CopyIntoTableClauseContext clause : ctx.copyIntoTableClause()) {
            if (clause.ON_ERROR() != null) {
                requireValidOnError(clause.copyOptionValue());
            }
            if (clause.optionKey() != null && clause.parenOptionList() != null
                    && "ENCRYPTION".equalsIgnoreCase(clause.optionKey().getText())) {
                requireEncryptionParameters(clause.parenOptionList());
            }
        }
        StorageIntegrationReference.require(executor.getCatalog(), executor.getSecurityManager(), named);
    }

    /**
     * An ENCRYPTION group whose TYPE is NONE takes no other parameter: {@code ENCRYPTION = (TYPE='NONE'
     * MASTER_KEY='key')} is refused {@code Incompatible number of parameters for value [TYPE]} (live-verified).
     *
     * @param group the group as written
     */
    private void requireEncryptionParameters(final FrostlakeParser.ParenOptionListContext group) {
        boolean none = false;
        for (final FrostlakeParser.ParenOptionContext option : group.parenOption()) {
            if ("TYPE".equalsIgnoreCase(option.optionKey().getText()) && option.copyOptionValue() != null
                    && "NONE".equalsIgnoreCase(copyOptValue(option.copyOptionValue()))) {
                none = true;
            }
        }
        if (none && group.parenOption().size() > 1) {
            throw new RuntimeException("Incompatible number of parameters for value [TYPE]");
        }
    }

    /** The string value of a copy option: quotes stripped for a STRING_LITERAL, else the literal token text (CSV, GZIP, CONTINUE, …). */
    /**
     * A load from an INTERNAL stage takes no credentials: live refuses them while the statement compiles,
     * naming the user or table stage as a pair and a named stage on its own, and it refuses before it
     * looks for any file. An external location ({@code 's3://…'}) keeps its credentials.
     *
     * @param ctx the COPY INTO &lt;table&gt; statement
     */
    private void rejectCredentialsOnInternalStage(final FrostlakeParser.CopyIntoStatementContext ctx) {
        FrostlakeParser.StageRefContext source = null;
        boolean credentials = false;
        for (final FrostlakeParser.CopyIntoTableClauseContext clause : ctx.copyIntoTableClause()) {
            if (clause.FROM() != null && clause.copySource() != null) {
                source = clause.copySource().stageRef() != null ? clause.copySource().stageRef()
                    : clause.copySource().copyTransformation() != null
                        ? clause.copySource().copyTransformation().stageRef() : null;
            } else if (clause.optionKey() != null && clause.parenOptionList() != null
                    && "CREDENTIALS".equalsIgnoreCase(clause.optionKey().getText())) {
                credentials = true;
            }
        }
        if (!credentials || source == null) {
            return;
        }
        final boolean ownStage = source.TILDE() != null || source.PERCENT() != null;
        throw new RuntimeException("SQL compilation error: \n COPY statements targeting a "
            + (ownStage ? "user or table stage" : "stage") + " do not support credential properties.");
    }

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
        final String type = fileFormat.toUpperCase(Locale.ROOT);
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
            // The column spelled exactly as the name resolves, and only when none is, one in another case: beside
            // a quoted "x", X names the column X (live-verified).
            for (int c = 0; c < tableCols.size(); c++) {
                if (tableCols.get(c).getName().equals(columns.get(f))) {
                    mapped[f] = c;
                    break;
                }
            }
            for (int c = 0; c < tableCols.size() && mapped[f] < 0; c++) {
                if (tableCols.get(c).getName().equalsIgnoreCase(columns.get(f))) {
                    mapped[f] = c;
                }
            }
            if (mapped[f] < 0) {
                throw new RuntimeException("COPY INTO column not found in target table: " + columns.get(f));
            }
        }
        return mapped;
    }

    /**
     * Build one row from a CSV record's fields, placing each field in the column it maps onto (unmapped
     * columns stay NULL). The fields arrive already settled by {@link FileFormatOptions#settle}, so a null is a
     * SQL NULL and an empty string is a real empty string — with ONE exception: a semi-structured target
     * column takes NULL for an empty field whatever EMPTY_FIELD_AS_NULL says. Live-verified with
     * a control beside it: {@code EMPTY_FIELD_AS_NULL = FALSE} loaded an empty field as NULL into both a
     * VARIANT and an ARRAY column (LOADED, no error) where the same statement loaded the empty string into a
     * VARCHAR one.
     */
    /**
     * Map one PARSE_HEADER line's field names onto table columns for MATCH_BY_COLUMN_NAME: each
     * header field takes the column sharing its (trimmed) name, exactly under CASE_SENSITIVE and
     * blindly otherwise; a field naming no column maps nowhere, and an unnamed column stays NULL.
     */
    private static int[] headerFieldToCol(final List<String> headerFields, final List<TableColumn> tableCols,
            final boolean caseSensitive) {
        final int[] map = new int[headerFields.size()];
        for (int f = 0; f < headerFields.size(); f++) {
            map[f] = -1;
            final String header = headerFields.get(f) == null ? "" : headerFields.get(f).trim();
            for (int c = 0; c < tableCols.size(); c++) {
                final String columnName = tableCols.get(c).getName();
                if (caseSensitive ? columnName.equals(header) : columnName.equalsIgnoreCase(header)) {
                    map[f] = c;
                    break;
                }
            }
        }
        return map;
    }

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
     * already settled by {@link FileFormatOptions#settle} — a null is a SQL NULL, and an empty string is
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

    /**
     * Highest positional reference ($1, $2, …) across a transformation's projection expressions
     * (0 if none). Counted from POSITIONAL_PARAMETER tokens in the parse tree — a regex over the
     * flattened text also matched dollar-digits INSIDE string literals ({@code 'price $999999'})
     * and sized the synthetic stage table that wide.
     */
    private int maxPositionalRef(final List<FrostlakeParser.CopyTransformItemContext> items) {
        int max = 0;
        for (final FrostlakeParser.CopyTransformItemContext item : items) {
            max = Math.max(max, maxPositionalIn(item.expression()));
        }
        return max;
    }

    private int maxPositionalIn(final ParseTree node) {
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            if (token.getType() == FrostlakeLexer.POSITIONAL_PARAMETER) {
                return Integer.parseInt(token.getText().substring(1));
            }
            return 0;
        }
        int max = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            max = Math.max(max, maxPositionalIn(node.getChild(i)));
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
    private String copyFileKey(final Path file) {
        // The whole path, not the bare name: a load reaches files at any depth, and dir/a.csv and
        // other/a.csv are two files — live loads the second after the first (live-verified).
        // A file moved inside the directory of its name, when later names continued it, keeps its key.
        final File f = file.toAbsolutePath().normalize().toFile();
        return portable(String.valueOf(StagePathSegments.namedPath(file.toAbsolutePath().normalize()))) + "|"
            + f.length() + "|" + f.lastModified();
    }

    /** Whether a field read so far holds nothing but spaces, which may come before its enclosure. */
    private static boolean blank(final StringBuilder field) {
        for (int i = 0; i < field.length(); i++) {
            if (field.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }

    /** One field's enclosure content — the field itself when no enclosure (or none closed). */
    private static String enclosedContent(final String field, final boolean sawEnclosure,
            final int contentStart, final int contentEnd) {
        if (!sawEnclosure || contentStart < 0) {
            return field;
        }
        final int end = contentEnd >= contentStart ? contentEnd : field.length();
        return field.substring(Math.min(contentStart, field.length()), Math.min(end, field.length()));
    }

    /**
     * Map a JSON record to a row: a single (VARIANT) column gets the whole element; otherwise object fields map to
     * columns by name, as MATCH_BY_COLUMN_NAME says: CASE_SENSITIVE takes a key spelled exactly as the column,
     * CASE_INSENSITIVE a key in any case, the exact spelling first (documented).
     */
    private static Row buildJsonRow(final List<TableColumn> tableCols, final JsonNode record,
                                    final String matchByColumnName) {
        final List<Object> values = new ArrayList<>(tableCols.size());
        if (tableCols.size() == 1) {
            values.add(jsonNodeToValue(record, tableCols.get(0)));
            return new Row(values);
        }
        for (final TableColumn col : tableCols) {
            values.add(jsonNodeToValue(jsonField(record, col.getName(), matchByColumnName), col));
        }
        return new Row(values);
    }

    /**
     * Look up a JSON object field by column name. Under MATCH_BY_COLUMN_NAME = CASE_SENSITIVE only the exact spelling
     * is the column's; under CASE_INSENSITIVE any spelling is, the exact one first; with no mode the column's own,
     * lower-case and upper-case spellings are tried, as before.
     */
    private static JsonNode jsonField(final JsonNode record, final String columnName, final String matchByColumnName) {
        if (record == null || !record.isObject()) {
            return null;
        }
        if ("CASE_SENSITIVE".equalsIgnoreCase(matchByColumnName)) {
            return record.get(columnName);
        }
        if ("CASE_INSENSITIVE".equalsIgnoreCase(matchByColumnName)) {
            final JsonNode exact = record.get(columnName);
            if (exact != null) {
                return exact;
            }
            for (final String name : record.propertyNames()) {
                if (name.equalsIgnoreCase(columnName)) {
                    return record.get(name);
                }
            }
            return null;
        }
        JsonNode value = record.get(columnName);
        if (value == null) {
            value = record.get(columnName.toLowerCase(Locale.ROOT));
        }
        if (value == null) {
            value = record.get(columnName.toUpperCase(Locale.ROOT));
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

    /**
     * The options an unload writes its text by: the target stage's own format, then the statement's FILE_FORMAT
     * clause, a format either names first and the options written beside it after.
     */
    private FileFormatOptions unloadFormat(final String location, final FrostlakeParser.CopyIntoStatementContext ctx) {
        final FileFormatOptions options = new FileFormatOptions();
        final Stage stage = namedStageOf(location);
        if (stage != null) {
            final Map<String, String> own = stage.getFileFormatOptions();
            if (own.get("FORMAT_NAME") != null) {
                takeNamedFormat(options, own.get("FORMAT_NAME"));
            }
            options.takeAll(own);
        }
        for (final FrostlakeParser.CopyIntoStageClauseContext clause : ctx.copyIntoStageClause()) {
            if (clause.FILE_FORMAT() != null && clause.copyFormatOptions() != null) {
                takeWrittenFormat(options, clause.copyFormatOptions().copyFormatOption());
            }
        }
        return options;
    }

    /**
     * The statement's inline FILE_FORMAT list onto the options, read and judged as every such list is (see
     * {@link FileFormatClause}): the format FORMAT_NAME names first, which must be there, then the options
     * written beside it.
     */
    private void takeWrittenFormat(final FileFormatOptions options,
                                   final List<FrostlakeParser.CopyFormatOptionContext> written) {
        final FileFormatClause clause = FileFormatClause.read(written, new FileFormat("WRITTEN", options.getType()),
            true, true);
        if (clause.formatName() != null) {
            FileFormatReference.require(executor.getCatalog(), SqlIdentifiers.canonicalText(clause.formatName()));
            takeNamedFormat(options, clause.formatName());
        }
        options.takeWritten(clause);
    }

    private void takeNamedFormat(final FileFormatOptions options, final String name) {
        final FileFormat named = resolveFileFormat(name);
        if (named != null) {
            options.takeFormat(named);
        }
    }

    /** The named stage a location starts with, or null for a user stage, a table stage or a stage unknown. */
    private Stage namedStageOf(final String location) {
        if (location == null || !location.startsWith("@") || location.startsWith("@~") || location.startsWith("@%")) {
            return null;
        }
        final int slash = location.indexOf('/');
        try {
            return executor.getCatalog().getStage(slash < 0 ? location.substring(1) : location.substring(1, slash));
        } catch (final RuntimeException unknown) {
            return null;
        }
    }

    /**
     * Refuse an unload to a type this engine cannot write, before anything is read or written. The account
     * unloads CSV, JSON and PARQUET only (docs.snowflake.com, COPY INTO &lt;location&gt;: {@code TYPE = CSV | JSON |
     * PARQUET}), so AVRO, ORC and XML are refused; PARQUET is the account's, but no Parquet writer ships here (the
     * frostlake-formats module only reads it), so it is refused as this engine's limit rather than written as CSV.
     */
    private static void requireUnloadableType(final String fileFormat) {
        final String type = fileFormat.toUpperCase(Locale.ROOT);
        if ("CSV".equals(type) || "JSON".equals(type)) {
            return;
        }
        if ("PARQUET".equals(type)) {
            throw new RuntimeException("COPY INTO location: file format type 'PARQUET' is not supported by this"
                + " engine for unloading (supported: CSV, JSON)");
        }
        throw new RuntimeException("COPY INTO location: file format type '" + type
            + "' cannot be unloaded (supported for unloading: CSV, JSON, PARQUET)");
    }

    private Object executeCopyIntoStage(final FrostlakeParser.CopyIntoStatementContext ctx, final String location,
            final String locationDisplay) {
        logger.info("Executing COPY INTO location: {}", location);

        String fromTable = null;
        FrostlakeParser.SelectStatementContext fromSelect = null;
        String fileFormat = "CSV";
        boolean header = false;
        FrostlakeParser.ExpressionContext partitionExpr = null;
        boolean overwrite = false;
        boolean single = false;
        boolean detailedOutput = false;
        String compression = "AUTO";
        String fileExtension = null;
        // PARTITION BY belongs directly after FROM: an option clause before it makes the account
        // stop at the BY with a plain syntax error, live-verified.
        boolean sawOptionClause = false;

        for (final FrostlakeParser.CopyIntoStageClauseContext clause : ctx.copyIntoStageClause()) {
            if (clause.FROM() != null) {
                final FrostlakeParser.CopyTableSourceContext sourceCtx = clause.copyTableSource();
                if (sourceCtx.qualifiedName() != null) {
                    fromTable = ParseTreeText.getQualifiedName(sourceCtx.qualifiedName());
                } else if (sourceCtx.selectStatement() != null) {
                    fromSelect = sourceCtx.selectStatement();
                }
            } else if (clause.FILE_FORMAT() != null && clause.copyFormatOptions() != null) {
                // Read, judged and applied by unloadFormat once every clause is seen.
                sawOptionClause = true;
            } else if (clause.PARTITION() != null) {
                if (sawOptionClause) {
                    final Token by = clause.BY().getSymbol();
                    throw new RuntimeException(SqlCompilationError.of("syntax error line " + by.getLine()
                        + " at position " + by.getCharPositionInLine() + " unexpected 'BY'."));
                }
                partitionExpr = clause.expression();
            } else if (clause.HEADER() != null) {
                sawOptionClause = true;
                // Bare HEADER (no "= TRUE/FALSE") means HEADER = TRUE, per Snowflake.
                header = clause.booleanValue() == null || clause.booleanValue().TRUE() != null;
            } else if (clause.OVERWRITE() != null) {
                sawOptionClause = true;
                overwrite = requireBareBoolean("OVERWRITE", clause.copyOptionValue());
            } else if (clause.SINGLE() != null) {
                sawOptionClause = true;
                single = requireBareBoolean("SINGLE", clause.copyOptionValue());
            } else if (clause.MAX_FILE_SIZE() != null) {
                sawOptionClause = true;
                // The size is a best-effort per-worker target on a real account, so only its
                // validity matters here — a negative refuses with the bare invalid-value echo.
                if (clause.MINUS() != null) {
                    throw new RuntimeException(SqlCompilationError.of("invalid value [-"
                        + clause.INTEGER_LITERAL().getText() + "] for parameter 'MAX_FILE_SIZE'"));
                }
            } else if (clause.VALIDATION_MODE() != null) {
                // VALIDATION_MODE never fits an unload: every value refuses as invalid.
                throw invalidCopyOptionValue("VALIDATION_MODE", clause.copyOptionValue());
            } else if (clause.identifier() != null && clause.copyOptionValue() != null) {
                sawOptionClause = true;
                final String optionName = ParseTreeText.getIdentifier(clause.identifier()).toUpperCase(Locale.ROOT);
                if ("DETAILED_OUTPUT".equals(optionName)) {
                    detailedOutput = requireBareBoolean("DETAILED_OUTPUT", clause.copyOptionValue());
                } else if ("INCLUDE_QUERY_ID".equals(optionName)) {
                    requireBareBoolean("INCLUDE_QUERY_ID", clause.copyOptionValue());
                } else if (StorageIntegrationReference.PROPERTY.equals(optionName)) {
                    // An unload into a stage takes no integration: the stage carries its own (live-verified).
                    throw new RuntimeException("SQL compilation error: \n COPY statements targeting a stage do not"
                        + " support the STORAGE_INTEGRATION property.");
                } else {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid parameter '" + optionName + "'"));
                }
            } else {
                sawOptionClause = true;
            }
        }

        // The text is written by the target stage's own format, then the statement's: delimiters, NULL_IF, the
        // enclosure and the escapes, the type, the compression and the extension alike.
        final FileFormatOptions unloadOptions = unloadFormat(location, ctx);
        if (unloadOptions.getType() != null) {
            fileFormat = unloadOptions.getType();
        }
        if (unloadOptions.getCompression() != null) {
            compression = unloadOptions.getCompression();
        }
        if (unloadOptions.getFileExtension() != null) {
            fileExtension = unloadOptions.getFileExtension();
        }
        requireUnloadableType(fileFormat);
        final CsvUnloadWriter csv = new CsvUnloadWriter(unloadOptions,
            executor.getFunctionRegistry().getFunction("TO_VARCHAR"));

        // Resolve the data to unload — either a table scan or a query result.
        final ResultSet data;
        if (fromSelect != null) {
            data = executor.executeSelectFromContext(fromSelect);
        } else if (fromTable != null) {
            final Table sourceTable = executor.getCatalog().resolveTable(fromTable);
            // An unload reads its table as a query does, so it needs SELECT on it as the query form does
            // (documented: COPY INTO <location> requires SELECT on the table it unloads).
            executor.requireSelect(fromTable);
            if (hasMaskedColumns(sourceTable) || hasProjectionPolicies(sourceTable)
                    || sourceTable.hasRowAccessPolicy()) {
                // An unload reads the table as a query does, so its masking, projection and row access policies
                // apply: the raw scan below wrote the unmasked values, a column no query may project and the rows
                // the policy withholds to the stage. Read through the query that applies them, unrecorded because
                // the read is part of this COPY, not a statement of its own.
                data = executor.executeUnrecorded("SELECT * FROM " + spelledName(fromTable)).get(0);
            } else {
                final List<ResultSetColumn> cols = new ArrayList<>();
                for (final TableColumn tc : sourceTable.getColumns()) {
                    cols.add(new ResultSetColumn(tc.getName(), tc.getDataType()));
                }
                data = new ResultSet(cols, executor.getStorageEngine().getTableStorage(executor.getFullyQualifiedTableName(fromTable)).scan());
            }
        } else {
            throw new RuntimeException("COPY INTO " + location + " requires a FROM clause");
        }

        final boolean json = "JSON".equalsIgnoreCase(fileFormat);
        final int rowsUnloaded = data.getRows().size();

        // TYPE = JSON unloads exactly one column of JSON documents; anything else is the account's
        // plain unsupported-feature refusal (code 2, no compilation prefix), live-verified.
        if (json && !isSingleVariantColumn(data)) {
            throw new RuntimeException(
                "Unsupported feature 'unloading of more than one column or non-json values'.");
        }
        // The default AUTO compression gzips the files (adding .gz); only NONE turns it off. The
        // remaining codecs are not implemented here and must fail loudly rather than write plain.
        final boolean gzip;
        if ("AUTO".equalsIgnoreCase(compression) || "GZIP".equalsIgnoreCase(compression)) {
            gzip = true;
        } else if ("NONE".equalsIgnoreCase(compression)) {
            gzip = false;
        } else {
            throw new RuntimeException("COPY INTO location: COMPRESSION = '" + compression
                + "' is not supported by this engine (supported: AUTO, GZIP, NONE)");
        }

        // Resolve the target directory (file:// stage, mapped s3:// stage, or external location). An
        // unmapped target formats but writes nothing. PARTITION BY splits the rows across per-key files.
        final String extension = fileExtension != null ? fileExtension : (json ? "json" : "csv");
        final StagePrefix target = partitionExpr == null ? executor.resolveStagePrefix(location) : null;
        Path dir;
        String singleFileName = null;
        String unloadFileName = null;
        String unloadRelativeName = null;
        if (target != null) {
            // A stage path is a PREFIX of the names the unload writes, not a directory (live-verified). A path
            // ending in a slash, or none at all, puts the default name `data` after it; any other path IS the
            // name. SINGLE = TRUE writes that name exactly (no extension, compressed or not), and refuses only
            // when a file of exactly that name exists — @st/f1 is written beside a staged f1.csv. Several
            // files are numbered after the name — @st/pfx writes pfx_0_0_0.csv.gz beside @st/pfx/'s
            // pfx/data_0_0_0.csv.gz — and refuse when ANY staged name starts with it: @st/a refuses over a
            // staged ab/x, while a bare @st only minds names starting with data.
            final String written = target.getPrefix();
            final String namePrefix = written.isEmpty() || written.endsWith("/") ? written + "data" : written;
            final String relativeName = single ? namePrefix
                : namePrefix + "_0_0_0." + extension + (gzip ? ".gz" : "");
            if (!overwrite && rowsUnloaded > 0 && target.getRoot() != null
                    && (single ? target.occupied(relativeName) : target.anyFileStartingWith(namePrefix))) {
                throw new RuntimeException("Files already existing at the unload destination: "
                    + locationDisplay + ". Use overwrite option to force unloading.");
            }
            final int slash = relativeName.lastIndexOf('/');
            // Each segment is escaped the way a stage READ escapes it, or a path holding a '.' or '..'
            // segment would be written where no listing could reach it — and would climb out of the
            // stage on the way.
            dir = target.getRoot() == null ? null
                : slash < 0 ? target.getRoot()
                    : StagePathSegments.resolve(target.getRoot(), relativeName.substring(0, slash));
            if (dir != null && !dir.resolve(relativeName.substring(slash + 1)).normalize()
                    .startsWith(target.getRoot())) {
                throw new RuntimeException("Failed to write unload file to " + location
                    + ": the path leaves the stage");
            }
            unloadFileName = relativeName.substring(slash + 1);
            unloadRelativeName = relativeName;
        } else {
            dir = executor.resolveCopyBaseDir(location);
            // SINGLE = TRUE with a file-shaped target writes exactly that name (the compressed bytes
            // keep the given name untouched — no .gz is appended), live-verified.
            if (single && dir != null && !location.endsWith("/") && location.indexOf('/') >= 0) {
                singleFileName = dir.getFileName().toString();
                dir = dir.getParent();
            }
            // An occupied destination refuses without OVERWRITE = TRUE, echoing the target as written. A
            // partitioned unload never collides: each run names its files afresh.
            if (!overwrite && partitionExpr == null && dir != null && Files.isDirectory(dir)
                    && rowsUnloaded > 0) {
                final File[] existing = dir.toFile().listFiles();
                if (existing != null) {
                    for (final File present : existing) {
                        if (present.isFile()) {
                            throw new RuntimeException("Files already existing at the unload destination: "
                                + locationDisplay + ". Use overwrite option to force unloading.");
                        }
                    }
                }
            }
        }

        // SINGLE = TRUE onto a DIRECTORY writes one file named `data`, with no extension at all —
        // not the format's, and not the compression's: CSV, JSON, uncompressed and GZIP all land as
        // `data` (live-verified). Only a multi-file unload carries the numbered name and extension.
        final String defaultName = single
            ? "data" : "data_0_0_0." + extension + (gzip ? ".gz" : "");
        final List<Row> detailRows = new ArrayList<>();
        int inputBytes = 0;
        int outputBytes = 0;
        if (rowsUnloaded > 0) {
            if (partitionExpr != null) {
                final int[] totals = writePartitionedUnload(data, partitionExpr, dir, json, header, csv,
                    location, extension, gzip, detailRows);
                inputBytes = totals[0];
                outputBytes = totals[1];
            } else {
                final String content = json ? formatRowsAsJson(data) : csv.text(data, header);
                final String fileName = unloadFileName != null ? unloadFileName
                    : singleFileName != null ? singleFileName : defaultName;
                inputBytes = content.getBytes(StandardCharsets.UTF_8).length;
                // A stage name that other staged names continue — dir beside dir/g — is written as that
                // directory's own file (see StagePathSegments).
                final Path stageFile = unloadRelativeName == null || dir == null ? null
                    : stageFileToWrite(target.getRoot(), unloadRelativeName, location);
                outputBytes = stageFile != null
                    ? writeUnloadFile(stageFile.getParent(), stageFile.getFileName().toString(), content, gzip,
                        location, rowsUnloaded)
                    : writeUnloadFile(dir, fileName, content, gzip, location, rowsUnloaded);
                detailRows.add(new Row(Arrays.asList(fileName, outputBytes, rowsUnloaded)));
            }
        }

        // DETAILED_OUTPUT = TRUE swaps the summary for one row per written file, bare names.
        if (detailedOutput) {
            final List<ResultSetColumn> detailColumns = new ArrayList<>();
            if (partitionExpr != null) {
                // A partitioned unload's rows say which partition each file holds, ahead of the file.
                detailColumns.add(new ResultSetColumn("PARTITION_NAME", StatementResultWidths.UNLOAD_FILE_NAME));
            }
            detailColumns.add(new ResultSetColumn("FILE_NAME", StatementResultWidths.UNLOAD_FILE_NAME));
            detailColumns.add(new ResultSetColumn("FILE_SIZE", StatementResultWidths.UNLOAD_FILE_COUNT));
            detailColumns.add(new ResultSetColumn("ROW_COUNT", StatementResultWidths.UNLOAD_FILE_COUNT));
            return new ResultSet(detailColumns, detailRows);
        }
        final List<ResultSetColumn> resultColumns = Arrays.asList(
            new ResultSetColumn("rows_unloaded", StatementResultWidths.UNLOAD_COUNT),
            new ResultSetColumn("input_bytes", StatementResultWidths.UNLOAD_COUNT),
            new ResultSetColumn("output_bytes", StatementResultWidths.UNLOAD_COUNT)
        );
        final List<Row> resultRows = new ArrayList<>();
        resultRows.add(new Row(Arrays.asList(rowsUnloaded, inputBytes, outputBytes)));
        return new ResultSet(resultColumns, resultRows);
    }

    /** The local path an unload writes a stage-relative name to, its directories made (see StagePathSegments). */
    private static Path stageFileToWrite(final Path root, final String relativeName, final String location) {
        try {
            return StagePathSegments.fileToWrite(root, relativeName);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write unload file to " + location + ": " + e.getMessage(), e);
        }
    }

    /** Write one unload file's content into {@code dir} (created if needed, gzipped when asked); returns bytes written, or 0 if the target has no local dir. */
    private int writeUnloadFile(final Path dir, final String fileName, final String content,
            final boolean gzip, final String location, final int rowsForLog) {
        if (dir == null) {
            logger.info("COPY INTO {}: target has no local directory; {} rows formatted but not written", location, rowsForLog);
            return 0;
        }
        try {
            Files.createDirectories(dir);
            final Path out = dir.resolve(fileName);
            final byte[] plain = content.getBytes(StandardCharsets.UTF_8);
            final byte[] bytes = gzip ? QueryExecutor.gzipBytes(plain) : plain;
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
    private int[] writePartitionedUnload(final ResultSet data, final FrostlakeParser.ExpressionContext partitionExpr,
            final Path dir, final boolean json, final boolean header, final CsvUnloadWriter csv,
            final String location, final String extension, final boolean gzip, final List<Row> detailRows) {
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
            final String keyStr = key == null ? NULL_PARTITION : key.toString();
            List<Row> bucket = groups.get(keyStr);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(keyStr, bucket);
            }
            bucket.add(row);
        }

        int inputTotal = 0;
        int outputTotal = 0;
        // Each unload names its files after itself, so a rerun ADDS files beside the last run's rather than
        // overwriting them — which is also why a partitioned unload is never refused over existing files.
        final String fileName = "data_" + UUID.randomUUID() + "_0_0_0." + extension + (gzip ? ".gz" : "");
        for (final Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            final ResultSet part = new ResultSet(data.getColumns(), entry.getValue());
            final String content = json ? formatRowsAsJson(part) : csv.text(part, header);
            final String subDir = sanitizePartitionDir(entry.getKey());
            final Path partDir = dir != null ? dir.resolve(subDir) : null;
            inputTotal += content.getBytes(StandardCharsets.UTF_8).length;
            final int written = writeUnloadFile(partDir, fileName, content, gzip,
                location + "/" + subDir, entry.getValue().size());
            outputTotal += written;
            detailRows.add(new Row(Arrays.asList((Object) entry.getKey(), subDir + "/" + fileName, written,
                entry.getValue().size())));
        }
        return new int[] {inputTotal, outputTotal};
    }

    /** Make a partition-key value safe to use as a directory name (non-alphanumerics → underscore). */
    private String sanitizePartitionDir(final String key) {
        if (NULL_PARTITION.equals(key)) {
            return NULL_PARTITION;   // the NULL partition's own name, which the listing shows as written
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '=' ? c : '_');
        }
        return sb.length() == 0 ? "_" : sb.toString();
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
        // One column whose rows are all NULL — or none at all — unloads cleanly: there is no
        // non-JSON value to refuse over, and an empty unload writes nothing anyway.
        return true;
    }

    /**
     * The file format a COPY INTO a table that writes no FILE_FORMAT reads its files by: the one its stage keeps — a
     * named stage's FILE_FORMAT, a table stage's STAGE_FILE_FORMAT — whether it names a file format or writes its
     * options out. Null when the statement writes a FILE_FORMAT, reads no stage, or the stage keeps no format.
     */
    private FileFormat stageFormatOf(final FrostlakeParser.CopyIntoStatementContext ctx, final String tableName) {
        FrostlakeParser.StageRefContext stage = null;
        boolean from = false;
        for (final FrostlakeParser.CopyIntoTableClauseContext clause : ctx.copyIntoTableClause()) {
            if (clause.FILE_FORMAT() != null) {
                return null;
            }
            if (clause.FROM() != null) {
                from = true;
                final FrostlakeParser.CopySourceContext source = clause.copySource();
                stage = source.stageRef() != null ? source.stageRef()
                    : source.copyTransformation() != null ? source.copyTransformation().stageRef() : null;
            }
        }
        // A COPY with no FROM reads the target table's own stage.
        final String location = stage != null ? executor.stageRefToLocation(stage) : from ? null : "@%" + tableName;
        if (location == null) {
            return null;
        }
        final Map<String, String> kept = executor.stageFileFormat(location);
        if (kept.get("FORMAT_NAME") != null) {
            // Read where the stage lives, and refused when it names a format that is not there (see
            // QueryExecutor.stageNamedFormat): a COPY never falls back to the default CSV in its place.
            return executor.stageNamedFormat(location, kept.get("FORMAT_NAME"));
        }
        if (kept.isEmpty()) {
            return null;
        }
        final FileFormat format = new FileFormat("STAGE",
            kept.get("TYPE") != null ? kept.get("TYPE").toUpperCase(Locale.ROOT) : "CSV");
        // A stage keeps its options as a named format keeps its own, NULL_IF included.
        for (final Map.Entry<String, String> option : kept.entrySet()) {
            if (!"TYPE".equalsIgnoreCase(option.getKey())) {
                format.setOption(option.getKey().toUpperCase(Locale.ROOT), option.getValue());
            }
        }
        return format;
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

    /**
     * Whether a column of the table carries a projection policy: an unload projects every column, so it is read
     * through the query that refuses one the policy restricts, never straight from storage.
     */
    private static boolean hasProjectionPolicies(final Table table) {
        for (final TableColumn column : table.getColumns()) {
            if (column.hasProjectionPolicy()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a masking policy applies to any of the table's columns (masking needs the security manager). */
    private boolean hasMaskedColumns(final Table table) {
        if (executor.getSecurityManager() == null) {
            return false;
        }
        for (final TableColumn column : table.getColumns()) {
            if (column.hasMaskingPolicy()) {
                return true;
            }
        }
        return false;
    }

    /** A canonical qualified name spelled back as SQL, each part quoted only where it has to be. */
    private static String spelledName(final String canonicalQualifiedName) {
        final String[] parts = QualifiedName.parse(canonicalQualifiedName).parts();
        final StringBuilder spelled = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            spelled.append(i > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(parts[i]));
        }
        return spelled.toString();
    }
}
