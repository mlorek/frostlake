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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles CREATE FUNCTION and CREATE PROCEDURE (UDF / stored-procedure creation across SQL/JS/Python/Java/
 * Scala), extracted from {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here.
 * Column/type parsing is delegated to {@link ColumnDefinitionParser}; shared schema/body helpers are reached
 * via the {@code ddl} back-reference.
 */
public class CreateRoutineHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateRoutineHandler.class);

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
        String qualifiedName = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;

        try {
            Schema schema;
            String functionName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                functionName = parts[0].toUpperCase();
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw new RuntimeException("No database selected");
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                functionName = parts[1].toUpperCase();
            } else if (parts.length == 3) {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                functionName = parts[2].toUpperCase();
            } else {
                throw new RuntimeException("Invalid function name: " + qualifiedName);
            }

            ddl.checkCreatePrivilege(Privilege.CREATE_FUNCTION, ContainerType.SCHEMA, schema.getName());

            // Parse parameters first
            List<Parameter> parameters = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext paramCtx : ctx.parameterList().parameterDef()) {
                    parameters.add(columnParser.parseParameterDef(paramCtx));
                }
            }

            // Handle OR REPLACE - drop specific overload with matching signature
            if (orReplace) {
                try {
                    List<DataType> argumentTypes = new ArrayList<>();
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
            DataType returnType;
            List<Parameter> returnColumns = new ArrayList<>();
            if (ctx.returnType() == null) {
                throw new RuntimeException("RETURNS clause is required for CREATE FUNCTION");
            } else if (ctx.returnType().TABLE() != null) {
                isTableFunction = true;
                returnType = StringType.VARCHAR;
                if (ctx.returnType().columnList() != null) {
                    for (final FrostlakeParser.ColumnOrConstraintContext colCtx : ctx.returnType().columnList().columnOrConstraint()) {
                        if (colCtx.columnDef() != null) {
                            String colName = getText(colCtx.columnDef().identifier()).toUpperCase();
                            DataType colType = columnParser.parseDataType(colCtx.columnDef().dataTypeName(), colCtx.columnDef().typeParameters());
                            returnColumns.add(new Parameter(colName, colType));
                        }
                    }
                }
            } else if (ctx.returnType().dataTypeName() != null) {
                returnType = columnParser.parseDataType(ctx.returnType().dataTypeName(), ctx.returnType().typeParameters());
            } else {
                throw new RuntimeException("Invalid return type in CREATE FUNCTION");
            }

            String body = ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null;

            // Extract options — order-independent via functionOption*
            String language = "SQL";
            String handler = null;
            String runtimeVersion = null;
            String nullHandling = "CALLED ON NULL INPUT";
            String volatility = "VOLATILE";
            String comment = null;
            List<String> imports = new ArrayList<>();
            for (final FrostlakeParser.FunctionOptionContext opt : ctx.functionOption()) {
                if (opt.languageClause() != null) {
                    FrostlakeParser.LanguageClauseContext lc = opt.languageClause();
                    if (lc.JAVASCRIPT() != null) language = "JAVASCRIPT";
                    else if (lc.JAVA() != null) language = "JAVA";
                    else if (lc.SCALA() != null) language = "SCALA";
                    else if (lc.PYTHON() != null) language = "PYTHON";
                    else language = "SQL";
                } else if (opt.handlerClause() != null) {
                    handler = ddl.extractStringLiteral(opt.handlerClause().STRING_LITERAL());
                } else if (opt.runtimeVersionClause() != null) {
                    runtimeVersion = ddl.extractRuntimeVersion(opt.runtimeVersionClause());
                } else if (opt.nullHandlingClause() != null) {
                    FrostlakeParser.NullHandlingClauseContext nhCtx = opt.nullHandlingClause();
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
                }
            }

            Function function = new Function(functionName, parameters, returnType, returnColumns, body, isTableFunction, language, handler, runtimeVersion);

            if (ctx.SECURE() != null) function.setSecure(true);
            function.setNullHandling(nullHandling);
            function.setVolatility(volatility);
            if (!imports.isEmpty()) function.setImports(imports);
            if (comment != null) function.setComment(comment);

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
        String qualifiedName = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;

        try {
            Schema schema;
            String procedureName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                procedureName = parts[0].toUpperCase();
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw new RuntimeException("No database selected");
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                procedureName = parts[1].toUpperCase();
            } else if (parts.length == 3) {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                procedureName = parts[2].toUpperCase();
            } else {
                throw new RuntimeException("Invalid procedure name: " + qualifiedName);
            }

            ddl.checkCreatePrivilege(Privilege.CREATE_PROCEDURE, ContainerType.SCHEMA, schema.getName());

            // Parse parameters first
            List<Parameter> parameters = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext paramCtx : ctx.parameterList().parameterDef()) {
                    parameters.add(columnParser.parseParameterDef(paramCtx));
                }
            }

            // Handle OR REPLACE - drop specific overload with matching signature
            if (orReplace) {
                try {
                    List<DataType> argumentTypes = new ArrayList<>();
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

            DataType returnType;
            List<Parameter> procReturnColumns = new ArrayList<>();
            if (ctx.returnType() == null) {
                throw new RuntimeException("RETURNS clause is required for CREATE PROCEDURE");
            } else if (ctx.returnType().TABLE() != null) {
                returnType = StringType.VARCHAR;
                // RETURNS TABLE(col TYPE, ...): keep the declared columns — they name the result of a
                // CALL and of a TABLE(proc(...)) FROM source, exactly as Snowflake names them.
                if (ctx.returnType().columnList() != null) {
                    for (final FrostlakeParser.ColumnOrConstraintContext colCtx : ctx.returnType().columnList().columnOrConstraint()) {
                        if (colCtx.columnDef() != null) {
                            String colName = getText(colCtx.columnDef().identifier()).toUpperCase();
                            DataType colType = columnParser.parseDataType(colCtx.columnDef().dataTypeName(), colCtx.columnDef().typeParameters());
                            procReturnColumns.add(new Parameter(colName, colType));
                        }
                    }
                }
            } else if (ctx.returnType().dataTypeName() != null) {
                returnType = columnParser.parseDataType(ctx.returnType().dataTypeName(), ctx.returnType().typeParameters());
            } else {
                throw new RuntimeException("Invalid return type in CREATE PROCEDURE");
            }

            String body = ctx.bodyDefinition() != null ? ddl.extractBodyDefinition(ctx.bodyDefinition()) : null;

            String language = "SQL";
            if (ctx.languageClause() != null) {
                if (ctx.languageClause().JAVASCRIPT() != null) {
                    language = "JAVASCRIPT";
                } else if (ctx.languageClause().JAVA() != null) {
                    language = "JAVA";
                } else if (ctx.languageClause().SCALA() != null) {
                    language = "SCALA";
                } else if (ctx.languageClause().PYTHON() != null) {
                    language = "PYTHON";
                } else if (ctx.languageClause().SQL() != null) {
                    language = "SQL";
                }
            }

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

            Procedure procedure = new Procedure(procedureName, parameters, returnType, body, language, handler, runtimeVersion, packages);
            if (!procReturnColumns.isEmpty()) {
                procedure.setReturnColumns(procReturnColumns);
            }

            if (ctx.importsClause() != null) {
                List<String> importList = new ArrayList<>();
                for (final var sl : ctx.importsClause().stringLiteralList().STRING_LITERAL()) {
                    importList.add(ddl.extractStringLiteral(sl));
                }
                procedure.setImports(importList);
            }

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                procedure.setComment(comment);
            }

            if (ctx.executeAsClause() != null) {
                String execAs = ctx.executeAsClause().OWNER() != null ? "OWNER" : "CALLER";
                procedure.setExecuteAs(execAs);
            }

            procedure.setOwner(catalog.currentRoleForOwner());
            schema.addProcedure(procedure);
            logger.trace("Created procedure: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Procedure already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

}
