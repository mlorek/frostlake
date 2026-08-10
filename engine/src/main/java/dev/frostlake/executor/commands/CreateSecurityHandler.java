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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Handles CREATE for security / governance objects — TAG, MASKING POLICY, ROW ACCESS POLICY, USER, ROLE —
 * extracted from {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. Column/type
 * parsing is delegated to {@link ColumnDefinitionParser}; shared schema helpers via the {@code ddl} back-ref.
 */
public class CreateSecurityHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateSecurityHandler.class);

    /** What live accepts as a contact's address: one address, a dotted domain, no spaces. */
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@,]+\\.[A-Za-z]{2,}");

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
        final String tagName = getText(ctx.qualifiedName(0));
        // MASKING is not a tag property — Snowflake refuses it at compile time, before IF NOT
        // EXISTS could forgive anything (live-verified wording).
        if (ctx.tagProperties() != null && ctx.tagProperties().MASKING() != null) {
            throw new RuntimeException(
                SqlCompilationError.of("invalid property 'MASKING' for 'TAG'"));
        }
        if (ctx.or_replace() != null) {
            try { catalog.dropTag(tagName); } catch (final RuntimeException ignored) {}
        }
        try {
            ddl.checkCreatePrivilege(Privilege.CREATE_TAG, ContainerType.SCHEMA,
                ddl.resolveSchemaFromQualifiedName(tagName).getName());
            final List<String> allowedValues = new ArrayList<>();
            String comment = null;

            if (ctx.tagProperties() != null) {
                if (ctx.tagProperties().ALLOWED_VALUES() != null) {
                    for (final var stringLiteral : ctx.tagProperties().stringLiteralList().STRING_LITERAL()) {
                        allowedValues.add(ddl.extractStringLiteral(stringLiteral));
                    }
                }
            }

            final String statementComment = ddl.extractCommentFromList(ctx.commentClause());
            if (statementComment != null) {
                comment = statementComment;
            }

            catalog.createTag(tagName, allowedValues, comment);
            logger.trace("Created tag: {}", tagName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Tag already exists (IF NOT EXISTS): {}", tagName);
        }
        return null;
    }

    public Object handleCreateMaskingPolicy(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        try {
            final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String policyName = parts[parts.length - 1].toUpperCase();
            ddl.checkCreatePrivilege(Privilege.CREATE_MASKING_POLICY, ContainerType.SCHEMA, schema.getName());
            if (orReplace) {
                if (catalog.isPolicyInUse(policyName, true)) {
                    throw new RuntimeException("Policy " + policyName.toUpperCase()
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                try { schema.dropMaskingPolicy(policyName); } catch (final RuntimeException ignored) {}
            }
            final List<Parameter> params = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
                    params.add(columnParser.parseParameterDef(p));
                }
            }
            final String returnType = ctx.dataTypeName() != null ? ctx.dataTypeName().getText().toUpperCase() : "STRING";
            // The body is stored as EXPRESSION TEXT verbatim: a string-literal body keeps its
            // quotes ('***') so DESCRIBE shows it as typed (live-verified) and policy application
            // can inline it as an expression.
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            // The policy hands back what it was given: live refuses a signature whose argument and
            // return types disagree, before anything is attached.
            if (!params.isEmpty() && ctx.dataTypeName() != null) {
                final DataType declaredReturn = columnParser.parseDataType(ctx.dataTypeName(),
                    ctx.typeParameters());
                if (!SqlTypeNames.sameFamily(params.get(0).getDataType(), declaredReturn)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX
                        + " Masking policy function argument and return type mismatch.");
                }
            }
            final MaskingPolicy policy = new MaskingPolicy(policyName, params, returnType, body);
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) policy.setComment(comment);
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addMaskingPolicy(policy);
            logger.trace("Created masking policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    public Object handleCreateRowAccessPolicy(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        try {
            final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String policyName = parts[parts.length - 1].toUpperCase();
            ddl.checkCreatePrivilege(Privilege.CREATE_ROW_ACCESS_POLICY, ContainerType.SCHEMA, schema.getName());
            if (orReplace) { try { schema.dropRowAccessPolicy(policyName); } catch (final RuntimeException ignored) {} }
            final List<Parameter> params = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
                    params.add(columnParser.parsePolicyParameterDef(p));
                }
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            final RowAccessPolicy policy = new RowAccessPolicy(policyName, params, body);
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) policy.setComment(comment);
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addRowAccessPolicy(policy);
            logger.trace("Created row access policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    /**
     * CREATE JOIN POLICY — the third policy kind with a zero-argument signature and a constraint
     * body, here answering JOIN_CONSTRAINT(JOIN_REQUIRED => …).
     */
    public Object handleCreateJoinPolicy(final FrostlakeParser.CreateStatementContext ctx,
                                         final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        if (ctx.parameterList() != null && !ctx.parameterList().parameterDef().isEmpty()) {
            throw new RuntimeException("Join policy must have exactly zero arguments, got "
                + ctx.parameterList().parameterDef().size() + " arguments.");
        }
        final String returnType = ctx.dataTypeName() != null
            ? ctx.dataTypeName().getText().toUpperCase(Locale.ROOT)
            : getText(ctx.identifier(0)).toUpperCase(Locale.ROOT);
        if (!"JOIN_CONSTRAINT".equals(returnType)) {
            throw new RuntimeException("Join policy return type '" + returnType
                + "' is not JOIN_CONSTRAINT.");
        }
        try {
            final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String policyName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
            if (orReplace) {
                if (catalog.isJoinPolicyInUse(policyName)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Policy " + policyName
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                schema.dropJoinPolicy(policyName);
            } else if (schema.hasJoinPolicy(policyName)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Object '" + policyName + "' already exists."));
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            final JoinPolicy policy = new JoinPolicy(policyName, body);
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                policy.setComment(comment);
            }
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addJoinPolicy(policy);
            logger.trace("Created join policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    /**
     * CREATE AGGREGATION POLICY — the same shape as a projection policy, with its own two refusals
     * and a body answering AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => n) or NO_AGGREGATION_CONSTRAINT().
     */
    public Object handleCreateAggregationPolicy(final FrostlakeParser.CreateStatementContext ctx,
                                                final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        if (ctx.parameterList() != null && !ctx.parameterList().parameterDef().isEmpty()) {
            throw new RuntimeException("Aggregation policy must have exactly zero arguments, got "
                + ctx.parameterList().parameterDef().size() + " arguments.");
        }
        final String returnType = ctx.dataTypeName() != null
            ? ctx.dataTypeName().getText().toUpperCase(Locale.ROOT)
            : getText(ctx.identifier(0)).toUpperCase(Locale.ROOT);
        if (!"AGGREGATION_CONSTRAINT".equals(returnType)) {
            throw new RuntimeException("Aggregation policy return type '" + returnType
                + "' is not AGGREGATION_CONSTRAINT.");
        }
        try {
            final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String policyName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
            if (orReplace) {
                if (catalog.isAggregationPolicyInUse(policyName)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Policy " + policyName
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                schema.dropAggregationPolicy(policyName);
            } else if (schema.hasAggregationPolicy(policyName)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Object '" + policyName + "' already exists."));
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            final AggregationPolicy policy = new AggregationPolicy(policyName, body);
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                policy.setComment(comment);
            }
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addAggregationPolicy(policy);
            logger.trace("Created aggregation policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    /**
     * CREATE PROJECTION POLICY — zero arguments, a body returning PROJECTION_CONSTRAINT, and no
     * behaviour of its own until a column is attached to it. Both refusals are live's: a signature
     * with any argument at all, and a declared return type that is not PROJECTION_CONSTRAINT.
     */
    public Object handleCreateProjectionPolicy(final FrostlakeParser.CreateStatementContext ctx,
                                               final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        if (ctx.parameterList() != null && !ctx.parameterList().parameterDef().isEmpty()) {
            throw new RuntimeException("Projection policy must have exactly zero arguments, got "
                + ctx.parameterList().parameterDef().size() + " arguments.");
        }
        final String returnType = ctx.dataTypeName() != null
            ? ctx.dataTypeName().getText().toUpperCase(Locale.ROOT)
            : getText(ctx.identifier(0)).toUpperCase(Locale.ROOT);
        if (!"PROJECTION_CONSTRAINT".equals(returnType)) {
            throw new RuntimeException("Projection policy return type '" + returnType
                + "' is not PROJECTION_CONSTRAINT.");
        }
        try {
            final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String policyName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
            if (orReplace) {
                if (catalog.isProjectionPolicyInUse(policyName)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Policy " + policyName
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                schema.dropProjectionPolicy(policyName);
            } else if (schema.hasProjectionPolicy(policyName)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Object '" + policyName + "' already exists."));
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            final ProjectionPolicy policy = new ProjectionPolicy(policyName, body);
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                policy.setComment(comment);
            }
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addProjectionPolicy(policy);
            logger.trace("Created projection policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    /**
     * CREATE CONTACT — a schema-level object with three string properties and no behaviour. COMMENT,
     * URL and EMAIL_DISTRIBUTION_LIST are the ones live takes; anything else is refused the way live
     * refuses a property an object type does not have ({@code invalid property 'EMAIL' for 'CONTACT'}).
     */
    public Object handleCreateContact(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        try {
            final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String contactName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
            if (!orReplace && schema.hasContact(contactName)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Object '" + contactName + "' already exists."));
            }
            final Contact contact = new Contact(contactName);
            for (final FrostlakeParser.ContactPropertyContext property : ctx.contactProperty()) {
                applyContactProperty(contact, property);
            }
            // A contact is reached one way or the other, never both (live-verified). Each property is
            // validated as it is read, so an unusable address is refused before this pairing is.
            if (contact.getUrl() != null && !contact.getUrl().isEmpty()
                    && contact.getEmailDistributionList() != null
                    && !contact.getEmailDistributionList().isEmpty()) {
                throw new RuntimeException("Email, url or users/email_list, only one could be set.");
            }
            contact.setOwner(catalog.currentRoleForOwner());
            schema.addContact(contact);
            logger.trace("Created contact: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object"); }
        return null;
    }

    private void applyContactProperty(final Contact contact,
                                      final FrostlakeParser.ContactPropertyContext property) {
        final String key = property.optionKey().getText().toUpperCase(Locale.ROOT);
        final String value = ddl.extractStringLiteral(property.STRING_LITERAL());
        if ("COMMENT".equals(key)) {
            contact.setComment(value);
        } else if ("URL".equals(key)) {
            contact.setUrl(value);
        } else if ("EMAIL_DISTRIBUTION_LIST".equals(key)) {
            // ONE address, whatever the property name suggests — live refuses a comma-separated list,
            // an address with no dotted domain, a one-letter top level and any address with a space.
            // The empty string is allowed and clears it.
            if (!value.isEmpty() && !EMAIL.matcher(value).matches()) {
                throw new RuntimeException("Invalid email address(es): [" + value + "].");
            }
            contact.setEmailDistributionList(value);
        } else {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid property '" + key + "' for 'CONTACT'"));
        }
    }

    public Object handleCreateUser(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String userName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropUser(userName); } catch (final RuntimeException ignored) {}
        }
        try {
            if (ctx.userProperties() != null) {
                catalog.createUser(userName, null, null);
                UserProperties.apply(catalog.getUser(userName), ctx.userProperties().userProperty());
            } else {
                catalog.createUser(userName);
            }

            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                final User user = catalog.getUser(userName);
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
        final String roleName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try { catalog.dropRole(roleName); } catch (final RuntimeException ignored) {}
        }
        try {
            catalog.createRole(roleName);

            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                final Role role = catalog.getRole(roleName);
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
