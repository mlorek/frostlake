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

import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.PolicyObjectTagCalls;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NameTakenException;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

import org.antlr.v4.runtime.tree.ParseTree;
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
        // A property written twice and a propagation mode that is none of the three are compilation errors,
        // refused before IF NOT EXISTS could forgive anything.
        TagPropagation.requireCompilable(ctx.tagSetProperty());
        if (ctx.or_alter() != null && catalog.hasTagExact(tagName)) {
            alterTagInPlace(ctx, catalog.getTagExact(tagName));
            return null;
        }
        final List<String> allowedValues = new ArrayList<>();
        if (ctx.tagProperties() != null && ctx.tagProperties().ALLOWED_VALUES() != null) {
            for (final var stringLiteral : ctx.tagProperties().stringLiteralList().STRING_LITERAL()) {
                allowedValues.add(ddl.extractStringLiteral(stringLiteral));
            }
        }
        // The propagation and its conflict rule are judged before the catalog changes, so a refused statement
        // leaves no tag behind and replaces none.
        String propagate = null;
        String onConflict = null;
        String comment = null;
        for (final FrostlakeParser.TagSetPropertyContext property : ctx.tagSetProperty()) {
            if (property.tagPropagation() != null) {
                propagate = TagPropagation.mode(property.tagPropagation());
            } else if (property.tagConflict() != null) {
                onConflict = TagPropagation.conflict(property.tagConflict());
            } else {
                comment = ddl.extractComment(property.commentClause());
            }
        }
        TagPropagation.requireConsistent(allowedValues, propagate, onConflict);
        if (ctx.or_replace() != null) {
            // Replacing a tag needs OWNERSHIP of it.
            queryExecutor.requireOwnership("TAG", qualifiedNameParts(ctx.qualifiedName(0)), null);
            // Asked before the tag a replacement drops is gone.
            ddl.checkCreatePrivilege(Privilege.CREATE_TAG, ddl.resolveSchemaFromQualifiedName(tagName));
            try { catalog.dropTagExact(tagName); } catch (final RuntimeException ignored) {}
        }
        try {
            DDLCommandHandler.refuseTakenName(ctx.or_replace() == null && !ifNotExists,
                ddl.resolveSchemaFromQualifiedName(tagName).hasTagExact(ddl.extractObjectName(tagName)),
                qualifiedNameParts(ctx.qualifiedName(0)));
            ddl.checkCreatePrivilege(Privilege.CREATE_TAG,
                ddl.resolveSchemaFromQualifiedName(tagName));
            catalog.createTag(tagName, allowedValues, comment);
            if (propagate != null) {
                final Tag created = catalog.getTagExact(tagName);
                created.setPropagate(propagate);
                created.setOnConflict(onConflict);
            }
            logger.trace("Created tag: {}", tagName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0));
            logger.debug("Tag already exists (IF NOT EXISTS): {}", tagName);
        }
        return null;
    }

    /**
     * CREATE OR ALTER TAG over an existing tag: the tag takes the statement's ALLOWED_VALUES, PROPAGATE, ON_CONFLICT
     * and COMMENT, and a property the statement leaves out is unset, since the statement describes the whole tag.
     */
    private void alterTagInPlace(final FrostlakeParser.CreateStatementContext ctx, final Tag tag) {
        final List<String> allowedValues = new ArrayList<>();
        if (ctx.tagProperties() != null && ctx.tagProperties().ALLOWED_VALUES() != null) {
            for (final var stringLiteral : ctx.tagProperties().stringLiteralList().STRING_LITERAL()) {
                allowedValues.add(ddl.extractStringLiteral(stringLiteral));
            }
        }
        // The statement describes the whole tag, its propagation included: what it leaves out is unset.
        String propagate = null;
        String onConflict = null;
        String comment = null;
        for (final FrostlakeParser.TagSetPropertyContext property : ctx.tagSetProperty()) {
            if (property.tagPropagation() != null) {
                propagate = TagPropagation.mode(property.tagPropagation());
            } else if (property.tagConflict() != null) {
                onConflict = TagPropagation.conflict(property.tagConflict());
            } else {
                comment = ddl.extractComment(property.commentClause());
            }
        }
        TagPropagation.requireConsistent(allowedValues, propagate, onConflict);
        tag.setAllowedValues(allowedValues);
        tag.setComment(comment);
        tag.setPropagate(propagate);
        tag.setOnConflict(onConflict);
        ConditionalDdlOutcome.alteredInPlace();
    }

    /**
     * The refusal of a policy created over a name its schema holds, which names the policy as the statement
     * wrote it: {@code Object '"p"' already exists.} (live-verified).
     *
     * @param parts the policy's name as written, canonical
     * @return the refusal
     */
    private static NameTakenException policyNameTaken(final String[] parts) {
        return new NameTakenException(SqlCompilationError.of("Object '"
            + SqlIdentifiers.spellCanonical(parts[parts.length - 1]) + "' already exists."));
    }

    /**
     * A policy's name and kind, judged with the privilege to create it: {@link #requirePolicyName}, then
     * {@link #requirePolicyKind}.
     *
     * @param schema      the policy's schema
     * @param policyName  its name
     * @param kind        its kind as a refusal spells it
     * @param parts       its name as written, canonical
     * @param orReplace   whether the statement says OR REPLACE
     * @param ifNotExists whether it says IF NOT EXISTS
     * @param privilege   the privilege creating it asks
     */
    private void requirePolicyCreatable(final Schema schema, final String policyName, final String kind,
                                        final String[] parts, final boolean orReplace, final boolean ifNotExists,
                                        final Privilege privilege) {
        requirePolicyName(schema, policyName, parts, orReplace, ifNotExists, privilege);
        requirePolicyKind(schema, policyName, kind, parts, orReplace);
    }

    /**
     * A policy's name, judged with the privilege to create it. The policies of a schema share one namespace: a
     * plain CREATE over a name any of them holds is refused for the name before the privilege is asked; with OR
     * REPLACE or IF NOT EXISTS the privilege comes first (live-verified).
     */
    private void requirePolicyName(final Schema schema, final String policyName, final String[] parts,
                                   final boolean orReplace, final boolean ifNotExists, final Privilege privilege) {
        if (schema.policyKindHolding(policyName) != null && !orReplace && !ifNotExists) {
            throw policyNameTaken(parts);
        }
        ddl.checkCreatePrivilege(privilege, schema);
    }

    /**
     * A holder of the name of another policy kind, which OR REPLACE finds no policy to replace — {@code Object found
     * is of type 'MASKING_POLICY', not specified type 'ROW_ACCESS_POLICY'.} — and IF NOT EXISTS finds the name taken
     * by (live-verified). A masking or a row access policy is judged so once its statement has compiled
     * (live-verified).
     */
    private void requirePolicyKind(final Schema schema, final String policyName, final String kind,
                                   final String[] parts, final boolean orReplace) {
        final String holder = schema.policyKindHolding(policyName);
        if (holder != null && !holder.equals(kind)) {
            if (orReplace) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " Object found is of type '" + holder
                    + "', not specified type '" + kind + "'.");
            }
            throw policyNameTaken(parts);
        }
    }

    public Object handleCreateMaskingPolicy(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        final PolicyProperties properties =
            PolicyProperties.read(ddl, ctx.policyCreateProperty(), "MASKING_POLICY", true);
        try {
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            // The name as written: a quoted name keeps its case and is another policy than its upper-case namesake
            // (live-verified).
            final String policyName = parts[parts.length - 1];
            if (ctx.or_alter() != null && schema.getMaskingPolicyExact(policyName) != null) {
                alterMaskingPolicyInPlace(ctx, schema, schema.getMaskingPolicyExact(policyName), parts, properties);
                return null;
            }
            requirePolicyName(schema, policyName, parts, orReplace, ifNotExists, Privilege.CREATE_MASKING_POLICY);
            if (orReplace) {
                queryExecutor.requireOwnership("MASKING_POLICY", parts, null);
                if (catalog.isPolicyInUse(policyName, true)) {
                    throw new RuntimeException("Policy " + policyName.toUpperCase(Locale.ROOT)
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
            } else if (!ifNotExists && schema.getMaskingPolicyExact(policyName) != null) {
                // The name is judged before the signature and the body are (live-verified).
                throw policyNameTaken(parts);
            }
            final List<Parameter> params = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
                    params.add(columnParser.parseParameterDef(p));
                }
            }
            final String returnType = ctx.dataTypeName() != null ? ctx.dataTypeName().getText().toUpperCase(Locale.ROOT) : "STRING";
            // The body is stored as EXPRESSION TEXT verbatim: a string-literal body keeps its
            // quotes ('***') so DESCRIBE shows it as typed (live-verified) and policy application
            // can inline it as an expression.
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            PolicyBodyCheck.requireNumericLimits(body);
            final DataType declaredReturnType = ctx.dataTypeName() != null
                ? columnParser.parseRoutineDataType(ctx.dataTypeName(), ctx.typeParameters()) : null;
            PolicyObjectTagCalls.requireTagsIn(ctx.bodyDefinition() != null ? ctx.bodyDefinition()
                : ctx.booleanExpr(), catalog);
            // The body is judged against RETURNS first; then the policy must hand back what it was given: live
            // refuses a signature whose argument and return types disagree, before anything is attached.
            PolicyBodyCheck.compile(queryExecutor, catalog, policyName, declaredReturnType,
                params, body, schema);
            if (!params.isEmpty() && declaredReturnType != null
                    && !SqlTypeNames.sameFamily(params.get(0).getDataType(), declaredReturnType)) {
                throw new RuntimeException(SqlCompilationError.PREFIX
                    + " Masking policy function argument and return type mismatch.");
            }
            // IF NOT EXISTS finds the policy there only once the statement has compiled, and OR REPLACE drops the
            // policy it replaces only then: a statement refused leaves it as it was (live-verified).
            requirePolicyKind(schema, policyName, "MASKING_POLICY", parts, orReplace);
            if (orReplace) {
                if (schema.getMaskingPolicyExact(policyName) != null) {
                    schema.dropMaskingPolicy(policyName);
                }
            } else if (schema.getMaskingPolicyExact(policyName) != null) {
                throw policyNameTaken(parts);
            }
            final MaskingPolicy policy = new MaskingPolicy(policyName, params, returnType, body);
            policy.setReturnDataType(declaredReturnType);
            policy.setReturnSpelling(declaredReturnType == null ? null
                : SqlTypeNames.declaredSpelling(declaredReturnType, ctx.typeParameters() != null));
            if (properties.comment() != null) {
                policy.setComment(properties.comment());
            }
            policy.setExemptOtherPolicies(properties.exempt());
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addMaskingPolicy(policy);
            logger.trace("Created masking policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0)); }
        return null;
    }

    /**
     * CREATE OR ALTER over a masking policy that exists: the policy becomes what the statement writes — its body, its
     * COMMENT and its EXEMPT_OTHER_POLICIES, a property left out unset — and keeps its attachments, so a masked column
     * reads the new body. Its signature stays: another return type is {@code Property 'FUNCTION_RETURN_TYPE' cannot be
     * changed in CREATE OR ALTER.}, then another argument list {@code Property 'FUNCTION_SIGNATURE' …}; the body is
     * then judged as a CREATE judges it (live-verified).
     */
    private void alterMaskingPolicyInPlace(final FrostlakeParser.CreateStatementContext ctx, final Schema schema,
                                           final MaskingPolicy policy, final String[] parts,
                                           final PolicyProperties properties) {
        queryExecutor.requireOwnership("MASKING_POLICY", parts, null);
        // The return type is compared as declared: STRING is VARCHAR, and VARCHAR(10) is another type.
        final DataType declaredReturn = columnParser.parseRoutineDataType(ctx.dataTypeName(), ctx.typeParameters());
        final String heldReturn = policy.getReturnDataType() != null
            ? SqlTypeNames.canonical(policy.getReturnDataType()) : policy.getReturnType();
        if (!SqlTypeNames.canonical(declaredReturn).equals(heldReturn)) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Property 'FUNCTION_RETURN_TYPE' cannot be changed in CREATE OR ALTER."));
        }
        final List<Parameter> params = new ArrayList<>();
        for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
            params.add(columnParser.parseParameterDef(p));
        }
        requireSameSignature(policy.getParameters(), params);
        final String body = ctx.bodyDefinition() != null
            ? ddl.getOriginalText(ctx.bodyDefinition()) : ddl.getOriginalText(ctx.booleanExpr());
        PolicyBodyCheck.requireNumericLimits(body);
        PolicyObjectTagCalls.requireTagsIn(ctx.bodyDefinition() != null ? ctx.bodyDefinition() : ctx.booleanExpr(),
            catalog);
        PolicyBodyCheck.compile(queryExecutor, catalog, policy.getName(), declaredReturn, params, body, schema);
        if (!params.isEmpty() && !SqlTypeNames.sameFamily(params.get(0).getDataType(), declaredReturn)) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Masking policy function argument and return type mismatch.");
        }
        policy.setBody(body);
        policy.setComment(properties.comment());
        policy.setExemptOtherPolicies(properties.exempt());
        ConditionalDdlOutcome.alteredInPlace();
        logger.trace("Altered masking policy in place: {}", policy.getName());
    }

    /**
     * CREATE OR ALTER over a row access policy that exists, as {@link #alterMaskingPolicyInPlace} does for a masking
     * policy: the body, the COMMENT and EXEMPT_OTHER_POLICIES become the statement's, and the signature stays.
     */
    private void alterRowAccessPolicyInPlace(final FrostlakeParser.CreateStatementContext ctx, final Schema schema,
                                             final RowAccessPolicy policy, final String[] parts,
                                             final PolicyProperties properties) {
        queryExecutor.requireOwnership("ROW_ACCESS_POLICY", parts, null);
        final List<Parameter> params = new ArrayList<>();
        for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
            params.add(columnParser.parsePolicyParameterDef(p));
        }
        requireSameSignature(policy.getParameters(), params);
        final String body = ctx.bodyDefinition() != null
            ? ddl.getOriginalText(ctx.bodyDefinition()) : ddl.getOriginalText(ctx.booleanExpr());
        PolicyObjectTagCalls.refuseIn(ctx.bodyDefinition() != null ? ctx.bodyDefinition() : ctx.booleanExpr(),
            PolicyObjectTagCalls.ON_CURRENT_COLUMN);
        compileRowAccessBody(ctx, policy.getName(), params, body, schema);
        policy.setBody(body);
        policy.setComment(properties.comment());
        policy.setExemptOtherPolicies(properties.exempt());
        ConditionalDdlOutcome.alteredInPlace();
        logger.trace("Altered row access policy in place: {}", policy.getName());
    }

    /**
     * A row access policy's body, judged against the RETURNS the statement spells, which must then be BOOLEAN: a
     * body of another type than the declared one is refused first, in a SQL function's words, and a declared type
     * other than BOOLEAN after it, by name (live-verified).
     */
    private void compileRowAccessBody(final FrostlakeParser.CreateStatementContext ctx, final String policyName,
                                      final List<Parameter> params, final String body, final Schema schema) {
        final DataType declared = columnParser.parseRoutineDataType(ctx.dataTypeName(), ctx.typeParameters());
        PolicyBodyCheck.compile(queryExecutor, catalog, policyName, declared, params, body, schema);
        if (!(declared instanceof BooleanType)) {
            throw new RuntimeException(SqlCompilationError.inline("Row access policy return type '"
                + RoutineReturnTypeChecker.spell(declared) + "' is not BOOLEAN."));
        }
    }

    /**
     * Refuse a CREATE OR ALTER whose arguments are not the policy's own: as many, of the same names and the same types
     * (live-verified).
     */
    private static void requireSameSignature(final List<Parameter> existing, final List<Parameter> written) {
        boolean same = existing.size() == written.size();
        for (int i = 0; same && i < existing.size(); i++) {
            same = existing.get(i).getName().equalsIgnoreCase(written.get(i).getName())
                && SqlTypeNames.canonical(existing.get(i).getDataType())
                    .equals(SqlTypeNames.canonical(written.get(i).getDataType()));
        }
        if (!same) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Property 'FUNCTION_SIGNATURE' cannot be changed in CREATE OR ALTER."));
        }
    }

    public Object handleCreateRowAccessPolicy(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qn = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;
        // CREATE OR ALTER takes no EXEMPT_OTHER_POLICIES for a row access policy (live-verified).
        final PolicyProperties properties =
            PolicyProperties.read(ddl, ctx.policyCreateProperty(), "ROW_ACCESS_POLICY", ctx.or_alter() == null);
        try {
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            // The name as written, as a masking policy's is (live-verified).
            final String policyName = parts[parts.length - 1];
            if (ctx.or_alter() != null && schema.getRowAccessPolicyExact(policyName) != null) {
                alterRowAccessPolicyInPlace(ctx, schema, schema.getRowAccessPolicyExact(policyName), parts,
                    properties);
                return null;
            }
            requirePolicyName(schema, policyName, parts, orReplace, ifNotExists,
                Privilege.CREATE_ROW_ACCESS_POLICY);
            if (orReplace) {
                queryExecutor.requireOwnership("ROW_ACCESS_POLICY", parts, null);
            }
            final List<Parameter> params = new ArrayList<>();
            if (ctx.parameterList() != null) {
                for (final FrostlakeParser.ParameterDefContext p : ctx.parameterList().parameterDef()) {
                    params.add(columnParser.parsePolicyParameterDef(p));
                }
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            // A row access policy protects a table and no column: the column's tag is not its to read.
            PolicyObjectTagCalls.refuseIn(ctx.bodyDefinition() != null ? ctx.bodyDefinition() : ctx.booleanExpr(),
                PolicyObjectTagCalls.ON_CURRENT_COLUMN);
            // The statement compiles before IF NOT EXISTS finds the policy there, and before OR REPLACE drops the
            // one it replaces: a statement refused leaves it as it was (live-verified).
            compileRowAccessBody(ctx, policyName, params, body, schema);
            requirePolicyKind(schema, policyName, "ROW_ACCESS_POLICY", parts, orReplace);
            if (orReplace) {
                if (schema.getRowAccessPolicyExact(policyName) != null) {
                    schema.dropRowAccessPolicy(policyName);
                }
            } else if (schema.getRowAccessPolicyExact(policyName) != null) {
                throw policyNameTaken(parts);
            }
            final RowAccessPolicy policy = new RowAccessPolicy(policyName, params, body);
            if (properties.comment() != null) {
                policy.setComment(properties.comment());
            }
            policy.setExemptOtherPolicies(properties.exempt());
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addRowAccessPolicy(policy);
            logger.trace("Created row access policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0)); }
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
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            // The name as written, as an aggregation policy's is.
            final String policyName = parts[parts.length - 1];
            requirePolicyCreatable(schema, policyName, "JOIN_POLICY", parts, orReplace, ifNotExists,
                Privilege.CREATE_JOIN_POLICY);
            if (orReplace) {
                if (catalog.isJoinPolicyInUse(policyName)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Policy "
                        + policyName.toUpperCase(Locale.ROOT)
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                schema.dropJoinPolicy(policyName);
            } else if (schema.hasJoinPolicy(policyName)) {
                throw policyNameTaken(parts);
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            PolicyBodyCheck.requireNumericLimits(body);
            final JoinPolicy policy = new JoinPolicy(policyName, body);
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                policy.setComment(comment);
            }
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addJoinPolicy(policy);
            logger.trace("Created join policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0)); }
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
        final PolicyProperties properties =
            PolicyProperties.read(ddl, ctx.policyCreateProperty(), "AGGREGATION_POLICY", false);
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
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            // The name as written, as a masking policy's is (live-verified).
            final String policyName = parts[parts.length - 1];
            requirePolicyCreatable(schema, policyName, "AGGREGATION_POLICY", parts, orReplace, ifNotExists,
                Privilege.CREATE_AGGREGATION_POLICY);
            if (orReplace) {
                if (catalog.isAggregationPolicyInUse(policyName)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Policy "
                        + policyName.toUpperCase(Locale.ROOT)
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                if (schema.getAggregationPolicyExact(policyName) != null) {
                    schema.dropAggregationPolicy(policyName);
                }
            } else if (schema.getAggregationPolicyExact(policyName) != null) {
                throw policyNameTaken(parts);
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            PolicyBodyCheck.requireNumericLimits(body);
            // An aggregation policy protects neither a column nor a table whose tag it could read.
            final ParseTree written = ctx.bodyDefinition() != null ? ctx.bodyDefinition() : ctx.booleanExpr();
            PolicyObjectTagCalls.refuseIn(written, PolicyObjectTagCalls.ON_CURRENT_TABLE);
            PolicyObjectTagCalls.refuseIn(written, PolicyObjectTagCalls.ON_CURRENT_COLUMN);
            final AggregationPolicy policy = new AggregationPolicy(policyName, body);
            if (properties.comment() != null) {
                policy.setComment(properties.comment());
            }
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addAggregationPolicy(policy);
            logger.trace("Created aggregation policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0)); }
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
        final PolicyProperties properties =
            PolicyProperties.read(ddl, ctx.policyCreateProperty(), "PROJECTION_POLICY", false);
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
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            // The name as written, as a masking policy's is (live-verified).
            final String policyName = parts[parts.length - 1];
            requirePolicyCreatable(schema, policyName, "PROJECTION_POLICY", parts, orReplace, ifNotExists,
                Privilege.CREATE_PROJECTION_POLICY);
            if (orReplace) {
                if (catalog.isProjectionPolicyInUse(policyName)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Policy "
                        + policyName.toUpperCase(Locale.ROOT)
                        + " cannot be dropped/replaced as it is associated with one or more entities.");
                }
                if (schema.getProjectionPolicyExact(policyName) != null) {
                    schema.dropProjectionPolicy(policyName);
                }
            } else if (schema.getProjectionPolicyExact(policyName) != null) {
                throw policyNameTaken(parts);
            }
            final String body = ctx.bodyDefinition() != null
                ? ddl.getOriginalText(ctx.bodyDefinition())
                : ddl.getOriginalText(ctx.booleanExpr());
            PolicyBodyCheck.requireNumericLimits(body);
            // A projection policy protects a column: the table's tag is not its to read.
            PolicyObjectTagCalls.refuseIn(ctx.bodyDefinition() != null ? ctx.bodyDefinition() : ctx.booleanExpr(),
                PolicyObjectTagCalls.ON_CURRENT_TABLE);
            final ProjectionPolicy policy = new ProjectionPolicy(policyName, body);
            if (properties.comment() != null) {
                policy.setComment(properties.comment());
            }
            policy.setOwner(catalog.currentRoleForOwner());
            schema.addProjectionPolicy(policy);
            logger.trace("Created projection policy: {}", qn);
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0)); }
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
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            final String contactName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
            ddl.checkCreatePrivilege(Privilege.CREATE_CONTACT, schema);
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
        } catch (final RuntimeException e) { ddl.handleIfNotExists(ifNotExists, e, "object", ctx.qualifiedName(0)); }
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

    /**
     * CREATE [OR REPLACE] USER [IF NOT EXISTS]. The property list's form is checked first, as the account compiles
     * it; then the name: IF NOT EXISTS leaves a user it finds as it is, and without OR REPLACE a user that exists is
     * refused ({@code Object 'U1' already exists.}); last the checks against the new user's type, the ranges and
     * the key policy, all before anything changes, so a refused statement leaves no user behind and a refused OR
     * REPLACE leaves the old user standing.
     */
    public Object handleCreateUser(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String userName = getText(ctx.identifier(0));
        final List<FrostlakeParser.UserPropertyContext> properties = ctx.userProperties() != null
            ? ctx.userProperties().userProperty() : new ArrayList<FrostlakeParser.UserPropertyContext>();
        UserProperties.checkForm(properties);
        AccountPrivileges.requireOnAccount(queryExecutor, catalog, Privilege.CREATE_USER);
        if (ctx.or_replace() == null && catalog.hasUserExact(userName)) {
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                logger.debug("User already exists (IF NOT EXISTS): {}", userName);
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + userName + "' already exists."));
        }
        UserProperties.checkNew(properties);
        // A network policy the list names has to exist before the user is made.
        final String networkPolicy = UserNetworkPolicy.named(catalog, properties);
        if (ctx.or_replace() != null && catalog.hasUserExact(userName)) {
            final String held = catalog.getUserExact(userName).getName();
            catalog.dropUserExact(userName);
            SecurityObjectCommandHandler.forgetUser(catalog, held);
        }
        if (ctx.userProperties() != null) {
            catalog.createUser(userName, null, null);
            UserProperties.apply(catalog.getUserExact(userName), properties);
            UserNetworkPolicy.attach(catalog, catalog.getUserExact(userName), networkPolicy);
        } else {
            catalog.createUser(userName);
        }
        final String comment = ddl.extractCommentFromList(ctx.commentClause());
        if (comment != null) {
            final User user = catalog.getUserExact(userName);
            user.setComment(comment);
        }
        logger.trace("Created user: {}", userName);
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
