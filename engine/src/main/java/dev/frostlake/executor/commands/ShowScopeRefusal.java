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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.parser.ClassNameWords;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * The SHOW statements that parse but name nothing Frostlake can list, refused before the listing runs —
 * live-verified across every listing that takes a scope.
 *
 * <p>★ A CLASS LISTING, {@code SHOW <class>}, is refused as its class is: see {@link #missingClass}. So is
 * {@code DROP <class> <instance>}.
 *
 * <p>★ AN ACCOUNT SCOPE THAT NAMES SOMETHING, {@code IN ACCOUNT db.s}, is refused naming the last part as
 * resolved, without quotes and without a closing period:
 * {@code Must specify the full search path starting from database for S}. A listing of account-level objects
 * looks the name up instead, later ({@link ShowScopeKindRefusal}).
 *
 * <p>★ A CLASS-INSTANCE SCOPE, {@code IN <class> <instance>} — a word written after a bare scope name that is
 * no scope kind ({@link ShowScopeKind}, refused by {@link ShowScopeKindRefusal}). No
 * class is modelled, so every one is refused, with the sentence of the listing:
 *
 * <pre>
 *   SHOW TABLES / SCHEMAS / STAGES IN c i     syntax error line 1 at position 15 unexpected 'c'.
 *   SHOW FUNCTIONS / PROCEDURES / ROLES IN c i    SQL compilation error: Object type or Class 'C' does not exist …
 *   every other listing                       Unsupported statement type 'Cannot show objects of type VIEW in INSTANCE'.
 * </pre>
 *
 * <p>The first carries no prefix at all and echoes the class name as written — only its first part when the
 * name has an empty middle part, {@code TOK_OBJECT_LITERAL} for an IDENTIFIER() reference, placed at its
 * argument. The first and the last are judged with the statement's shape; the routine listings look the class
 * up, after a {@code LIMIT 0} and a WITH PRIVILEGES are judged ({@link #refuseMissingClass}). They resolve the
 * class like an object: a two-part name reads its schema in the current database, and a missing database or
 * schema is refused as such before the class. A built-in class ({@link BuiltInClass}) exists, so its instance is
 * resolved and missed instead: {@code SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST m} is
 * {@code Instance '<DB>.<SCHEMA>.M' does not exist or not authorized.} A single unquoted keyword — {@code USER},
 * {@code DATE}, {@code DATASET}, {@code MATERIALIZED}, … — or an ODBC escape word such as {@code T} is no class
 * name at all, so the word after it is a syntax error: the parser refuses it (ShowScopeWords), except an
 * IDENTIFIER() reference, which is refused here at its IDENTIFIER word.
 */
final class ShowScopeRefusal {

    private ShowScopeRefusal() {
    }

    /**
     * Refuse the statement's scope when it is one of the two, or do nothing.
     *
     * @param ctx           the SHOW statement
     * @param catalog       the catalog the routine listings resolve a class name against
     * @param queryExecutor the executor that resolves an IDENTIFIER() reference
     * @param listing       the statement's listing
     */
    static void requireListable(final FrostlakeParser.ShowStatementContext ctx, final Catalog catalog,
                                final QueryExecutor queryExecutor, final ShowListing listing) {
        final FrostlakeParser.ObjectNameContext name = ctx.objectName();
        if (name == null || ShowScopeKindRefusal.scopeKind(ctx) != null) {
            return;
        }
        if (ctx.ACCOUNT() != null) {
            if (listing.isAccountLevel()) {
                return;
            }
            final String[] parts = queryExecutor.resolveObjectNameParts(name);
            throw new RuntimeException(SqlCompilationError.of(
                "Must specify the full search path starting from database for " + parts[parts.length - 1]));
        }
        final FrostlakeParser.ShowInstanceNameContext instance = ctx.showInstanceName();
        if (instance == null) {
            return;
        }
        if (ctx.FUNCTIONS() != null && ctx.TERSE() == null && ctx.USER() == null && ctx.BUILTIN() == null
                && isWord(name, "MODEL")) {
            // The one listing that reads MODEL as a scope kind, the plain SHOW FUNCTIONS: no model exists
            // (live-verified).
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        if (isKeywordClassName(name)) {
            // The parser reads no other name after such a word; an IDENTIFIER() reference is refused here.
            throw new RuntimeException(SqlCompilationError.of(syntaxErrorAt(instance.getStart(),
                instance.getStart().getText())));
        }
        if (ctx.TABLES() != null && ctx.ICEBERG() == null && ctx.EVENT() == null && ctx.DYNAMIC() == null && ctx.HYBRID() == null
                || ctx.SCHEMAS() != null || ctx.STAGES() != null) {
            if (name.identifierArgument() != null) {
                throw new RuntimeException(syntaxErrorAt(name.identifierArgument().getStart(), "TOK_OBJECT_LITERAL"));
            }
            throw new RuntimeException(syntaxErrorAt(name.getStart(), writtenClassName(name)));
        }
        if (looksUpClass(ctx, listing)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Unsupported statement type 'Cannot show objects of type " + listing.objectType() + " in INSTANCE'."));
    }

    /**
     * Refuse a routine or role listing whose scope names an instance of a class, or do nothing. The class is looked
     * up once a {@code LIMIT 0} and a WITH PRIVILEGES have been judged (live-verified): a built-in class exists, so
     * the instance it names is resolved as any schema object's name and misses; any other class is missing itself.
     *
     * @param ctx           the statement
     * @param catalog       the catalog the class and its instance resolve against
     * @param queryExecutor the executor that resolves an IDENTIFIER() reference
     * @param listing       the statement's listing
     */
    static void refuseMissingClass(final FrostlakeParser.ShowStatementContext ctx, final Catalog catalog,
                                   final QueryExecutor queryExecutor, final ShowListing listing) {
        final FrostlakeParser.ObjectNameContext name = ctx.objectName();
        if (name == null || ctx.showInstanceName() == null || ctx.ACCOUNT() != null
                || ShowScopeKindRefusal.scopeKind(ctx) != null || !looksUpClass(ctx, listing)) {
            return;
        }
        final String[] parts = queryExecutor.resolveObjectNameParts(name);
        if (BuiltInClass.isBuiltIn(parts, catalog)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Instance",
                BuiltInClass.instancePath(ShowScopeKindRefusal.instanceParts(ctx, queryExecutor), catalog)));
        }
        throw new RuntimeException(missingClass(parts, catalog));
    }

    /** Whether the listing looks the class of a class-instance scope up: the routine listings and the role listing. */
    private static boolean looksUpClass(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing) {
        return ctx.FUNCTIONS() != null || ctx.PROCEDURES() != null || listing == ShowListing.ROLES;
    }

    /**
     * Refuse a class listing, {@code SHOW <class>}: its class is missing, before its scope or modifiers are read.
     *
     * @param ctx           the statement
     * @param catalog       the catalog the class name resolves against
     * @param queryExecutor the executor that reads an IDENTIFIER() class name
     */
    static void refuseClassListing(final FrostlakeParser.ShowClassStatementContext ctx, final Catalog catalog,
                                   final QueryExecutor queryExecutor) {
        throw new RuntimeException(missingClass(ClassNameParts.of(ctx.className(), queryExecutor), catalog));
    }

    /**
     * Whether the scope is a single unquoted word live does not read as a class name: a keyword to live's lexer
     * ({@code ClassNameWords.isClassWord}). Live reads such a word as the scope itself, so the word after it is its
     * syntax error.
     */
    private static boolean isKeywordClassName(final FrostlakeParser.ObjectNameContext name) {
        final FrostlakeParser.QualifiedNameContext written = name.qualifiedName();
        if (written == null || !written.namePart().isEmpty() || !written.DOT().isEmpty()) {
            return false;
        }
        final Token word = written.getStart();
        return word.getType() != FrostlakeLexer.QUOTED_IDENTIFIER && !ClassNameWords.isClassWord(word);
    }

    /** Whether the scope is the one unquoted word given, in any case. */
    private static boolean isWord(final FrostlakeParser.ObjectNameContext name, final String word) {
        final FrostlakeParser.QualifiedNameContext written = name.qualifiedName();
        return written != null && written.namePart().isEmpty() && written.DOT().isEmpty()
            && written.getStart().getType() != FrostlakeLexer.QUOTED_IDENTIFIER
            && word.equalsIgnoreCase(written.getStart().getText());
    }

    /** The class name as written — up to its empty middle part, when it has one. */
    private static String writtenClassName(final FrostlakeParser.ObjectNameContext name) {
        final FrostlakeParser.QualifiedNameContext written = name.qualifiedName();
        if (written == null) {
            return ParseTreeText.getOriginalText(name);
        }
        final ParserRuleContext shown = written.DOT().size() > written.namePart().size()
            ? written.nameStartPart() : written;
        return ParseTreeText.getOriginalText(shown);
    }

    /** A syntax error line placed at {@code token}, naming {@code text}, with no prefix. */
    private static String syntaxErrorAt(final Token token, final String text) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + text + "'.";
    }

    /**
     * The routine listings' refusal of a class that does not exist: a one-part name as it resolves, a longer
     * one qualified in full once its database and schema are found — or the database or schema that is not. The
     * schemas of the SNOWFLAKE database are found as live finds them ({@link BuiltInClass#resolvesSchema}).
     */
    static String missingClass(final String[] parts, final Catalog catalog) {
        if (parts.length > 3) {
            return SqlCompilationError.objectDoesNotExist();
        }
        if (parts.length == 1) {
            return classDoesNotExist(SqlIdentifiers.spellCanonical(parts[0]));
        }
        final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        if (databaseName == null) {
            return SqlCompilationError.objectDoesNotExist();
        }
        final Database database = catalog.databaseExact(databaseName);
        final String schemaName = parts[parts.length - 2];
        if (!BuiltInClass.resolvesSchema(database, schemaName)) {
            return SqlCompilationError.doesNotExist("Schema", QualifiedName.join(database.getName(), schemaName));
        }
        return classDoesNotExist(SqlIdentifiers.spellAlreadyCanonicalPath(
            QualifiedName.join(database.getName(), schemaName, parts[parts.length - 1])));
    }

    private static String classDoesNotExist(final String spelled) {
        return SqlCompilationError.inline("Object type or Class '" + spelled + "' does not exist or not authorized.");
    }
}
