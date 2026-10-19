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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

/**
 * ALTER EXTERNAL TABLE. Frostlake models no external table, so the statement is judged as the account judges it
 * and then misses its table: the action first — a property that is no property of an external table, set or
 * unset, a value AUTO_REFRESH does not take, a word other than LOCATION before a partition's location — then the
 * name, whose
 * missing database or schema is refused whether or not IF EXISTS is written, as is a table or a view of that name
 * ({@code Invalid object type: 'TABLE'}); a missing table is refused as an external table by its full path, which
 * IF EXISTS forgives (live-verified).
 */
final class ExternalTableAlteration {

    private static final String AUTO_REFRESH = "AUTO_REFRESH";
    private static final String LOCATION = "LOCATION";

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    ExternalTableAlteration(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /**
     * Runs the statement.
     *
     * @param ctx the ALTER EXTERNAL TABLE
     * @return null: the statement changes nothing
     */
    Object run(final FrostlakeParser.AlterStatementContext ctx) {
        final FrostlakeParser.ExternalTableActionContext action = ctx.externalTableAction();
        requireLocationWord(action.locationWord);
        requireProperty(action);
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final RelationKind found = owner.relationKindOf(name);
        if (found != null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid object type: '" + found.spelling() + "'"));
        }
        if (ctx.if_exists() != null) {
            return null;
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("External table", owner.qualifiedName(name)));
    }

    /** A partition's location follows the word LOCATION: any other word is refused where it stands. */
    private static void requireLocationWord(final Token word) {
        if (word == null || LOCATION.equalsIgnoreCase(word.getText())) {
            return;
        }
        final int[] shown = LeadingCommentOffset.rebase(word.getLine(), word.getCharPositionInLine());
        throw new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
            + shown[1] + " unexpected '" + word.getText() + "'."));
    }

    /**
     * SET takes AUTO_REFRESH with TRUE or FALSE: another value and another property are refused, the property by
     * its name as written. UNSET takes none: AUTO_REFRESH cannot lose its value, and any other word — the first of
     * a list, TAG written alone — is no property.
     */
    private static void requireProperty(final FrostlakeParser.ExternalTableActionContext action) {
        if (action.UNSET() != null) {
            final String written = action.TAG() != null ? action.TAG().getText() : action.identifier(0).getText();
            if (action.TAG() == null && AUTO_REFRESH.equals(SqlIdentifiers.canonical(action.identifier(0)))) {
                throw new RuntimeException(SqlCompilationError.of("invalid value 'null' for property '" + written
                    + "'"));
            }
            throw new RuntimeException(SqlCompilationError.of("invalid property '" + written
                + "' for 'EXTERNAL_TABLE'"));
        }
        if (action.SET() == null) {
            return;
        }
        final String property = SqlIdentifiers.canonical(action.identifier(0));
        if (!AUTO_REFRESH.equals(property)) {
            throw new RuntimeException(SqlCompilationError.of("invalid property '" + action.identifier(0).getText()
                + "' for 'EXTERNAL_TABLE'"));
        }
        if (action.TRUE() == null && action.FALSE() == null) {
            throw new RuntimeException(SqlCompilationError.of("invalid value [" + action.getChild(
                action.getChildCount() - 1).getText() + "] for parameter 'AUTO_REFRESH'"));
        }
    }
}
