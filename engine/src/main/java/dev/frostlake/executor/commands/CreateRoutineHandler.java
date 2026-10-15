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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.udf.JavaScriptIdentifiers;
import dev.frostlake.executor.udf.RoutineImports;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.FutureGrants;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.NullHandling;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.metastore.model.Volatility;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * The RUNTIME_VERSION values each language offers, matched as written: {@code '3.11'} and the number
     * {@code 3.11} are one, {@code ' 3.11'} and {@code '3.11.0'} are not (live-verified). Python 3.8 and 3.9 are
     * decommissioned rather than unknown on the account, so they are not refused as invalid values here.
     */
    private static final Map<String, Set<String>> RUNTIME_VERSIONS = Map.of(
        "PYTHON", Set.of("3.8", "3.9", "3.10", "3.11", "3.12", "3.13", "3.14"),
        "JAVA", Set.of("11", "17", "21"),
        "SCALA", Set.of("2.12", "2.13"));

    /**
     * The RUNTIME_VERSION values a language once offered and no longer runs. They are known values, so they
     * pass the invalid-value rule and are refused afterwards by their own sentence.
     */
    private static final Map<String, Set<String>> DECOMMISSIONED_RUNTIMES = Map.of(
        "PYTHON", Set.of("3.8", "3.9"));

    /** The lowest RUNTIME_VERSION a language still runs, as the decommissioned sentence names it. */
    private static final Map<String, String> LOWEST_RUNTIME = Map.of(
        "PYTHON", "3.10");

    /** A LANGUAGE as the decommissioned sentence spells it. */
    private static final Map<String, String> RUNTIME_LANGUAGE_NAMES = Map.of(
        "PYTHON", "Python",
        "JAVA", "Java",
        "SCALA", "Scala");

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

        // What the statement says of itself is judged before any name in it resolves: the options' order (a
        // syntax rule), the language, EXECUTE AS, a VECTOR the language cannot carry and MEMOIZABLE's shape
        // (live-verified).
        RoutineStatementRules.judge(ctx);
        rejectSourcelessCopyGrants(ctx);
        rejectVectorSignature(ctx, functionLanguage(ctx), false, hasMemoizableOption(ctx));
        if (hasMemoizableOption(ctx)) {
            final boolean table = ctx.returnType() != null && ctx.returnType().TABLE() != null;
            requireMemoizableShape(functionLanguage(ctx).name(), table,
                table || ctx.returnType() == null || ctx.returnType().dataTypeName() == null ? StringType.VARCHAR
                    : columnParser.parseDataType(ctx.returnType().dataTypeName(), ctx.returnType().typeParameters()),
                parseParameters(ctx));
        }
        // Then the schema, then — for a plain CREATE — an overload of the same signature. Everything else about
        // the routine is judged after both, and IF NOT EXISTS over an existing overload still judges all of it
        // before it does nothing (live-verified).
        final Schema schema = routineSchema(parts, qualifiedName, "function");
        final String functionName = parts[parts.length - 1];
        final List<Parameter> parameters = parseParameters(ctx);
        // OR REPLACE needs OWNERSHIP of the overload it replaces; the overload itself is dropped only once the
        // new definition has passed every check, so a refused replacement leaves it in place (live-verified).
        final List<DataType> argumentTypes = parameterTypes(parameters);
        // A procedure of this name and signature holds it, whatever the CREATE is written with, and a plain
        // CREATE over an overload of the signature is refused for the name as the statement wrote it: both before
        // the privilege to create a function is asked (live-verified).
        schema.requireNotHeldByOtherKind(true, functionName, parameters);
        DDLCommandHandler.refuseTakenName(!orReplace && !ifNotExists,
            schema.hasFunctionSignature(functionName, parameters), parts);
        // A temporary function needs no privilege on the schema it is named in (live-verified).
        if (TemporaryObjectStatements.isTemporary(ctx)) {
            schema.requireCreatable(Privilege.CREATE_FUNCTION);
        } else {
            ddl.checkCreatePrivilege(Privilege.CREATE_FUNCTION, schema);
        }
        if (orReplace) {
            queryExecutor.requireFunctionOwnership(schema, functionName, argumentTypes);
        }
        // An overload that an existing one would compete with for some call is refused, with OR REPLACE and with
        // IF NOT EXISTS too: neither names the overload it competes with (live-verified).
        schema.requireUnambiguousFunction(functionName, parameters);

        validateRoutineProperties(functionLanguage(ctx).name(), functionRuntimeVersion(ctx), functionHandler(ctx),
            functionImports(ctx), hasImportsOption(ctx), hasPackagesOption(ctx));
        final RoutineEnvironments environments = new RoutineEnvironments(catalog, queryExecutor);
        final RoutineEnvironment environment = environments.read(ctx.functionOption(), functionLanguage(ctx), false,
            ctx.bodyDefinition() != null, orReplace ? replacedFunctionPath(schema, functionName, argumentTypes) : null);
        if (!namesService(ctx)) {
            requireBody(ctx, functionLanguage(ctx), false);
        }
        // A handler body is compiled, and its handler checked against the signature, before the signature's own
        // names are: a Python or Java handler taking the wrong number of arguments is refused for that even when
        // an argument name repeats (live-verified).
        if (!namesService(ctx)) {
            compileHandlerBody(ctx, functionName, environment);
        }
        rejectRepeatedSignatureNames(ctx);

        // A bind in the body is refused before anything else looks at it: a DEFINITION may carry
        // none, and the sentence is the definition's own rather than the unset-bind one.
        if (functionLanguage(ctx) == UdfLanguage.SQL && !namesService(ctx)) {
            BindsInDefinition.rejectInBody(queryExecutor,
                ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null);
        }
        // Compile the body first, as Snowflake does: a SQL UDF whose body does not parse is rejected at CREATE
        // time. Deliberately OUTSIDE the try below — a compilation error is not an "already exists" condition
        // and must never be swallowed by IF NOT EXISTS.
        // A service function's body is the HTTP path its calls go to, not SQL.
        if (!namesService(ctx)) {
            RoutineBodyCompiler.compileFunctionBody(queryExecutor, functionLanguage(ctx),
                ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null,
                ctx.returnType() != null && ctx.returnType().TABLE() != null,
                parts[parts.length - 1], signatureNames(ctx),
                parts.length >= 3 ? parts[0] : catalog.getCurrentDatabase(),
                parts.length >= 3 ? parts[1] : parts.length == 2 ? parts[0] : catalog.getCurrentSchema());
        }
        rejectColumnlessSqlTableFunction(ctx,
            parts.length >= 3 ? parts[0] : catalog.getCurrentDatabase(),
            parts.length >= 3 ? parts[1] : parts.length == 2 ? parts[0] : catalog.getCurrentSchema());

        try {
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
                            // A quoted name as written, as a table procedure's is.
                            final String colName = ParseTreeText.namePartText(colCtx.columnDef().columnDefName());
                            final DataType colType = columnParser.parseReturnColumnType(colCtx.columnDef().dataTypeName(), colCtx.columnDef().typeParameters());
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
            String service = null;
            String endpoint = null;
            Long maxBatchRows = null;
            final List<String> imports = new ArrayList<>();
            // A repeated property is legal and the last one written wins (live-verified).
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
                    imports.clear();
                    imports.addAll(importsOf(opt.importsClause()));
                } else if (opt.commentClause() != null) {
                    comment = ddl.extractComment(opt.commentClause());
                } else if (opt.MEMOIZABLE() != null) {
                    memoizable = true;
                } else if (opt.SERVICE() != null) {
                    service = ServiceFunctions.requireService(catalog, qualifiedNameParts(opt.qualifiedName()));
                } else if (opt.ENDPOINT() != null) {
                    endpoint = opt.identifier() != null ? SqlIdentifiers.canonical(opt.identifier())
                        : ddl.extractStringLiteral(opt.STRING_LITERAL());
                } else if (opt.MAX_BATCH_ROWS() != null) {
                    maxBatchRows = Long.valueOf(opt.INTEGER_LITERAL().getText());
                }
            }
            ServiceFunctions.requireComplete(service, endpoint, body);

            requireJavaScriptTypes(language, isTableFunction, returnType, parameters);
            requireJavaScriptNames(language, functionName, parameters);

            // The declared-vs-actual return-type check runs at CREATE, like the body compile above. A service
            // function's body is the path its calls are sent to, not SQL.
            if (service == null) {
                // The body compiles where the session is, not where the function is created (live-verified).
                final Schema compiledIn = sessionSchemaOr(schema);
                SqlUdfBodyArguments.reject(queryExecutor, language, isTableFunction, parameters, body, compiledIn);
                RoutineReturnTypeChecker.checkScalarSqlUdf(queryExecutor, catalog, language,
                    isTableFunction, returnType, parameters, body, compiledIn);
                if (isTableFunction && "SQL".equals(language)) {
                    SqlTableFunctionReturnTypes.check(queryExecutor, functionName, declaredColumnNames(ctx),
                        parameterTypes(returnColumns), body, compiledIn);
                }
            }

            final Function function = new Function(functionName, parameters, returnType, returnColumns, body, isTableFunction, language, handler, runtimeVersion);

            if (ctx.SECURE() != null) function.setSecure(true);
            function.setReturnSized(Boolean.valueOf(ctx.returnType().typeParameters() != null));
            function.setTemporary(TemporaryObjectStatements.isTemporary(ctx));
            function.setNullHandling(nullHandling);
            function.setVolatility(volatility);
            if (!imports.isEmpty()) function.setImports(RoutineImports.canonical(imports, catalog));
            // A handler its imports hold compiled for a newer Java than the runtime reads is refused here.
            RoutineBodyCompiler.requireReadableHandlerClass(function, queryExecutor);
            if (comment != null) function.setComment(comment);
            function.setMemoizable(memoizable);
            function.setEnvironment(environment);

            // A handler body was compiled before the signature was judged (compileHandlerBody).
            if (service != null) {
                function.setService(service, endpoint, maxBatchRows);
            }

            String grantsKept = null;
            if (orReplace) {
                if (!ctx.copyGrants().isEmpty()) {
                    // COPY GRANTS: the replaced overload's privileges are kept for the new one (live-verified).
                    // They are set aside before the overload goes, which takes its privileges with it.
                    final String replacing = signatureOf(schema, function.getName(), function.getParameters());
                    for (final Function replaced : schema.getFunctionOverloads(functionName)) {
                        if (replacing.equals(signatureOf(schema, replaced.getName(), replaced.getParameters()))) {
                            grantsKept = GrantNames.routine(schema, replaced.getName(), replaced.getParameters());
                            GrantNames.setAside(catalog, "FUNCTION", grantsKept);
                        }
                    }
                }
                try {
                    schema.dropFunctionBySignature(functionName, argumentTypes);
                    logger.trace("Dropped existing function overload for OR REPLACE: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // Function with this signature doesn't exist, which is fine for OR REPLACE
                    logger.trace("No existing function with matching signature to replace: {}", qualifiedName);
                }
            }
            function.setOwner(FutureGrants.ownerOfNewRoutine(catalog, "FUNCTION", schema, function.getName(),
                function.getParameters()));
            schema.addFunction(function);
            keepHandlerClasses(environments, ctx, handler, body, runtimeVersion);
            if (grantsKept != null) {
                GrantNames.takeBack(catalog, "FUNCTION", grantsKept, true);
                GrantNames.renamed(catalog, "FUNCTION", grantsKept,
                    GrantNames.routine(schema, function.getName(), function.getParameters()));
            }
            logger.trace("Created function: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Function already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    /** Keep a created routine's handler classes at its TARGET_PATH, when its options name one. */
    private static void keepHandlerClasses(final RoutineEnvironments environments,
                                           final FrostlakeParser.CreateStatementContext ctx, final String handler,
                                           final String body, final String runtimeVersion) {
        final String targetPath = RoutineEnvironments.writtenTargetPath(ctx.functionOption());
        if (targetPath != null) {
            environments.store(targetPath, functionLanguage(ctx), handler, body, runtimeVersion);
        }
    }

    /** The target path of the function overload a CREATE OR REPLACE replaces, or null when it replaces none. */
    private static String replacedFunctionPath(final Schema schema, final String name,
                                               final List<DataType> argumentTypes) {
        try {
            return schema.getFunctionBySignature(name, argumentTypes).getEnvironment().getTargetPath();
        } catch (final RuntimeException nothingReplaced) {
            return null;
        }
    }

    /** The target path of the procedure overload a CREATE OR REPLACE replaces, or null when it replaces none. */
    private static String replacedProcedurePath(final Schema schema, final String name,
                                                final List<DataType> argumentTypes) {
        try {
            return schema.getProcedureBySignature(name, argumentTypes).getEnvironment().getTargetPath();
        } catch (final RuntimeException nothingReplaced) {
            return null;
        }
    }

    /** A routine's name and argument type families, whatever defaults its parameters carry: what tells an overload. */
    private static String signatureOf(final Schema schema, final String name, final List<Parameter> parameters) {
        return GrantNames.routine(schema, name, parameters).replace("DEFAULT ", "");
    }

    /**
     * COPY GRANTS copies a replaced routine's privileges, so a CREATE that replaces nothing has none to copy
     * (live-verified).
     */
    private static void rejectSourcelessCopyGrants(final FrostlakeParser.CreateStatementContext ctx) {
        if (!ctx.copyGrants().isEmpty() && ctx.or_replace() == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Invalid operation COPY GRANTS without specifying source object. Please use OR REPLACE"));
        }
    }

    /** Whether a CREATE FUNCTION names a SERVICE: a service function. */
    private static boolean namesService(final FrostlakeParser.CreateStatementContext ctx) {
        for (final FrostlakeParser.FunctionOptionContext option : ctx.functionOption()) {
            if (option.SERVICE() != null) {
                return true;
            }
        }
        return false;
    }

    public Object handleCreateProcedure(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;

        // A procedure takes a function's options, in the same order, and is judged in the same phases as a
        // function: what the statement says of itself, then the schema, then an existing overload, then the rest
        // (live-verified).
        RoutineStatementRules.judge(ctx);
        rejectSourcelessCopyGrants(ctx);
        final UdfLanguage procedureLanguage = functionLanguage(ctx);
        rejectVectorSignature(ctx, procedureLanguage, true, hasMemoizableOption(ctx));
        // A procedure is never memoizable. Its language is judged first, as a function's is (live-verified).
        if (hasMemoizableOption(ctx)) {
            requireMemoizableLanguage(procedureLanguage.name());
            throw memoizableScalarOnly();
        }
        final Schema schema = routineSchema(parts, qualifiedName, "procedure");
        final String procedureName = parts[parts.length - 1];
        final List<Parameter> parameters = parseParameters(ctx);
        // OR REPLACE needs OWNERSHIP of the overload it replaces; the overload itself is dropped only once the
        // new definition has passed every check, so a refused replacement leaves it in place.
        final List<DataType> argumentTypes = parameterTypes(parameters);
        // As for a function: a function of this name and signature holds it, and a plain CREATE over an overload
        // of the signature is refused for the name, both before the privilege is asked.
        schema.requireNotHeldByOtherKind(false, procedureName, parameters);
        DDLCommandHandler.refuseTakenName(!orReplace && !ifNotExists,
            schema.hasProcedureSignature(procedureName, parameters), parts);
        if (TemporaryObjectStatements.isTemporary(ctx)) {
            schema.requireCreatable(Privilege.CREATE_PROCEDURE);
        } else {
            ddl.checkCreatePrivilege(Privilege.CREATE_PROCEDURE, schema);
        }
        if (orReplace) {
            queryExecutor.requireProcedureOwnership(schema, procedureName, argumentTypes);
        }
        // As for a function: an overload that would compete with an existing one is refused.
        schema.requireUnambiguousProcedure(procedureName, parameters);

        validateRoutineProperties(procedureLanguage.name(), functionRuntimeVersion(ctx), functionHandler(ctx),
            functionImports(ctx), hasImportsOption(ctx), hasPackagesOption(ctx));
        final RoutineEnvironments environments = new RoutineEnvironments(catalog, queryExecutor);
        final RoutineEnvironment environment = environments.read(ctx.functionOption(), procedureLanguage, true,
            ctx.bodyDefinition() != null, orReplace ? replacedProcedurePath(schema, procedureName, argumentTypes) : null);
        requireBody(ctx, procedureLanguage, true);
        rejectRepeatedSignatureNames(ctx);
        rejectServiceOptions(ctx);

        // Compile the body first, as Snowflake does: a LANGUAGE SQL procedure whose body is not a scripting
        // block is rejected at CREATE time. Deliberately OUTSIDE the try below — a compilation error is not an
        // "already exists" condition and must never be swallowed by IF NOT EXISTS.
        RoutineBodyCompiler.compileProcedureBody(queryExecutor, procedureLanguage,
            ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null,
            parameterNames(ctx.parameterList()));

        try {
            final DataType returnType;
            final List<Parameter> procReturnColumns = new ArrayList<>();
            if (ctx.returnType() == null) {
                throw new RuntimeException("RETURNS clause is required for CREATE PROCEDURE");
            } else if (ctx.returnType().TABLE() != null) {
                returnType = StringType.VARCHAR;
                // RETURNS TABLE(col TYPE, ...): keep the declared columns — they name the result of a
                // CALL and of a TABLE(proc(...)) FROM source, exactly as Snowflake names them, a quoted
                // name as written.
                if (ctx.returnType().columnList() != null) {
                    for (final FrostlakeParser.ColumnOrConstraintContext colCtx : ctx.returnType().columnList().columnOrConstraint()) {
                        if (colCtx.columnDef() != null) {
                            final String colName = ParseTreeText.namePartText(colCtx.columnDef().columnDefName());
                            final DataType colType = columnParser.parseReturnColumnType(colCtx.columnDef().dataTypeName(), colCtx.columnDef().typeParameters());
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

            final String language = procedureLanguage.name();
            final String handler = functionHandler(ctx);
            final String runtimeVersion = functionRuntimeVersion(ctx);

            // A repeated property is legal and the last one written wins (live-verified).
            List<String> packages = null;
            String comment = null;
            NullHandling nullHandling = NullHandling.CALLED_ON_NULL_INPUT;
            Volatility volatility = Volatility.VOLATILE;
            for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
                if (opt.packagesClause() != null) {
                    packages = new ArrayList<>();
                    final FrostlakeParser.StringLiteralListContext named = opt.packagesClause().stringLiteralList();
                    if (named != null) {
                        for (final TerminalNode stringLiteral : named.STRING_LITERAL()) {
                            packages.add(ddl.extractStringLiteral(stringLiteral));
                        }
                    }
                } else if (opt.commentClause() != null) {
                    comment = ddl.extractComment(opt.commentClause());
                } else if (opt.nullHandlingClause() != null) {
                    nullHandling = opt.nullHandlingClause().CALLED() != null ? NullHandling.CALLED_ON_NULL_INPUT
                        : NullHandling.RETURNS_NULL_ON_NULL_INPUT;
                } else if (opt.volatilityClause() != null) {
                    volatility = opt.volatilityClause().IMMUTABLE() != null ? Volatility.IMMUTABLE : Volatility.VOLATILE;
                }
            }

            final boolean procedureReturnsTable = ctx.returnType().TABLE() != null;
            requireJavaScriptTypes(language, procedureReturnsTable, returnType, parameters);
            requireJavaScriptNames(language, procedureName, parameters);

            final Procedure procedure = new Procedure(procedureName, parameters, returnType, body, language, handler, runtimeVersion, packages);
            procedure.setTemporary(TemporaryObjectStatements.isTemporary(ctx));
            procedure.setReturnSized(Boolean.valueOf(ctx.returnType().typeParameters() != null));
            procedure.setSecure(ctx.SECURE() != null);
            procedure.setReturnsTable(procedureReturnsTable);
            procedure.setReturnColumns(procReturnColumns);
            procedure.setNullHandling(nullHandling.getSqlText());
            procedure.setVolatility(volatility.name());
            procedure.setEnvironment(environment);

            if (hasImportsOption(ctx)) {
                procedure.setImports(RoutineImports.canonical(functionImports(ctx), catalog));
            }

            if (comment != null) {
                procedure.setComment(comment);
            }

            if (!ctx.executeAsClause().isEmpty()) {
                procedure.setExecuteAs(executeAsText(ctx.executeAsClause(0)));
            }

            RoutineBodyCompiler.compileProcedureBody(procedure);

            String grantsKept = null;
            if (orReplace) {
                if (!ctx.copyGrants().isEmpty()) {
                    // COPY GRANTS: the replaced overload's privileges are kept for the new one (live-verified).
                    // They are set aside before the overload goes, which takes its privileges with it.
                    final String replacing = signatureOf(schema, procedure.getName(), procedure.getParameters());
                    for (final Procedure replaced : schema.getProcedureOverloads(procedureName)) {
                        if (replacing.equals(signatureOf(schema, replaced.getName(), replaced.getParameters()))) {
                            grantsKept = GrantNames.routine(schema, replaced.getName(), replaced.getParameters());
                            GrantNames.setAside(catalog, "PROCEDURE", grantsKept);
                        }
                    }
                }
                try {
                    schema.dropProcedureBySignature(procedureName, argumentTypes);
                    logger.trace("Dropped existing procedure overload for OR REPLACE: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // Procedure with this signature doesn't exist, which is fine for OR REPLACE
                    logger.trace("No existing procedure with matching signature to replace: {}", qualifiedName);
                }
            }
            procedure.setOwner(FutureGrants.ownerOfNewRoutine(catalog, "PROCEDURE", schema, procedure.getName(),
                procedure.getParameters()));
            schema.addProcedure(procedure);
            keepHandlerClasses(environments, ctx, handler, body, runtimeVersion);
            if (grantsKept != null) {
                GrantNames.takeBack(catalog, "PROCEDURE", grantsKept, true);
                GrantNames.renamed(catalog, "PROCEDURE", grantsKept,
                    GrantNames.routine(schema, procedure.getName(), procedure.getParameters()));
            }
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

    /** Whether a function's options name MEMOIZABLE. */
    private static boolean hasMemoizableOption(final FrostlakeParser.CreateStatementContext ctx) {
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.MEMOIZABLE() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refuse a VECTOR in the signature of a routine whose language cannot carry one: a JAVASCRIPT, JAVA or
     * SCALA function, and every procedure but a PYTHON one. A SQL or PYTHON function takes a VECTOR as an
     * argument, a result or a table column, as a table does. The first VECTOR is named — the arguments in
     * order, then the result, then the table's columns — spelled as the type is ({@code VECTOR(FLOAT, 3)}
     * for {@code VECTOR(FLOAT,3)}). Only MEMOIZABLE's language rule is judged before it; the repeated
     * names, the language's own type and name rules, the properties, the body and an existing routine all
     * come after (live-verified).
     */
    private void rejectVectorSignature(final FrostlakeParser.CreateStatementContext ctx, final UdfLanguage language,
                                       final boolean procedure, final boolean memoizable) {
        if (language == UdfLanguage.PYTHON || !procedure && language == UdfLanguage.SQL) {
            return;
        }
        final DataType vector = firstVector(ctx);
        if (vector == null) {
            return;
        }
        if (memoizable) {
            requireMemoizableLanguage(language.name());
        }
        throw new RuntimeException(SqlCompilationError.of("Unsupported data type '" + vector.getName() + "'."));
    }

    /** The first VECTOR a routine's signature declares, or null: the arguments, the result, the table's columns. */
    private DataType firstVector(final FrostlakeParser.CreateStatementContext ctx) {
        if (ctx.parameterList() != null) {
            for (final FrostlakeParser.ParameterDefContext def : ctx.parameterList().parameterDef()) {
                if (def.dataTypeName().VECTOR() != null) {
                    return columnParser.parseDataType(def.dataTypeName(), def.typeParameters());
                }
            }
        }
        final FrostlakeParser.ReturnTypeContext returns = ctx.returnType();
        if (returns == null) {
            return null;
        }
        if (returns.dataTypeName() != null && returns.dataTypeName().VECTOR() != null) {
            return columnParser.parseDataType(returns.dataTypeName(), returns.typeParameters());
        }
        if (returns.columnList() != null) {
            for (final FrostlakeParser.ColumnOrConstraintContext column : returns.columnList().columnOrConstraint()) {
                if (column.columnDef() != null && column.columnDef().dataTypeName().VECTOR() != null) {
                    return columnParser.parseDataType(column.columnDef().dataTypeName(),
                        column.columnDef().typeParameters());
                }
            }
        }
        return null;
    }

    /** The names a {@code RETURNS TABLE (...)} declares, as they resolve: unquoted upper-cased, quoted as written. */
    private static List<String> declaredColumnNames(final FrostlakeParser.CreateStatementContext ctx) {
        final List<String> names = new ArrayList<>();
        if (ctx.returnType() != null && ctx.returnType().columnList() != null) {
            for (final FrostlakeParser.ColumnOrConstraintContext column
                    : ctx.returnType().columnList().columnOrConstraint()) {
                if (column.columnDef() != null) {
                    names.add(ParseTreeText.namePartText(column.columnDef().columnDefName()));
                }
            }
        }
        return names;
    }

    private static List<DataType> parameterTypes(final List<Parameter> parameters) {
        final List<DataType> types = new ArrayList<>();
        for (final Parameter parameter : parameters) {
            types.add(parameter.getDataType());
        }
        return types;
    }

    /** The declared LANGUAGE among a routine's options; {@link UdfLanguage#SQL} when none is declared. */
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
     * The schema a routine is created in: the current one for a bare name, one of the current database for a
     * two-part name, the named one for a three-part name, each qualifier matched exactly. A database or schema
     * that is not there is refused here, after what the statement says of itself and before anything else
     * (live-verified).
     */
    private Schema routineSchema(final String[] parts, final String qualifiedName, final String kind) {
        if (parts.length == 1) {
            return ddl.resolveCurrentSchema();
        }
        if (parts.length == 2) {
            if (catalog.getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return catalog.databaseExact(catalog.getCurrentDatabase()).schemaExact(parts[0]);
        }
        if (parts.length == 3) {
            return catalog.databaseExact(parts[0]).schemaExact(parts[1]);
        }
        throw new RuntimeException("Invalid " + kind + " name: " + qualifiedName);
    }

    /**
     * The signature's parameters, as declared. A DEFAULT that calls a user-defined function, or reads a name, is
     * refused where it stands (see {@link DefaultExpressionCalls}).
     */
    private List<Parameter> parseParameters(final FrostlakeParser.CreateStatementContext ctx) {
        final List<Parameter> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            final DefaultExpressionCalls defaults = new DefaultExpressionCalls(queryExecutor);
            for (final FrostlakeParser.ParameterDefContext parameter : ctx.parameterList().parameterDef()) {
                final Parameter declared = columnParser.parseParameterDef(parameter);
                if (parameter.expression() == null) {
                    parameters.add(declared);
                    continue;
                }
                defaults.requireParameterDefault(parameter.expression());
                final String told = defaults.toldParameterDefault(parameter.expression());
                parameters.add(told.equals(declared.getDefaultValue()) ? declared : declared.toldAs(told));
            }
        }
        return parameters;
    }

    /**
     * A procedure takes no service-function option. Live refuses SERVICE as a property of no procedure, and the
     * others by their internal names as properties of no function, the sentence a procedure's unknown property
     * takes too: {@code invalid property 'SERVICE_ENDPOINT' for 'FUNCTION'} (live-verified).
     */
    private static void rejectServiceOptions(final FrostlakeParser.CreateStatementContext ctx) {
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            final String refused = opt.SERVICE() != null ? "'SERVICE' for 'PROCEDURE'"
                : opt.ENDPOINT() != null ? "'SERVICE_ENDPOINT' for 'FUNCTION'"
                : opt.MAX_BATCH_ROWS() != null ? "'MAX_BATCH_ROWS' for 'FUNCTION'" : null;
            if (refused != null) {
                throw new RuntimeException(SqlCompilationError.of("invalid property " + refused));
            }
        }
    }

    /**
     * Compile a JAVA, PYTHON or SCALA function's inline body and check its handler against the signature, the way
     * CREATE does before it judges the signature's own names. A signature or result that does not parse is left
     * to the statement's own reading of it, which refuses it there; SQL and JAVASCRIPT bodies are not compiled
     * here (see {@link RoutineBodyCompiler}). The packages it names decide what the body compiles against.
     */
    private void compileHandlerBody(final FrostlakeParser.CreateStatementContext ctx, final String name,
                                    final RoutineEnvironment environment) {
        final UdfLanguage language = functionLanguage(ctx);
        if (language != UdfLanguage.JAVA && language != UdfLanguage.PYTHON && language != UdfLanguage.SCALA
                || ctx.bodyDefinition() == null || ctx.returnType() == null) {
            return;
        }
        final List<Parameter> parameters = new ArrayList<>();
        final List<Parameter> returnColumns = new ArrayList<>();
        final boolean tableFunction = ctx.returnType().TABLE() != null;
        DataType returnType = StringType.VARCHAR;
        try {
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext parameter : ctx.parameterList().parameterDef()) {
                    parameters.add(columnParser.parseParameterDef(parameter));
                }
            }
            if (tableFunction) {
                if (ctx.returnType().columnList() != null) {
                    for (final FrostlakeParser.ColumnOrConstraintContext column
                            : ctx.returnType().columnList().columnOrConstraint()) {
                        if (column.columnDef() != null) {
                            returnColumns.add(new Parameter(
                                ParseTreeText.namePartText(column.columnDef().columnDefName()),
                                columnParser.parseReturnColumnType(column.columnDef().dataTypeName(),
                                    column.columnDef().typeParameters())));
                        }
                    }
                }
            } else if (ctx.returnType().dataTypeName() != null) {
                returnType = columnParser.parseDataType(ctx.returnType().dataTypeName(),
                    ctx.returnType().typeParameters());
            }
        } catch (final RuntimeException unparsed) {
            return;
        }
        final Function function = new Function(name, parameters, returnType, returnColumns,
            ddl.extractBodyDefinition(ctx.bodyDefinition()), tableFunction, language.name(), functionHandler(ctx),
            functionRuntimeVersion(ctx));
        function.setReturnSized(Boolean.valueOf(ctx.returnType() != null && ctx.returnType().typeParameters() != null));
        final List<String> imports = functionImports(ctx);
        if (!imports.isEmpty()) {
            function.setImports(imports);
        }
        function.setEnvironment(environment);
        RoutineBodyCompiler.compileFunctionBody(function);
    }


    /** The session's current schema, or {@code fallback} when the session has none. */
    private Schema sessionSchemaOr(final Schema fallback) {
        if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
            return fallback;
        }
        try {
            return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
        } catch (final RuntimeException dropped) {
            return fallback;
        }
    }

    /**
     * The per-language property rules, live-verified and applied to functions and procedures alike, judged
     * once the schema has resolved and before a repeated argument name and the body compile, in this order:
     * <ol>
     *   <li>SQL and JAVASCRIPT run an inline body, so an IMPORTS list — even an empty one — is refused as
     *       {@code invalid property 'imports'; feature 'dependency import list' not enabled}, then a
     *       RUNTIME_VERSION or a HANDLER is an invalid property, then a PACKAGES list — even an empty one —
     *       is {@code invalid property 'PACKAGES' for 'FUNCTION'}, whatever order they are written in. The
     *       RUNTIME_VERSION and handler rejections do NOT share a shape: RUNTIME_VERSION is reported upper-cased
     *       against 'FUNCTION', while handler is reported lower-cased against the LANGUAGE's own
     *       "&lt;LANG&gt; function", the latter even when the routine is a procedure;</li>
     *   <li>an IMPORTS stage or file that is not there, since the routine must not come into existence when
     *       it names one;</li>
     *   <li>PYTHON and SCALA name a versioned runtime and so require RUNTIME_VERSION, whose message carries
     *       no compilation-error prefix at all;</li>
     *   <li>a RUNTIME_VERSION the language does not offer — {@code '4.0'} for PYTHON, {@code '8'} for JAVA,
     *       {@code '2.11'} for SCALA — is an invalid value (see {@link #RUNTIME_VERSIONS});</li>
     *   <li>a routine whose body is not inline names its entry point, so a missing HANDLER is refused;</li>
     *   <li>and last, a RUNTIME_VERSION the language no longer runs
     *       (see {@link #DECOMMISSIONED_RUNTIMES}).</li>
     * </ol>
     */
    private void validateRoutineProperties(final String language, final String runtimeVersion,
                                           final String handler, final List<String> imports,
                                           final boolean importsNamed, final boolean packagesNamed) {
        final String lang = language.toUpperCase(Locale.ROOT);
        if (INLINE_BODY_LANGUAGES.contains(lang)) {
            if (importsNamed) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'imports'; feature 'dependency import list' not enabled"));
            }
            if (runtimeVersion != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'RUNTIME_VERSION' for 'FUNCTION'"));
            }
            if (handler != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'handler' for '" + lang + " function'"));
            }
            if (packagesNamed) {
                throw new RuntimeException(SqlCompilationError.of("invalid property 'PACKAGES' for 'FUNCTION'"));
            }
            return;
        }
        RoutineImports.validate(imports, catalog, queryExecutor);
        if (runtimeVersion == null && VERSIONED_RUNTIMES.contains(lang)) {
            throw new RuntimeException("Property 'runtime_version' must be specified");
        }
        final Set<String> offered = RUNTIME_VERSIONS.get(lang);
        if (runtimeVersion != null && offered != null && !offered.contains(runtimeVersion)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value '" + runtimeVersion + "' for property 'RUNTIME_VERSION'"));
        }
        if (handler == null) {
            throw new RuntimeException("Property 'handler' must be specified");
        }
        rejectDecommissionedRuntime(lang, runtimeVersion);
    }

    /**
     * Refuse a routine written with neither a body nor a file to take one from, in the account's words by
     * language: a procedure and a JavaScript function ask for the AS property, a handler language for a body or an
     * import, and a SQL table function for its query. A SQL scalar function alone may be written without one — it
     * answers NULL. The refusal follows the routine's other properties, a TARGET_PATH that wants a body among
     * them, and precedes its signature's names; IF NOT EXISTS over an existing overload does not spare it
     * (live-verified).
     *
     * @param ctx       the CREATE statement
     * @param language  the routine's language
     * @param procedure whether the routine is a procedure
     */
    private static void requireBody(final FrostlakeParser.CreateStatementContext ctx, final UdfLanguage language,
                                    final boolean procedure) {
        if (ctx.bodyDefinition() != null) {
            return;
        }
        switch (language) {
            case SQL:
                if (procedure) {
                    throw new RuntimeException("Property 'as' must be specified");
                }
                if (ctx.returnType() != null && ctx.returnType().TABLE() != null) {
                    throw new RuntimeException("Table function definition expected a subquery, found 'null'.");
                }
                return;
            case JAVASCRIPT:
                throw new RuntimeException("Property 'as' must be specified");
            case PYTHON:
                requireImportedBody(ctx, "a ZIP or PY file");
                return;
            case JAVA:
                requireImportedBody(ctx, "a JAR file");
                return;
            case SCALA:
                requireImportedBody(ctx, "a function body or imports file");
                return;
            default:
                return;
        }
    }

    /** A handler language's routine without a body takes its code from an import, or is refused. */
    private static void requireImportedBody(final FrostlakeParser.CreateStatementContext ctx, final String file) {
        if (!hasImportsOption(ctx)) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Either a function BODY or " + file + " in the IMPORTS clause is required."));
        }
    }

    /**
     * Refuse a RUNTIME_VERSION the language no longer runs. The sentence names the version as written and the
     * lowest one still offered, and it is refused only once the routine's other properties are in order.
     */
    private void rejectDecommissionedRuntime(final String lang, final String runtimeVersion) {
        final Set<String> retired = DECOMMISSIONED_RUNTIMES.get(lang);
        if (runtimeVersion == null || retired == null || !retired.contains(runtimeVersion)) {
            return;
        }
        final String name = RUNTIME_LANGUAGE_NAMES.get(lang);
        throw new RuntimeException(SqlCompilationError.inline(name + " runtime version " + runtimeVersion
            + " is decommissioned. Please update your code to " + name + " runtime version "
            + LOWEST_RUNTIME.get(lang) + " or later."));
    }

    /** A function's RUNTIME_VERSION as written, or null when its options name none. */
    private String functionRuntimeVersion(final FrostlakeParser.CreateStatementContext ctx) {
        String runtimeVersion = null;
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.runtimeVersionClause() != null) {
                runtimeVersion = ddl.extractRuntimeVersion(opt.runtimeVersionClause());
            }
        }
        return runtimeVersion;
    }

    /** A function's HANDLER, or null when its options name none. */
    private String functionHandler(final FrostlakeParser.CreateStatementContext ctx) {
        String handler = null;
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.handlerClause() != null) {
                handler = ddl.extractStringLiteral(opt.handlerClause().STRING_LITERAL());
            }
        }
        return handler;
    }

    /**
     * A routine's IMPORTS, in written order, empty when its options name none. A repeated IMPORTS replaces the
     * earlier one, which is then never looked at: {@code IMPORTS = ('@missing/a.zip') IMPORTS = ()} is created
     * (live-verified).
     */
    private List<String> functionImports(final FrostlakeParser.CreateStatementContext ctx) {
        List<String> imports = new ArrayList<>();
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.importsClause() != null) {
                imports = importsOf(opt.importsClause());
            }
        }
        return imports;
    }

    /** Whether a routine's options name IMPORTS at all. */
    private static boolean hasImportsOption(final FrostlakeParser.CreateStatementContext ctx) {
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.importsClause() != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether a routine's options name PACKAGES at all. */
    private static boolean hasPackagesOption(final FrostlakeParser.CreateStatementContext ctx) {
        for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
            if (opt.packagesClause() != null) {
                return true;
            }
        }
        return false;
    }

    /** The files an IMPORTS clause names, in written order, empty for an absent clause. */
    private List<String> importsOf(final FrostlakeParser.ImportsClauseContext clause) {
        final List<String> imports = new ArrayList<>();
        if (clause != null && clause.stringLiteralList() != null) {
            for (final TerminalNode literal : clause.stringLiteralList().STRING_LITERAL()) {
                imports.add(ddl.extractStringLiteral(literal));
            }
        }
        return imports;
    }

    /**
     * The names the signature declares, upper-cased, for the body's own name resolution. A parameter is
     * matched without regard to case: a body over {@code (x INT)} may write {@code x} or {@code X}.
     */
    private Set<String> signatureNames(final FrostlakeParser.CreateStatementContext ctx) {
        final Set<String> names = new LinkedHashSet<>();
        if (ctx.parameterList() != null) {
            for (final FrostlakeParser.ParameterDefContext parameter : ctx.parameterList().parameterDef()) {
                names.add(getText(parameter.identifier()).toUpperCase(Locale.ROOT));
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
    /**
     * A SQL table function must declare its columns: {@code RETURNS TABLE ()} is refused at CREATE, counting
     * the columns its query body produces — {@code Mismatch between declared return signature column count (0)
     * and actual column count (2)}. A table function in another language may declare none (live-verified).
     */
    private void rejectColumnlessSqlTableFunction(final FrostlakeParser.CreateStatementContext ctx,
                                                  final String database, final String schema) {
        if (functionLanguage(ctx) != UdfLanguage.SQL || ctx.returnType() == null || ctx.returnType().TABLE() == null
                || ctx.returnType().columnList() != null || ctx.bodyDefinition() == null) {
            return;
        }
        final String body = ddl.extractBodyDefinition(ctx.bodyDefinition()).trim();
        throw new RuntimeException("Mismatch between declared return signature column count (0) and actual column count ("
            + queryBodyColumnCount(body, database, schema) + ")");
    }

    /**
     * How many columns a query body produces: its compiled shape, or, when the shape reads the function's own
     * parameters and so cannot compile on its own, the items of its first select list.
     */
    private int queryBodyColumnCount(final String body, final String database, final String schema) {
        try {
            return queryExecutor.resolveViewShapeInScope(database, schema, body, null).size();
        } catch (final RuntimeException unresolved) {
            final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(body);
            final FrostlakeParser.SelectListContext items = query == null ? null : firstSelectList(query);
            return items == null ? 0 : items.selectItem().size();
        }
    }

    /** The first select list under a parse tree, in source order, or null when it has none. */
    private static FrostlakeParser.SelectListContext firstSelectList(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectListContext) {
            return (FrostlakeParser.SelectListContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.SelectListContext found = firstSelectList(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

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

    /**
     * A JavaScript handler is declared as a function under the routine's name, taking the arguments as its
     * parameters, so each canonical name must be one JavaScript can declare. The routine's name is judged
     * first, {@code Invalid UDF function name: 'delete'} for a procedure too, then the arguments, every one
     * JavaScript cannot declare named in one list: {@code Invalid argument name(s): 'my arg, class'}. Both
     * follow the type rule, carry no compilation-error prefix, and are not swallowed by IF NOT EXISTS
     * (live-verified).
     */
    private static void requireJavaScriptNames(final String language, final String routineName,
                                               final List<Parameter> parameters) {
        if (!"JAVASCRIPT".equalsIgnoreCase(language)) {
            return;
        }
        if (!JavaScriptIdentifiers.isDeclarable(routineName)) {
            throw new RuntimeException("Invalid UDF function name: '" + routineName + "'");
        }
        final List<String> undeclarable = new ArrayList<>();
        for (final Parameter parameter : parameters) {
            if (!JavaScriptIdentifiers.isDeclarable(parameter.getName())) {
                undeclarable.add(parameter.getName());
            }
        }
        if (!undeclarable.isEmpty()) {
            throw new RuntimeException("Invalid argument name(s): '" + String.join(", ", undeclarable) + "'");
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
                final String name = ParseTreeText.namePartText(column.columnDef().columnDefName()).toUpperCase(Locale.ROOT);
                if (!columns.add(name)) {
                    throw new RuntimeException(
                        "Return signature contains a duplicate column name '" + name + "'.");
                }
            }
        }
    }


    /**
     * The mode an EXECUTE AS clause writes: OWNER, CALLER or RESTRICTED CALLER.
     *
     * @param clause the clause
     * @return the mode as SQL writes it
     */
    static String executeAsText(final FrostlakeParser.ExecuteAsClauseContext clause) {
        if (clause.OWNER() != null) {
            return "OWNER";
        }
        return clause.identifier() != null ? "RESTRICTED CALLER" : "CALLER";
    }
}
