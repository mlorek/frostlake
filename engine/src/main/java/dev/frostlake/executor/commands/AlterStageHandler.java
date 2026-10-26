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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.FileFormatClause;
import dev.frostlake.executor.FileFormatReference;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StageDirectoryRefresh;
import dev.frostlake.executor.StorageIntegrationReference;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ALTER STAGE, as the account reads it (live-verified):
 *
 * <pre>
 *   SET p1 = … [,] p2 = …       several properties at once, a comma between them or none; a repeated one counts
 *                               once, by its last value; nothing is applied when one of them is refused
 *   SET COMMENT = NULL          takes the comment away; = 5 is  invalid value [5] for parameter 'COMMENT'
 *   SET FOO = 'x'               invalid property 'FOO' for 'STAGE'
 *   SET URL | CREDENTIALS | ENCRYPTION on an internal stage
 *                               Cannot set URL, credentials, or encryption key of an internal or temporary stage.
 *   SET DIRECTORY = ( … )       see {@link StageDirectoryOptions}
 *   UNSET …                     Unsupported feature 'UNSET'.
 *   REFRESH [SUBPATH = '…']     registers the stage's files in its directory table and answers what changed;
 *                               see {@link StageDirectoryRefresh}
 *   REFRESH FOO = 'x'           Invalid Alter Stage Refresh Option FOO   only the first option is read
 *   REFRESH SUBPATH = 5 | NULL  invalid value [5] for parameter 'SUBPATH'
 * </pre>
 *
 * IF EXISTS forgives the stage's absence alone, and not for a REFRESH, which refuses a missing stage either way.
 */
final class AlterStageHandler {

    private static final Logger logger = LoggerFactory.getLogger(AlterStageHandler.class);

    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;
    private final Catalog catalog;
    private Stage altered;

    AlterStageHandler(final QueryExecutor queryExecutor, final SQLCommandVisitor visitor) {
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
        this.catalog = queryExecutor.getCatalog();
    }

    /** The stage a statement changed, for its LAST_DDL; null after a REFRESH, which changes no definition. */
    Stage altered() {
        return altered;
    }

    /**
     * Runs an ALTER STAGE.
     *
     * @param ctx      the statement
     * @param ifExists whether it was written with IF EXISTS
     * @return what a REFRESH answers, or null for a statement that answers the standard status
     */
    ResultSet alter(final FrostlakeParser.AlterStatementContext ctx, final boolean ifExists) {
        final String stageName = visitor.getText(ctx.qualifiedName());
        final FrostlakeParser.StageActionContext action = ctx.stageAction();
        if (action.REFRESH() != null) {
            requireOptionValues(action);
        }
        if (ifExists && action.REFRESH() == null && !exists(stageName)) {
            logger.debug("Stage does not exist (IF EXISTS): {}", stageName);
            return null;
        }
        // Named exactly: a quoted name in another case is another stage, which is not there (live-verified).
        final Stage stage = catalog.getStageExact(stageName);
        if (action.RENAME() == null) {
            queryExecutor.requirePrivilege(Privilege.MODIFY, "STAGE", stageName);
        }
        if (action.REFRESH() != null) {
            return refresh(stage, stageName, action);
        }
        if (action.UNSET() != null) {
            // Live's own refusal — UNSET simply is not a stage action there.
            throw new RuntimeException("Unsupported feature 'UNSET'.");
        }
        if (action.RENAME() != null) {
            queryExecutor.requireOwnership("STAGE", ParseTreeText.qualifiedNameParts(ctx.qualifiedName()), null);
            final String newName = visitor.getText(action.qualifiedName());
            // A renamed stage is created under its new name: a name a stage of that schema holds is refused
            // first, and the schema is then asked for CREATE STAGE (live-verified).
            final String[] newParts = ParseTreeText.qualifiedNameParts(action.qualifiedName());
            final Schema target = newParts.length == 1 ? catalog.resolveOwningSchema(stageName)
                : catalog.resolveOwningSchema(newName);
            DDLCommandHandler.refuseTakenName(true, target.hasStageExact(newParts[newParts.length - 1]), newParts);
            queryExecutor.requireCreatePrivilege(Privilege.CREATE_STAGE, target);
            catalog.renameStage(stageName, newName);
            logger.trace("Renamed stage {} to {}", stageName, newName);
        } else {
            // Judged whole before anything is applied, so a refused property leaves the others unset.
            for (final FrostlakeParser.StageSettingContext setting : action.stageSetting()) {
                set(stage, setting, false);
            }
            for (final FrostlakeParser.StageSettingContext setting : action.stageSetting()) {
                set(stage, setting, true);
            }
            logger.trace("Set properties on stage: {}", stageName);
        }
        altered = stage;
        return null;
    }

    private boolean exists(final String stageName) {
        try {
            return catalog.getStageExact(stageName) != null;
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    /** One property of a SET: judged, and applied when asked to. */
    private void set(final Stage stage, final FrostlakeParser.StageSettingContext setting, final boolean apply) {
        if (setting.URL() != null) {
            refuseOnInternalStage(stage);
            CloudStageUrls.require(visitor.extractStringLiteral(setting.STRING_LITERAL()));
            if (apply) {
                stage.setUrl(visitor.extractStringLiteral(setting.STRING_LITERAL()));
            }
        } else if (setting.FILE_FORMAT() != null) {
            setFileFormat(stage, setting, apply);
        } else if (setting.COMMENT() != null) {
            // A session variable is read into the value's place: a text, or NULL for no comment (see
            // PropertyVariables).
            final FrostlakeParser.CopyOptionValueContext value = setting.copyOptionValue();
            final TerminalNode text = setting.STRING_LITERAL() != null ? setting.STRING_LITERAL()
                : value == null ? null : value.STRING_LITERAL();
            final boolean none = setting.NULL() != null
                || value != null && value.getToken(FrostlakeParser.NULL, 0) != null;
            if (text == null && !none) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                    ParseTreeText.getOriginalText(value), "COMMENT"));
            }
            if (apply) {
                stage.setComment(none ? null : visitor.extractStringLiteral(text));
            }
        } else if (setting.COPY_OPTIONS() != null) {
            if (setting.stageCopyOptionList() != null) {
                // The list judged takes the place of the stage's whole set (live-verified).
                final Map<String, String> options = StageCopyOptions.read(setting.stageCopyOptionList());
                if (apply) {
                    stage.getCopyOptions().clear();
                    stage.getCopyOptions().putAll(options);
                }
            }
        } else {
            setNamed(stage, setting, apply);
        }
    }

    private void setFileFormat(final Stage stage, final FrostlakeParser.StageSettingContext setting,
                               final boolean apply) {
        if (setting.STRING_LITERAL() != null) {
            final String named = visitor.extractStringLiteral(setting.STRING_LITERAL());
            FileFormatReference.require(catalog, SqlIdentifiers.canonicalText(named));
            if (apply) {
                stage.setFileFormat(named);
                stage.getFileFormatOptions().clear();
                stage.getFileFormatOptions().put("FORMAT_NAME", named);
            }
        } else if (setting.qualifiedName() != null) {
            final String named = visitor.getText(setting.qualifiedName());
            FileFormatReference.require(catalog, named);
            if (apply) {
                stage.setFileFormat(named);
                stage.getFileFormatOptions().clear();
                stage.getFileFormatOptions().put("FORMAT_NAME", ParseTreeText.getOriginalText(setting.qualifiedName()));
            }
        } else {
            // Read and judged as every FILE_FORMAT list is (see FileFormatClause), before anything is applied.
            final FileFormatClause written = FileFormatClause.read(setting.parenOptionList(),
                new FileFormat("WRITTEN", "CSV"), true);
            if (written.formatName() != null) {
                FileFormatReference.require(catalog, SqlIdentifiers.canonicalText(written.formatName()));
            }
            if (apply) {
                // A new format replaces the stage's own whole: an option it does not write is back at its
                // default (live-verified).
                stage.getFileFormatOptions().clear();
                if (written.formatName() != null) {
                    stage.setFileFormat(written.formatName());
                    stage.getFileFormatOptions().put("FORMAT_NAME", written.formatNameWritten());
                } else if (written.writtenType() != null) {
                    stage.setFileFormat(written.writtenType().toUpperCase(Locale.ROOT));
                    stage.getFileFormatOptions().put("TYPE", written.writtenType());
                }
                stage.getFileFormatOptions().putAll(written.format().getOptions());
            }
        }
    }

    /** A property named by a plain word: DIRECTORY and COPY_OPTIONS are modeled, the other known ones inert. */
    private void setNamed(final Stage stage, final FrostlakeParser.StageSettingContext setting, final boolean apply) {
        final String key = setting.ENCRYPTION() != null ? "ENCRYPTION"
            : visitor.getText(setting.identifier()).toUpperCase(Locale.ROOT);
        if (!CreateInfrastructureHandler.isStageOption(key)) {
            // Named as written: invalid property 'foo' for 'STAGE' (live-verified).
            throw ParameterRegistry.invalidProperty(setting.identifier().getText(), "STAGE");
        }
        if ("CREDENTIALS".equals(key) || "ENCRYPTION".equals(key)) {
            refuseOnInternalStage(stage);
        } else if ("STORAGE_INTEGRATION".equals(key)) {
            requireIntegration(setting);
        } else if ("DIRECTORY".equals(key)) {
            final StageDirectoryOptions options = StageDirectoryOptions.read(setting.parenOptionList(), false);
            if (apply) {
                options.applyTo(stage);
            }
        } else if ("COPY_OPTIONS".equals(key) && setting.parenOptionList() != null) {
            // The list judged takes the place of the stage's whole set (live-verified).
            final Map<String, String> options = StageCopyOptions.read(setting.parenOptionList());
            if (apply) {
                stage.getCopyOptions().clear();
                stage.getCopyOptions().putAll(options);
            }
        }
    }

    /** A URL, credentials and an encryption key belong to an external stage alone. */
    private static void refuseOnInternalStage(final Stage stage) {
        if (stage.getUrl() == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Cannot set URL, credentials, or encryption key of an internal or temporary stage."));
        }
    }

    /** STORAGE_INTEGRATION names an integration that exists. */
    private void requireIntegration(final FrostlakeParser.StageSettingContext setting) {
        StorageIntegrationReference.require(catalog, queryExecutor.getSecurityManager(),
            setting.copyOptionValue());
    }

    /**
     * A REFRESH option written without its {@code = value} is a syntax error at what follows its name: the next
     * option, the semicolon, or the end of the statement.
     */
    private static void requireOptionValues(final FrostlakeParser.StageActionContext action) {
        for (final FrostlakeParser.StageRefreshOptionContext option : action.stageRefreshOption()) {
            if (option.EQ() == null) {
                throw SyntaxAfter.unexpected(option);
            }
        }
    }

    /**
     * REFRESH: the first option alone is read, and it is SUBPATH; what follows it is not looked at.
     */
    private ResultSet refresh(final Stage stage, final String stageName,
                              final FrostlakeParser.StageActionContext action) {
        String subpath = "";
        final List<FrostlakeParser.StageRefreshOptionContext> options = action.stageRefreshOption();
        if (!options.isEmpty()) {
            final FrostlakeParser.StageRefreshOptionContext first = options.get(0);
            final String name = visitor.getText(first.identifier()).toUpperCase(Locale.ROOT);
            if (!"SUBPATH".equals(name)) {
                throw new RuntimeException("Invalid Alter Stage Refresh Option " + name);
            }
            subpath = subpathOf(first);
        }
        if (!stage.isDirectoryEnabled()) {
            throw new RuntimeException("DIRECTORY not enabled for the stage " + stage.getName());
        }
        final Schema schema = catalog.resolveOwningSchema(stageName);
        final String qualified = schema.getDatabaseName() + "." + schema.getName() + "." + stage.getName();
        final ResultSet answer = new ResultSet(StageDirectoryRefresh.columns(),
            StageDirectoryRefresh.refresh(queryExecutor, stage, schema.getDatabaseName(), qualified, subpath));
        logger.trace("Refreshed directory of stage: {}", stageName);
        return answer;
    }

    /** A SUBPATH's value: a string, or a bare word read as written; NULL and a number are no path. */
    private String subpathOf(final FrostlakeParser.StageRefreshOptionContext option) {
        final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
        if (value == null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter("null", "SUBPATH"));
        }
        if (value.STRING_LITERAL() != null) {
            return visitor.extractStringLiteral(value.STRING_LITERAL());
        }
        if (value.INTEGER_LITERAL() != null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                ParseTreeText.getOriginalText(value), "SUBPATH"));
        }
        return ParseTreeText.getOriginalText(value);
    }
}
