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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.udf.RoutineImports;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles CREATE FUNCTION and CREATE PROCEDURE (UDF / stored-procedure creation across SQL/JS/Python/Java/
 * Scala), extracted from {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here.
 * Column/type parsing is delegated to {@link ColumnDefinitionParser}; shared schema/body helpers are reached
 * via the {@code ddl} back-reference.
 */
public class CreateRoutineHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateRoutineHandler.class);

    /** The languages whose runtime is versioned, and so require RUNTIME_VERSION. */
    private static final Set<String> VERSIONED_RUNTIMES = Set.of("PYTHON", "SCALA");

    /** The languages that run an inline body and so take neither RUNTIME_VERSION nor HANDLER. */
    private static final Set<String> INLINE_BODY_LANGUAGES = Set.of("SQL", "JAVASCRIPT");

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;

    CreateRoutineHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor,
                         final ColumnDefinitionParser columnParser) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.columnParser = columnParser;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handleCreateFunction(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;

        // MEMOIZABLE's place among the options is a SYNTAX rule on the account, so it is judged first.
        rejectMisplacedMemoizable(ctx);
        rejectRepeatedSignatureNames(ctx);

        // A '?' in the body is refused before anything else looks at it: a DEFINITION may carry no
        // unnamed bind, and the sentence is the definition's own rather than the unset-bind one.
        if (functionLanguage(ctx) == UdfLanguage.SQL) {
            BindsInDefinition.rejectInBody(
                ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null);
        }
        // Compile the body first, as Snowflake does: a SQL UDF whose body does not parse is rejected at CREATE
        // time. Deliberately OUTSIDE the try below — a compilation error is not an "already exists" condition
        // and must never be swallowed by IF NOT EXISTS.
        RoutineBodyCompiler.compileFunctionBody(queryExecutor, functionLanguage(ctx),
            ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null,
            ctx.returnType() != null && ctx.returnType().TABLE() != null,
            parts[parts.length - 1], signatureNames(ctx),
            parts.length >= 3 ? parts[0] : catalog.getCurrentDatabase(),
            parts.length >= 3 ? parts[1] : parts.length == 2 ? parts[0] : catalog.getCurrentSchema());

        try {
            final Schema schema;
            final String functionName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                functionName = parts[0];
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw NoCurrentDatabaseRefusal.forStatement();
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                functionName = parts[1];
            } else if (parts.length == 3) {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                functionName = parts[2];
            } else {
                throw new RuntimeException("Invalid function name: " + qualifiedName);
            }

            ddl.checkCreatePrivilege(Privilege.CREATE_FUNCTION, ContainerType.SCHEMA, schema.getName());

            // Parse parameters first
            final List<Parameter> parameters = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext paramCtx : ctx.parameterList().parameterDef()) {
                    parameters.add(columnParser.parseParameterDef(paramCtx));
                }
            }

            // Handle OR REPLACE - drop specific overload with matching signature
            if (orReplace) {
                try {
                    final List<DataType> argumentTypes = new ArrayList<>();
                    for (final Parameter param : parameters) {
                        argumentTypes.add(param.getDataType());
                    }
                    schema.dropFunctionBySignature(functionName, argumentTypes);
                    logger.trace("Dropped existing function overload for OR REPLACE: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // Function with this signature doesn't exist, which is fine for OR REPLACE
                    logger.trace("No existing function with matching signature to replace: {}", qualifiedName);
                }
            }

            boolean isTableFunction = false;
            final DataType returnType;
            final List<Parameter> returnColumns = new ArrayList<>();
            if (ctx.returnType() == null) {
                throw new RuntimeException("RETURNS clause is required for CREATE FUNCTION");
            } else if (ctx.returnType().TABLE() != null) {
                isTableFunction = true;
                returnType = StringType.VARCHAR;
                if (ctx.returnType().columnList() != null) {
                    for (final FrostlakeParser.ColumnOrConstraintContext colCtx : ctx.returnType().columnList().columnOrConstraint()) {
                        if (colCtx.columnDef() != null) {
                            final String colName = ParseTreeText.namePartText(colCtx.columnDef().columnDefName()).toUpperCase();
                            final DataType colType = columnParser.parseDataType(colCtx.columnDef().dataTypeName(), colCtx.columnDef().typeParameters());
                            returnColumns.add(new Parameter(colName, colType));
                        }
                    }
                }
            } else if (ctx.returnType().dataTypeName() != null) {
                returnType = columnParser.parseDataType(ctx.returnType().dataTypeName(), ctx.returnType().typeParameters());
            } else {
                throw new RuntimeException("Invalid return type in CREATE FUNCTION");
            }

            final String body = ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null;

            // Extract options — order-independent via functionOption*
            String language = "SQL";
            String handler = null;
            String runtimeVersion = null;
            String nullHandling = "CALLED ON NULL INPUT";
            String volatility = "VOLATILE";
            String comment = null;
            boolean memoizable = false;
            final List<String> imports = new ArrayList<>();
            rejectRepeatedFunctionOptions(ctx.functionOption());
            for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
                if (opt.languageClause() != null) {
                    language = languageOf(opt.languageClause()).name();
                } else if (opt.handlerClause() != null) {
                    handler = ddl.extractStringLiteral(opt.handlerClause().STRING_LITERAL());
                } else if (opt.runtimeVersionClause() != null) {
                    runtimeVersion = ddl.extractRuntimeVersion(opt.runtimeVersionClause());
                } else if (opt.nullHandlingClause() != null) {
                    final FrostlakeParser.NullHandlingClauseContext nhCtx = opt.nullHandlingClause();
                    if (nhCtx.STRICT() != null) nullHandling = "STRICT";
                    else if (nhCtx.CALLED() != null) nullHandling = "CALLED ON NULL INPUT";
                    else nullHandling = "RETURNS NULL ON NULL INPUT";
                } else if (opt.volatilityClause() != null) {
                    volatility = opt.volatilityClause().IMMUTABLE() != null ? "IMMUTABLE" : "VOLATILE";
                } else if (opt.importsClause() != null) {
                    for (final var sl : opt.importsClause().stringLiteralList().STRING_LITERAL()) {
                        imports.add(ddl.extractStringLiteral(sl));
                    }
                } else if (opt.commentClause() != null) {
                    comment = ddl.extractStringLiteral(opt.commentClause().STRING_LITERAL());
                } else if (opt.MEMOIZABLE() != null) {
                    memoizable = true;
                }
            }
            if (memoizable) {
                requireMemoizableShape(language, isTableFunction, returnType, parameters);
            }

            validateRoutineProperties(language, runtimeVersion, handler);
            requireJavaScriptTypes(language, isTableFunction, returnType, parameters);

            // The declared-vs-actual return-type check runs at CREATE, like the body compile above.
            RoutineReturnTypeChecker.checkScalarSqlUdf(queryExecutor, catalog, language,
                isTableFunction, returnType, parameters, body);

            final Function function = new Function(functionName, parameters, returnType, returnColumns, body, isTableFunction, language, handler, runtimeVersion);

            if (ctx.SECURE() != null) function.setSecure(true);
            function.setTemporary(TemporaryObjectStatements.isTemporary(ctx));
            function.setNullHandling(nullHandling);
            function.setVolatility(volatility);
            if (!imports.isEmpty()) function.setImports(imports);
            if (comment != null) function.setComment(comment);
            function.setMemoizable(memoizable);

            // The body is compiled before the function is registered, for the languages where a real
            // account does — see RoutineBodyCompiler for which those are and why it is not all of them.
            // IMPORTS is checked here too: live refuses a routine whose stage or jar is absent, so the
            // routine must not come into existence when it names one that is not there.
            RoutineImports.validate(function.getImports(), catalog);
            RoutineBodyCompiler.compileFunctionBody(function);

            function.setOwner(catalog.currentRoleForOwner());
            schema.addFunction(function);
            logger.trace("Created function: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Function already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    public Object handleCreateProcedure(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        rejectRepeatedSignatureNames(ctx);
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;

        // A procedure is never memoizable. Its language is judged first, as a function's is (live-verified).
        if (ctx.MEMOIZABLE() != null) {
            requireMemoizableLanguage(languageOf(ctx.languageClause()).name());
            throw memoizableScalarOnly();
        }

        // Compile the body first, as Snowflake does: a LANGUAGE SQL procedure whose body is not a scripting
        // block is rejected at CREATE time. Deliberately OUTSIDE the try below — a compilation error is not an
        // "already exists" condition and must never be swallowed by IF NOT EXISTS.
        RoutineBodyCompiler.compileProcedureBody(queryExecutor, languageOf(ctx.languageClause()),
            ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null,
            parameterNames(ctx.parameterList()));

        try {
            final Schema schema;
            final String procedureName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                procedureName = parts[0];
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw NoCurrentDatabaseRefusal.forStatement();
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                procedureName = parts[1];
            } else if (parts.length == 3) {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                procedureName = parts[2];
            } else {
                throw new RuntimeException("Invalid procedure name: " + qualifiedName);
            }

            ddl.checkCreatePrivilege(Privilege.CREATE_PROCEDURE, ContainerType.SCHEMA, schema.getName());

            // Parse parameters first
            final List<Parameter> parameters = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext paramCtx : ctx.parameterList().parameterDef()) {
                    parameters.add(columnParser.parseParameterDef(paramCtx));
                }
            }

            // Handle OR REPLACE - drop specific overload with matching signature
            if (orReplace) {
                try {
                    final List<DataType> argumentTypes = new ArrayList<>();
                    for (final Parameter param : parameters) {
                        argumentTypes.add(param.getDataType());
                    }
                    schema.dropProcedureBySignature(procedureName, argumentTypes);
                    logger.trace("Dropped existing procedure overload for OR REPLACE: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // Procedure with this signature doesn't exist, which is fine for OR REPLACE
                    logger.trace("No existing procedure with matching signature to replace: {}", qualifiedName);
                }
            }

            final DataType returnType;
            final List<Parameter> procReturnColumns = new ArrayList<>();
            if (ctx.returnType() == null) {
                throw new RuntimeException("RETURNS clause is required for CREATE PROCEDURE");
            } else if (ctx.returnType().TABLE() != null) {
                returnType = StringType.VARCHAR;
                // RETURNS TABLE(col TYPE, ...): keep the declared columns — they name the result of a
                // CALL and of a TABLE(proc(...)) FROM source, exactly as Snowflake names them.
                if (ctx.returnType().columnList() != null) {
                    for (final FrostlakeParser.ColumnOrConstraintContext colCtx : ctx.returnType().columnList().columnOrConstraint()) {
                        if (colCtx.columnDef() != null) {
                            final String colName = ParseTreeText.namePartText(colCtx.columnDef().columnDefName()).toUpperCase();
                            final DataType colType = columnParser.parseDataType(colCtx.columnDef().dataTypeName(), colCtx.columnDef().typeParameters());
                            procReturnColumns.add(new Parameter(colName, colType));
                        }
                    }
                }
            } else if (ctx.returnType().dataTypeName() != null) {
                returnType = columnParser.parseDataType(ctx.returnType().dataTypeName(), ctx.returnType().typeParameters());
            } else {
                throw new RuntimeException("Invalid return type in CREATE PROCEDURE");
            }

            final String body = ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null;

            final String language = languageOf(ctx.languageClause()).name();

            String handler = null;
            if (ctx.handlerClause() != null) {
                handler = ddl.extractStringLiteral(ctx.handlerClause().STRING_LITERAL());
            }

            String runtimeVersion = null;
            if (ctx.runtimeVersionClause() != null) {
                runtimeVersion = ddl.extractRuntimeVersion(ctx.runtimeVersionClause());
            }

            List<String> packages = null;
            if (ctx.packagesClause() != null) {
                packages = new ArrayList<>();
                for (final var stringLiteral : ctx.packagesClause().stringLiteralList().STRING_LITERAL()) {
                    packages.add(ddl.extractStringLiteral(stringLiteral));
                }
            }

            validateRoutineProperties(language, runtimeVersion, handler);
            requireJavaScriptTypes(language, !procReturnColumns.isEmpty(), returnType, parameters);

            final Procedure procedure = new Procedure(procedureName, parameters, returnType, body, language, handler, runtimeVersion, packages);
            procedure.setTemporary(TemporaryObjectStatements.isTemporary(ctx));
            if (!procReturnColumns.isEmpty()) {
                procedure.setReturnColumns(procReturnColumns);
            }

            if (ctx.importsClause() != null) {
                final List<String> importList = new ArrayList<>();
                for (final var sl : ctx.importsClause().stringLiteralList().STRING_LITERAL()) {
                    importList.add(ddl.extractStringLiteral(sl));
                }
                procedure.setImports(importList);
            }

            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                procedure.setComment(comment);
            }

            if (ctx.executeAsClause() != null) {
                final String execAs = ctx.executeAsClause().OWNER() != null ? "OWNER" : "CALLER";
                procedure.setExecuteAs(execAs);
            }

            RoutineImports.validate(procedure.getImports(), catalog);
            RoutineBodyCompiler.compileProcedureBody(procedure);

            procedure.setOwner(catalog.currentRoleForOwner());
            schema.addProcedure(procedure);
            logger.trace("Created procedure: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Procedure already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    /** The declared {@code LANGUAGE} of a routine; {@link UdfLanguage#SQL} when the clause is absent. */
    private static UdfLanguage languageOf(final FrostlakeParser.LanguageClauseContext lc) {
        if (lc == null) {
            return UdfLanguage.SQL;
        }
        if (lc.JAVASCRIPT() != null) {
            return UdfLanguage.JAVASCRIPT;
        }
        if (lc.JAVA() != null) {
            return UdfLanguage.JAVA;
        }
        if (lc.SCALA() != null) {
            return UdfLanguage.SCALA;
        }
        if (lc.PYTHON() != null) {
            return UdfLanguage.PYTHON;
        }
        return UdfLanguage.SQL;
    }

    /**
     * The declared {@code LANGUAGE} of a CREATE FUNCTION, whose options are order-independent so the clause has
     * to be looked up among them. Absent clause → {@link UdfLanguage#SQL}, the Snowflake default.
     */
    /**
     * Each routine option may appear ONCE, and the two families are exclusive within themselves:
     * one volatility ({@code VOLATILE} / {@code IMMUTABLE}) and one null-handling clause
     * ({@code CALLED ON NULL INPUT} / {@code RETURNS NULL ON NULL INPUT} / {@code STRICT}).
     * Live refuses the repeat as a syntax error; the grammar keeps its permissive
     * {@code functionOption*} because the option ORDER is genuinely free, so the check lives here
     * and anchors on the offending option's own first token (live anchors a token or two further
     * along — an accepted error-point divergence). COMMENT is deliberately absent: a routine takes
     * a repeated COMMENT live, unlike a table or a view.
     */
    private static void rejectRepeatedFunctionOptions(
            final List<FrostlakeParser.FunctionOptionContext> options) {
        final Set<String> seen = new HashSet<>();
        for (final FrostlakeParser.FunctionOptionContext opt : options) {
            final String family;
            if (opt.volatilityClause() != null) {
                family = "VOLATILITY";
            } else if (opt.nullHandlingClause() != null) {
                family = "NULL HANDLING";
            } else if (opt.commentClause() != null) {
                continue;   // repeated COMMENT is legal on a routine
            } else {
                family = opt.getStart().getText().toUpperCase(Locale.ROOT);
            }
            if (!seen.add(family)) {
                final Token at = opt.getStart();
                throw new RuntimeException(SqlCompilationError.of("syntax error line " + at.getLine()
                    + " at position " + at.getCharPositionInLine()
                    + " unexpected '" + at.getText() + "'."));
            }
        }
    }

    /**
     * MEMOIZABLE has a fixed place where the other options' order is free: after LANGUAGE, the null-handling
     * clause and the volatility, before COMMENT and the handler, runtime, package and import options, and
     * only once. Live refuses any other placement as a syntax error one token on — at the token after the
     * first word of an option that belongs before it ({@code MEMOIZABLE LANGUAGE SQL} at SQL,
     * {@code MEMOIZABLE IMMUTABLE AS} at AS), or at the token after a MEMOIZABLE that comes too late or a
     * second time ({@code COMMENT = 'c' MEMOIZABLE AS} at AS) (live-verified).
     */
    private static void rejectMisplacedMemoizable(final FrostlakeParser.CreateStatementContext ctx) {
        boolean memoizableSeen = false;
        boolean laterOptionSeen = false;
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            boolean misplaced = false;
            if (opt.MEMOIZABLE() != null) {
                misplaced = memoizableSeen || laterOptionSeen;
                memoizableSeen = true;
            } else if (opt.languageClause() != null || opt.nullHandlingClause() != null
                    || opt.volatilityClause() != null) {
                misplaced = memoizableSeen;
            } else {
                laterOptionSeen = true;
            }
            if (misplaced) {
                throw new RuntimeException(unexpectedAfterFirstWord(ctx, opt));
            }
        }
    }

    /** The syntax error at the token after an option's first word: its own second word, else what follows it. */
    private static String unexpectedAfterFirstWord(final FrostlakeParser.CreateStatementContext ctx,
            final FrostlakeParser.FunctionOptionContext option) {
        final List<Token> words = new ArrayList<>();
        collectWords(option, words, 2);
        Token at = words.size() > 1 ? words.get(1) : null;
        if (at == null) {
            final int index = ctx.children.indexOf(option);
            if (index >= 0 && index + 1 < ctx.getChildCount()) {
                final ParseTree next = ctx.getChild(index + 1);
                at = next instanceof TerminalNode ? ((TerminalNode) next).getSymbol()
                    : ((ParserRuleContext) next).getStart();
            }
        }
        if (at == null || at.getType() == Token.EOF) {
            final Token last = option.getStop();
            return SqlCompilationError.of("syntax error line " + last.getLine() + " at position "
                + (last.getCharPositionInLine() + last.getText().length()) + " unexpected '<EOF>'.");
        }
        return SqlCompilationError.of("syntax error line " + at.getLine() + " at position "
            + at.getCharPositionInLine() + " unexpected '" + at.getText() + "'.");
    }

    /** The first {@code limit} tokens under a parse-tree node, in order. */
    private static void collectWords(final ParseTree node, final List<Token> words, final int limit) {
        if (words.size() >= limit) {
            return;
        }
        if (node instanceof TerminalNode) {
            words.add(((TerminalNode) node).getSymbol());
            return;
        }
        for (int i = 0; i < node.getChildCount() && words.size() < limit; i++) {
            collectWords(node.getChild(i), words, limit);
        }
    }

    /**
     * A MEMOIZABLE function must be a scalar SQL UDF whose result is not a VARIANT or an OBJECT and whose
     * arguments are not semi-structured: an ARRAY result is fine, an ARRAY argument is not. Live judges
     * the language first, then the kind, then the result, then each argument in order (live-verified).
     */
    private static void requireMemoizableShape(final String language, final boolean tableFunction,
            final DataType returnType, final List<Parameter> parameters) {
        requireMemoizableLanguage(language);
        if (tableFunction) {
            throw memoizableScalarOnly();
        }
        final String result = semiStructuredName(returnType);
        if (result != null && !"ARRAY".equals(result)) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Memoizable function does not support "
                + result + " return type.");
        }
        for (final Parameter parameter : parameters) {
            final String argument = semiStructuredName(parameter.getDataType());
            if (argument != null) {
                throw new RuntimeException(SqlCompilationError.of("Unsupported data type '" + argument + "'."));
            }
        }
    }

    private static void requireMemoizableLanguage(final String language) {
        if (!"SQL".equalsIgnoreCase(language)) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Memoizable function supports only SQL language.");
        }
    }

    private static RuntimeException memoizableScalarOnly() {
        return new RuntimeException(SqlCompilationError.PREFIX
            + " Memoizable keyword only supports Scalar SQL UDF with zero arguments.");
    }

    /** VARIANT, OBJECT or ARRAY for a semi-structured type, null for any other. */
    private static String semiStructuredName(final DataType type) {
        if (type instanceof VariantType) {
            return "VARIANT";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        return null;
    }

    /** The canonical names a routine's signature declares, for the scripting scope checks. */
    private static Set<String> parameterNames(final FrostlakeParser.ParameterListContext list) {
        final Set<String> names = new HashSet<>();
        if (list != null) {
            for (final FrostlakeParser.ParameterDefContext def : list.parameterDef()) {
                names.add(ScriptingNameValidator.canonical(def.identifier().getText()));
            }
        }
        return names;
    }

    private static UdfLanguage functionLanguage(final FrostlakeParser.CreateStatementContext ctx) {
        UdfLanguage language = UdfLanguage.SQL;
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.languageClause() != null) {
                language = languageOf(opt.languageClause());
            }
        }
        return language;
    }


    /**
     * The per-language property rules, live-verified and applied to functions and procedures alike.
     * SQL and JAVASCRIPT run an inline body, so naming either RUNTIME_VERSION or HANDLER for one is
     * an invalid property — and the two rejections do NOT share a shape: RUNTIME_VERSION is reported
     * upper-cased against 'FUNCTION', while handler is reported lower-cased against the LANGUAGE's
     * own "&lt;LANG&gt; function", the latter even when the routine is a procedure. PYTHON and SCALA
     * name a versioned runtime and so require RUNTIME_VERSION, whose message carries no
     * compilation-error prefix at all.
     */
    private void validateRoutineProperties(final String language, final String runtimeVersion,
                                           final String handler) {
        final String lang = language.toUpperCase();
        if (INLINE_BODY_LANGUAGES.contains(lang)) {
            if (runtimeVersion != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'RUNTIME_VERSION' for 'FUNCTION'"));
            }
            if (handler != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'handler' for '" + lang + " function'"));
            }
            return;
        }
        if (runtimeVersion == null && VERSIONED_RUNTIMES.contains(lang)) {
            throw new RuntimeException("Property 'runtime_version' must be specified");
        }
        // Every language that loads a compiled/interpreted body names an entry point into it. A body
        // with no handler is not a routine Snowflake can call, so it is refused at CREATE.
        if (handler == null) {
            throw new RuntimeException("Property 'handler' must be specified");
        }
    }

    /**
     * The names the signature declares, upper-cased, for the body's own name resolution. A parameter is
     * matched without regard to case: a body over {@code (x INT)} may write {@code x} or {@code X}.
     */
    private Set<String> signatureNames(final FrostlakeParser.CreateStatementContext ctx) {
        final Set<String> names = new LinkedHashSet<>();
        if (ctx.parameterList() != null) {
            for (final FrostlakeParser.ParameterDefContext parameter : ctx.parameterList().parameterDef()) {
                names.add(getText(parameter.identifier()).toUpperCase());
            }
        }
        return names;
    }


    /**
     * The types JavaScript has no carrier for. A real account refuses one in an ARGUMENT or a SCALAR
     * RETURN at CREATE, in a sentence of its own carrying NO compilation-error prefix and echoing the
     * type canonically: every FIXED-POINT number (INT and DECIMAL fold into it) and TIME. A table
     * return's columns are not reached by the rule, and FLOAT, VARCHAR, BOOLEAN, DATE, all three
     * timestamp flavours, BINARY, VARIANT, OBJECT, ARRAY and GEOGRAPHY are taken (live-verified).
     * MEMOIZABLE's own language refusal comes first, which is why this runs after it.
     */
    private static void requireJavaScriptTypes(final String language, final boolean tableFunction,
                                               final DataType returnType, final List<Parameter> parameters) {
        if (!"JAVASCRIPT".equalsIgnoreCase(language)) {
            return;
        }
        for (final Parameter parameter : parameters) {
            rejectUnsupportedJavaScriptType(parameter.getDataType());
        }
        if (!tableFunction) {
            rejectUnsupportedJavaScriptType(returnType);
        }
    }

    /** One type, judged against what the language carries. */
    private static void rejectUnsupportedJavaScriptType(final DataType type) {
        final boolean fixedPoint = type instanceof NumericType && !NumericType.isApproximate(type);
        final boolean time = type instanceof DateTimeType && "TIME".equalsIgnoreCase(type.getName());
        if (fixedPoint || time) {
            throw new RuntimeException("Language JAVASCRIPT does not support type '"
                + SqlTypeNames.canonical(type) + "' for argument or return type.");
        }
    }


    /**
     * Refuse a signature that repeats a name, before anything else about the routine is judged - the
     * body compile, a block declaring a variable twice and the language's own type rule all come after
     * it. Names are compared as they RESOLVE: an unquoted one folds, so {@code (x INT, X INT)} repeats
     * while {@code ("x" INT, x INT)} is two arguments and is created. The account says "function
     * signature" for a PROCEDURE too, and a duplicate in a TABLE return carries a sentence of its own.
     * Neither sentence takes the compilation-error prefix (live-verified).
     */
    private void rejectRepeatedSignatureNames(final FrostlakeParser.CreateStatementContext ctx) {
        final Set<String> arguments = new LinkedHashSet<>();
        if (ctx.parameterList() != null) {
            for (final FrostlakeParser.ParameterDefContext parameter : ctx.parameterList().parameterDef()) {
                final String name = getText(parameter.identifier());
                if (!arguments.add(name)) {
                    throw new RuntimeException("Argument '" + name + "' repeats in the function signature.");
                }
            }
        }
        final Set<String> columns = new LinkedHashSet<>();
        if (ctx.returnType() != null && ctx.returnType().columnList() != null) {
            for (final FrostlakeParser.ColumnOrConstraintContext column
                    : ctx.returnType().columnList().columnOrConstraint()) {
                if (column.columnDef() == null) {
                    continue;
                }
                final String name = ParseTreeText.namePartText(column.columnDef().columnDefName()).toUpperCase();
                if (!columns.add(name)) {
                    throw new RuntimeException(
                        "Return signature contains a duplicate column name '" + name + "'.");
                }
            }
        }
    }

}
