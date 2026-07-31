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
 * Handles CREATE for security / governance objects — TAG, MASKING POLICY, ROW ACCESS POLICY, USER, ROLE —
 * extracted from {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. Column/type
 * parsing is delegated to {@link ColumnDefinitionParser}; shared schema helpers via the {@code ddl} back-ref.
 */
public class CreateSecurityHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateSecurityHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;

    CreateSecurityHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor,
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

    public Object handleCreateTag(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String tagName = getText(ctx.qualifiedName(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropTag(tagName); } catch (final RuntimeException ignored) {}
        }
        try {
            ddl.checkCreatePrivilege(Privilege.CREATE_TAG, ContainerType.SCHEMA,
                ddl.resolveSchemaFromQualifiedName(tagName).getName());
            List<String> allowedValues = new ArrayList<>();
            boolean masking = false;
            String comment = null;

            if (ctx.tagProperties() != null) {
                if (ctx.tagProperties().ALLOWED_VALUES() != null) {
                    for (final var stringLiteral : ctx.tagProperties().stringLiteralList().STRING_LITERAL()) {
                        allowedValues.add(ddl.extractStringLiteral(stringLiteral));
                    }
                } else if (ctx.tagProperties().MASKING() != null) {
                    masking = ctx.tagProperties().booleanValue().TRUE() != null;
                }
            }

            String statementComment = ddl.extractCommentFromList(ctx.commentClause());
            if (statementComment != null) {
                comment = statementComment;
            }

            catalog.createTag(tagName, allowedValues, masking, comment);
            logger.trace("Created tag: {}", tagName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Tag already exists (IF NOT EXISTS): {}", tagName);
        }
        return null;
    }

    public Object handleCreateMaskingPolicy(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String qn = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;
        try {
            Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            String policyName = parts[parts.length - 1].toUpperCase();
            ddl.checkCreatePrivilege(Privilege.CREATE_MASKING_POLICY, ContainerType.SCHEMA, schema.getName());
            if (orReplace) {
                if (catalog.isPolicyInUse(policyName, true)) {
                    throw new RuntimeException("Policy " + policyName.toUpperCase()
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                try { schema.dropMaskingPolicy(policyName); } catch (final RuntimeException ignored) {}
            }
            List<Parameter> params = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
                    params.add(columnParser.parseParameterDef(p));
                }
            }
            String returnType = ctx.dataTypeName() != null ? ctx.dataTypeName().getText().toUpperCase() : "STRING";
            String body = ctx.bodyDefinition() != null
                ? ddl.extractBodyDefinition(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            MaskingPolicy policy = new MaskingPolicy(policyName, params, returnType, body);
            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) policy.setComment(comment);
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addMaskingPolicy(policy);
            logger.trace("Created masking policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    public Object handleCreateRowAccessPolicy(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String qn = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;
        try {
            Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            String policyName = parts[parts.length - 1].toUpperCase();
            ddl.checkCreatePrivilege(Privilege.CREATE_ROW_ACCESS_POLICY, ContainerType.SCHEMA, schema.getName());
            if (orReplace) { try { schema.dropRowAccessPolicy(policyName); } catch (final RuntimeException ignored) {} }
            List<Parameter> params = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
                    params.add(columnParser.parseParameterDef(p));
                }
            }
            String body = ctx.bodyDefinition() != null
                ? ddl.extractBodyDefinition(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            RowAccessPolicy policy = new RowAccessPolicy(policyName, params, body);
            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) policy.setComment(comment);
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addRowAccessPolicy(policy);
            logger.trace("Created row access policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    public Object handleCreateUser(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String userName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropUser(userName); } catch (final RuntimeException ignored) {}
        }
        try {
            if (ctx.userProperties() != null) {
                String password = null;
                String defaultRole = null;

                for (final FrostlakeParser.UserPropertyContext propCtx : ctx.userProperties().userProperty()) {
                    if (propCtx.PASSWORD() != null) {
                        password = ddl.extractStringLiteral(propCtx.STRING_LITERAL());
                    } else if (propCtx.DEFAULT_ROLE() != null) {
                        if (propCtx.identifier() != null) {
                            defaultRole = getText(propCtx.identifier());
                        } else if (propCtx.STRING_LITERAL() != null) {
                            defaultRole = ddl.extractStringLiteral(propCtx.STRING_LITERAL());
                        }
                    }
                }

                if (password != null) {
                    catalog.createUser(userName, password, defaultRole);
                } else {
                    catalog.createUser(userName, null, defaultRole);
                }
            } else {
                catalog.createUser(userName);
            }

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                User user = catalog.getUser(userName);
                user.setComment(comment);
            }

            logger.trace("Created user: {}", userName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("User already exists (IF NOT EXISTS): {}", userName);
        }
        return null;
    }

    public Object handleCreateRole(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String roleName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropRole(roleName); } catch (final RuntimeException ignored) {}
        }
        try {
            catalog.createRole(roleName);

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                Role role = catalog.getRole(roleName);
                role.setComment(comment);
            }

            logger.trace("Created role: {}", roleName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Role already exists (IF NOT EXISTS): {}", roleName);
        }
        return null;
    }

}
