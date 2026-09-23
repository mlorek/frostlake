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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import java.util.Locale;
import org.antlr.v4.runtime.Token;

/**
 * The SHOW scopes that name something no listing can list here: a scope kind ({@link ShowScopeKind}), and any
 * scope but {@code IN ACCOUNT} on a listing of account-level objects. All live-verified, listing by listing.
 *
 * <p>Live judges them in two groups. The ones the statement's shape decides come first, before a {@code LIMIT 0}
 * and a WITH PRIVILEGES are judged:
 *
 * <pre>
 *   SHOW TABLES IN VIEW [v]                  Unsupported statement type 'Cannot show objects of type TABLE in VIEW'.
 *   SHOW TABLES IN PIPE [p]                  … of type TABLE in PIPE
 *   SHOW TABLES IN SERVICE s                 … of type TABLE in SERVICE
 *   SHOW VIEWS IN CLASS c                    … of type VIEW in CLASS
 *   SHOW DATABASES IN DATABASE | SCHEMA | TABLE [x]    … of type DATABASE in DATABASE, SCHEMA, TABLE
 *   SHOW VIEWS IN TABLE [t]                  … of type VIEW in TABLE — every schema-level listing but COLUMNS
 *                                            and KEYS, a HYBRID one naming KEY VALUE TABLE
 *   SHOW TABLES IN ORGANIZATION | CONNECTION y         Must specify the full search path starting from database for Y
 *   SHOW PRIMARY KEYS IN VIEW y              the same full-search-path sentence; alone, IN VIEW lists as IN TABLE
 *   SHOW TABLES IN FAILOVER GROUP [g]        … of type TABLE in FAILOVER GROUP (REPLICATION GROUP alike)
 *   SHOW TABLES IN COMPUTE POOL p            … of type Table in Compute pool — the kinds spelled as words
 *   SHOW TABLES IN APPLICATION PACKAGE       syntax error at the end: the package needs its name (COMPUTE POOL alike)
 * </pre>
 *
 * <p>The name after a kind may be quoted, qualified or an IDENTIFIER() reference: the full-search-path sentence
 * names its last part as it resolves ({@code IN VIEW a.b} is B), an application scope named by a path does not
 * exist, and a service resolves as any object's name, its database or schema refused first when missing.
 *
 * <p>The ones that look the named object up come after them:
 *
 * <pre>
 *   SHOW TABLES IN APPLICATION a / CLASS c   Object does not exist, or operation cannot be performed.
 *   SHOW COLUMNS IN APPLICATION a            Application 'A' does not exist or not authorized.
 *   SHOW COLUMNS IN APPLICATION PACKAGE a    Application package 'A' does not exist or not authorized.
 *   SHOW FUNCTIONS IN SERVICE s              Service '&lt;DB&gt;.&lt;SCHEMA&gt;.S' does not exist or not authorized.
 *   SHOW ROLES IN CLASS c                    Object type or Class 'C' does not exist or not authorized.
 *   SHOW DATABASES IN [ACCOUNT] x            Object does not exist, or operation cannot be performed.
 *   SHOW COMPUTE POOLS IN [ACCOUNT] x        Account 'X' does not exist or not authorized.
 * </pre>
 *
 * <p>Which answer a listing gives to an APPLICATION, CLASS or SERVICE scope is its own fact ({@link ShowListing}).
 * {@code SHOW ROLES IN DATABASE [d]} is no refusal at all: it lists the database's roles, of which Frostlake
 * models none.
 */
final class ShowScopeKindRefusal {

    private ShowScopeKindRefusal() {
    }

    /**
     * Refuse a scope its shape rules out, or do nothing.
     *
     * @param ctx     the statement
     * @param listing its listing
     */
    static void refuseShape(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                            final QueryExecutor queryExecutor) {
        if (listing.isAccountLevel() && listing.objectType() != null) {
            final String container = containerKind(ctx);
            if (container != null && !(listing == ShowListing.ROLES && "DATABASE".equals(container))) {
                throw cannotShow(listing, container);
            }
        }
        if (ctx.TABLE() != null && !listing.isAccountLevel() && listing != ShowListing.COLUMNS
                && listing != ShowListing.KEYS && listing != ShowListing.SCHEMAS) {
            // A TABLE holds only columns and keys: every other schema-level listing refuses the scope by its
            // shape, before the table — named or not — is looked up.
            final String type = ctx.MODELS() != null ? "MODEL" : listing.objectType();
            if (type != null) {
                throw cannotShow(type, "TABLE");
            }
        }
        final ShowScopeKind kind = scopeKind(ctx);
        if (kind == null || listing.objectType() == null) {
            return;
        }
        if (kind.needsName() && ctx.showInstanceName().showScopeName() == null) {
            throw new RuntimeException(SqlCompilationError.of(lineAfter(ctx, ctx.showInstanceName().showKindWord)));
        }
        if (kind == ShowScopeKind.VIEW && listing == ShowListing.KEYS) {
            if (ctx.showInstanceName() != null) {
                throw fullSearchPath(ctx, queryExecutor);
            }
            return;
        }
        if (kind == ShowScopeKind.VIEW || kind == ShowScopeKind.PIPE || kind == ShowScopeKind.FAILOVER_GROUP
                || kind == ShowScopeKind.REPLICATION_GROUP) {
            throw cannotShow(listing, kind.spelling());
        }
        if (kind == ShowScopeKind.COMPUTE_POOL) {
            final String type = listing.objectType();
            throw cannotShow(type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT), "Compute pool");
        }
        if (kind == ShowScopeKind.ORGANIZATION || kind == ShowScopeKind.CONNECTION) {
            throw fullSearchPath(ctx, queryExecutor);
        }
        if (answer(kind, listing) == ShowScopeAnswer.CANNOT_SHOW) {
            throw cannotShow(listing, kind.spelling());
        }
    }

    /**
     * Refuse a scope whose object is looked up and missing, or do nothing.
     *
     * @param ctx           the statement
     * @param listing       its listing
     * @param catalog       the catalog the current database and schema come from
     * @param queryExecutor the executor that resolves an IDENTIFIER() reference
     */
    static void refuseMissing(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                              final Catalog catalog, final QueryExecutor queryExecutor) {
        final ShowScopeKind kind = scopeKind(ctx);
        if (kind == null) {
            if (listing.isAccountLevel() && ctx.objectName() != null && ctx.showInstanceName() == null
                    && (ctx.ACCOUNT() != null || containerKind(ctx) == null)) {
                throw missingAccountScope(ctx, listing, queryExecutor);
            }
            return;
        }
        switch (answer(kind, listing)) {
            case OBJECT_DOES_NOT_EXIST:
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            case MISSING_APPLICATION:
                final String[] application = instanceParts(ctx, queryExecutor);
                if (application.length > 1) {
                    throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
                }
                throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled(
                    kind == ShowScopeKind.APPLICATION_PACKAGE ? "Application package" : "Application",
                    SqlIdentifiers.spellCanonical(application[0])));
            case MISSING_CLASS:
                throw new RuntimeException(ShowScopeRefusal.missingClass(instanceParts(ctx, queryExecutor), catalog));
            case MISSING_SERVICE:
                throw missingService(instanceParts(ctx, queryExecutor), catalog);
            default:
                return;
        }
    }

    /**
     * The scope kind a SHOW names right after its IN, or null: a scope written with a DATABASE, SCHEMA, TABLE,
     * ACCOUNT or other kind keyword of its own names its object instead.
     */
    static ShowScopeKind scopeKind(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.IN() == null || ctx.ACCOUNT() != null || containerKind(ctx) != null || ctx.VIEW() != null
                || ctx.APPLICATION() != null || ctx.CLASS() != null) {
            return null;
        }
        return ShowScopeKind.of(ctx.objectName(), ctx.showInstanceName());
    }

    /** The container keyword a scope is written with — DATABASE, SCHEMA or TABLE — or null. */
    private static String containerKind(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.DATABASE() != null) {
            return "DATABASE";
        }
        if (ctx.SCHEMA() != null) {
            return "SCHEMA";
        }
        return ctx.TABLE() != null ? "TABLE" : null;
    }

    private static ShowScopeAnswer answer(final ShowScopeKind kind, final ShowListing listing) {
        if (kind == ShowScopeKind.SERVICE) {
            return listing.serviceScope();
        }
        if (kind == ShowScopeKind.APPLICATION || kind == ShowScopeKind.APPLICATION_PACKAGE) {
            return listing.applicationScope();
        }
        return kind == ShowScopeKind.CLASS ? listing.classScope() : ShowScopeAnswer.CANNOT_SHOW;
    }

    private static RuntimeException cannotShow(final ShowListing listing, final String scope) {
        return cannotShow(listing.objectType(), scope);
    }

    private static RuntimeException cannotShow(final String objectType, final String scope) {
        return new RuntimeException(SqlCompilationError.of("Unsupported statement type 'Cannot show objects of type "
            + objectType + " in " + scope + "'."));
    }

    /** The full-search-path sentence, naming the last part of the name written after the kind as it resolves. */
    private static RuntimeException fullSearchPath(final FrostlakeParser.ShowStatementContext ctx,
                                                   final QueryExecutor queryExecutor) {
        final String[] parts = instanceParts(ctx, queryExecutor);
        return new RuntimeException(SqlCompilationError.of(
            "Must specify the full search path starting from database for " + parts[parts.length - 1]));
    }

    /**
     * The canonical parts of the name written after a scope kind or a class: a word, a quoted or qualified name,
     * IDENTIFIER().
     */
    static String[] instanceParts(final FrostlakeParser.ShowStatementContext ctx, final QueryExecutor queryExecutor) {
        final FrostlakeParser.ShowInstanceNameContext instance = ctx.showInstanceName();
        final FrostlakeParser.ShowScopeNameContext name = instance.showScopeName();
        if (name == null) {
            return new String[] {instance.getText().toUpperCase(Locale.ROOT)};
        }
        if (name.identifierArgument() != null) {
            return queryExecutor.resolveIdentifierArgument(name.identifierArgument());
        }
        final String[] parts = new String[name.identifier().size()];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = ParseTreeText.getIdentifier(name.identifier(i));
        }
        return parts;
    }

    /**
     * The syntax error at the token after the last word of a scope that needs a name and has none: a modifier's
     * first word, the semicolon, or the end of the statement.
     *
     * @param ctx  the statement
     * @param last the scope's last word
     * @return the syntax-error line
     */
    static String lineAfter(final FrostlakeParser.ShowStatementContext ctx, final Token last) {
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        Token next = null;
        if (tail != null && tail.getChildCount() > 0) {
            next = tail.getStart();
        } else if (ctx.SEMI() != null) {
            next = ctx.SEMI().getSymbol();
        }
        if (next != null) {
            final int[] shown = LeadingCommentOffset.rebase(next.getLine(), next.getCharPositionInLine());
            return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + next.getText()
                + "'.";
        }
        final int[] shown = LeadingCommentOffset.rebase(last.getLine(),
            last.getCharPositionInLine() + last.getText().length());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '<EOF>'.";
    }

    /**
     * An account-level listing scoped by anything but a bare {@code IN ACCOUNT}: compute pools name the account the
     * scope's last part would be, the other listings answer that no object exists.
     */
    private static RuntimeException missingAccountScope(final FrostlakeParser.ShowStatementContext ctx,
                                                        final ShowListing listing,
                                                        final QueryExecutor queryExecutor) {
        if (listing != ShowListing.COMPUTE_POOLS) {
            return new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        return new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Account",
            SqlIdentifiers.spellCanonical(parts[parts.length - 1])));
    }

    /**
     * A service is resolved as any object's name — a bare one in the current schema, a path's database and schema
     * refused first when missing — and named in full.
     */
    private static RuntimeException missingService(final String[] parts, final Catalog catalog) {
        if (parts.length > 1) {
            final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
            return new RuntimeException(SqlCompilationError.doesNotExist("Service",
                owner.qualifiedName(parts[parts.length - 1])));
        }
        final String database = catalog.getCurrentDatabase();
        final String schema = catalog.getCurrentSchema();
        if (database == null || schema == null) {
            return new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        return new RuntimeException(SqlCompilationError.doesNotExist("Service",
            QualifiedName.join(database, schema, parts[0])));
    }
}
