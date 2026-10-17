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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.Token;

/**
 * The SHOW listings of the kinds this engine models no object of: each answers the account's columns
 * ({@link UnmodelledListing}) and no rows, once its scope and its modifiers are judged as the account judges them
 * (live-verified, listing by listing):
 *
 * <pre>
 *   RESOURCE MONITORS, SHARES, CONNECTIONS,     the account's own things: any scope but IN ACCOUNT is refused by
 *   APPLICATIONS, APPLICATION PACKAGES          its shape, and a named account is looked up and missed
 *   SNAPSHOTS, GIT REPOSITORIES, SEMANTIC       a schema's things: a schema or a database that does not exist is
 *   VIEWS, the SESSION, AUTHENTICATION,         named, and a table is no scope of theirs
 *   PACKAGES and PRIVACY POLICIES, EXTERNAL
 *   and DATA METRIC FUNCTIONS
 *   INDEXES                                     a schema's things or a table's; a schema or a database that does
 *                                               not exist is answered by the sentence that names nothing
 *   CHANNELS                                    a schema's things or a table's, a bare name after IN being a
 *                                               table's and a schema named in full, as the key listings read them
 *   REPLICATION DATABASES, REPLICATION GROUPS,  a LIKE and an account, named after ACCOUNT or alone; no TERSE and
 *   FAILOVER GROUPS                             none of the modifiers
 *   REPLICATION ACCOUNTS                        a LIKE alone
 *   DELEGATED AUTHORIZATIONS                    BY USER u or TO SECURITY INTEGRATION i, each looked up and
 *                                               refused when missing, and none of the modifiers
 *   REGIONS                                     the public regions ({@link RegionsListing}), and no scope
 * </pre>
 *
 * <p>HISTORY is read by every one of the first four families but CHANNELS and the SESSION and AUTHENTICATION
 * POLICIES, which refuse it where it stands.
 *
 * <p>SESSION POLICIES is read here with the others and is the one modelled: once its scope is judged, the session
 * policies of the scope's schemas are its rows ({@link SecurityObjectCommandHandler#showRows}).
 */
final class UnmodelledListings {

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    UnmodelledListings(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /**
     * Refuse what the listing's own grammar does not read, where the account's parser stops, or do nothing.
     *
     * @param ctx     the statement
     * @param listing its listing
     */
    static void requireParsed(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing) {
        if (ctx.replicationListingKind() != null) {
            requireReplicationParsed(ctx);
        } else if (ctx.unmodelledListingKind() != null && ctx.HISTORY() != null && !listing.readsHistory()) {
            throw syntaxErrorAt(ctx.HISTORY().getSymbol());
        }
    }

    /**
     * The replication listings open on REPLICATION or FAILOVER, the second listing groups alone; any other word
     * there is no listing, and the statement stops at the word after it. TERSE stops at the listing's own word.
     * REPLICATION ACCOUNTS reads no scope. For the others a scope written ACCOUNT needs the account's name, and
     * DATABASE or SCHEMA written alone is no name: each stops at what follows it.
     */
    private static void requireReplicationParsed(final FrostlakeParser.ShowStatementContext ctx) {
        final FrostlakeParser.ReplicationListingKindContext kind = ctx.replicationListingKind();
        final boolean replication = "REPLICATION".equalsIgnoreCase(kind.word.getText());
        final boolean failover = "FAILOVER".equalsIgnoreCase(kind.word.getText());
        if (!replication && !(failover && kind.GROUPS() != null)) {
            throw syntaxErrorAt(kind.getStop());
        }
        if (ctx.TERSE() != null) {
            throw syntaxErrorAt(kind.word);
        }
        if (ctx.IN() == null) {
            return;
        }
        if (kind.ACCOUNTS() != null) {
            // REPLICATION ACCOUNTS reads no scope: the statement stops at what follows the IN.
            throw SyntaxAfter.unexpected(ctx.IN());
        }
        if (ctx.objectName() == null) {
            if (ctx.showTail() == null || ctx.showTail().getChildCount() == 0) {
                throw SyntaxAfter.unexpected(ctx.ACCOUNT());
            }
        } else if (ctx.ACCOUNT() == null && isScopeKeywordAlone(ctx.objectName())) {
            throw SyntaxAfter.unexpected(ctx.objectName());
        }
    }

    /** Whether a name is the one unquoted word DATABASE or SCHEMA, a keyword the account reads no name in. */
    private static boolean isScopeKeywordAlone(final FrostlakeParser.ObjectNameContext name) {
        final FrostlakeParser.QualifiedNameContext written = name.qualifiedName();
        if (written == null || !written.DOT().isEmpty() || !written.namePart().isEmpty()) {
            return false;
        }
        final int word = written.getStart().getType();
        return word == FrostlakeLexer.DATABASE || word == FrostlakeLexer.SCHEMA;
    }

    /**
     * The word a replication listing's {@code IN ACCOUNT} reads as the account's name when nothing else follows
     * it: a LIMIT or a STARTS written there is that name (live-verified), and the account it names is looked up
     * and missed.
     *
     * @param ctx the statement
     * @return the word, or null when the scope names its account or names none
     */
    static Token accountNamedByModifier(final FrostlakeParser.ShowStatementContext ctx) {
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (ctx.replicationListingKind() == null || ctx.ACCOUNT() == null || ctx.objectName() != null
                || tail == null || tail.getChildCount() != 1 || tail.getStart() != tail.getStop()) {
            return null;
        }
        final int word = tail.getStart().getType();
        return word == FrostlakeLexer.LIMIT || word == FrostlakeLexer.STARTS ? tail.getStart() : null;
    }

    private static RuntimeException syntaxErrorAt(final Token token) {
        return new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(token, token.getText())));
    }

    /**
     * The listing: its scope looked up as the account looks it up, and then its columns.
     *
     * @param ctx     the statement
     * @param listing its listing
     * @param scope   the scope the statement's IN clause, or the lack of one, resolves to
     * @return the listing's columns, and the regions' rows
     */
    ResultSet show(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                   final ShowListingScope scope) {
        if (listing == ShowListing.REGIONS) {
            return RegionsListing.show(ctx);
        }
        final Token named = accountNamedByModifier(ctx);
        if (named != null) {
            throw new RuntimeException(listing.namesMissingAccount()
                ? SqlCompilationError.doesNotExistAsSpelled("Account", named.getText().toUpperCase(Locale.ROOT))
                : SqlCompilationError.objectDoesNotExist());
        }
        List<Schema> schemas = null;
        if (listing == ShowListing.DELEGATED_AUTHORIZATIONS) {
            requireGrantee(ctx.delegatedAuthorizationScope());
        } else if (!listing.isAccountLevel()) {
            schemas = requireScope(ctx, listing, scope);
        }
        final ResultSet columns = UnmodelledListing.of(listing).listing(ctx.TERSE() != null);
        if (listing == ShowListing.SESSION_POLICIES && schemas != null) {
            // The one kind listed here that is modelled: the session policies of the scope's schemas.
            return new ResultSet(columns.getColumns(),
                SecurityObjectCommandHandler.showRows(SecurityObjectKind.SESSION_POLICY, schemas));
        }
        return columns;
    }

    /**
     * Look the scope of a schema-level listing up, refusing the one that names nothing.
     *
     * @return the schemas the scope covers, or null for a table's scope
     */
    private List<Schema> requireScope(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                                      final ShowListingScope scope) {
        final boolean bare = ctx.IN() != null && ctx.ACCOUNT() == null && ctx.DATABASE() == null
            && ctx.SCHEMA() == null && ctx.TABLE() == null;
        if (ctx.TABLE() != null || bare && listing.readsTableAfterIn()) {
            // IN VIEW with no name lists as IN TABLE with none does.
            if (ctx.objectName() != null && ShowScopeKindRefusal.scopeKind(ctx) != ShowScopeKind.VIEW) {
                requireTable(queryExecutor.resolveObjectNameParts(ctx.objectName()), listing);
            }
            return null;
        }
        if (listing.readsTableAfterIn() && ctx.SCHEMA() != null && ctx.objectName() != null) {
            final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
            if (parts.length == 1) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Must specify the full search path starting from database for " + parts[0]));
            }
        }
        return scope.schemas(catalog, listing == ShowListing.INDEXES);
    }

    /**
     * Look up the user or the security integration a DELEGATED AUTHORIZATIONS listing names: BY reads a kind and a
     * name, and with one word after it, or a kind that is not USER, the kind is the one missing.
     */
    private void requireGrantee(final FrostlakeParser.DelegatedAuthorizationScopeContext scope) {
        if (scope == null) {
            return;
        }
        if (scope.BY() != null) {
            if (scope.USER() == null || scope.qualifiedName() == null) {
                throw missingObjectType();
            }
            final String[] user = ParseTreeText.qualifiedNameParts(scope.qualifiedName());
            if (user.length > 1) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            catalog.getUser(user[0]);
            return;
        }
        if (scope.INTEGRATION() == null) {
            throw missingObjectType();
        }
        if (scope.identifier() == null) {
            throw SyntaxAfter.unexpected(scope.INTEGRATION());
        }
        final String integration = ParseTreeText.getIdentifier(scope.identifier());
        if (catalog.getIntegrations().find(integration) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", integration));
        }
    }

    private static RuntimeException missingObjectType() {
        return new RuntimeException(SqlCompilationError.of(
            "Invalid or missing object type. Please specify a valid object type"));
    }

    /**
     * A table scope names a table: its database and schema are refused first when missing, then the table. A view
     * holds indexes to list — none — and no channels: the channel listing refuses it by a sentence that names no
     * view at all.
     */
    private void requireTable(final String[] parts, final ShowListing listing) {
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final RelationKind kind = owner.relationKindOf(name);
        if (kind == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table",
                parts.length == 1 ? QualifiedName.join(name) : owner.qualifiedName(name)));
        }
        if (listing == ShowListing.CHANNELS && kind == RelationKind.VIEW) {
            throw new RuntimeException(SqlCompilationError.of("View 'VIEW' does not exist or not authorized."));
        }
    }
}
