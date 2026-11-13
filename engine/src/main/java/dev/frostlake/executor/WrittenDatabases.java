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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.SystemDatabase;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The databases a statement names that its session does not see (see {@link dev.frostlake.metastore.HiddenDatabases}):
 * the first part of every name of three parts, the database part of a name a SCHEMA scope or USE reads as a schema,
 * a name a DATABASE scope or USE reads as a database, and the database of a GET_DDL name written as a text. A
 * database is seen by a role holding any privilege on it, or holding MANAGE GRANTS; the SNOWFLAKE database by every
 * role. A name the statement never writes is not judged, so a listing over the account still reads every database.
 */
final class WrittenDatabases {

    private WrittenDatabases() {
    }

    /**
     * @param statement the statement
     * @param executor  the executor whose session judges what is seen
     * @return the canonical names of the databases it names and does not see
     */
    static Set<String> hiddenIn(final ParseTree statement, final QueryExecutor executor) {
        final Set<String> hidden = new HashSet<>();
        final SecurityManager security = executor.getSecurityManager();
        if (security == null) {
            return hidden;
        }
        final Set<String> written = new HashSet<>();
        collect(statement, executor, written);
        final Catalog catalog = executor.getCatalog();
        for (final String name : written) {
            if (SystemDatabase.is(name) || !catalog.hasDatabaseExact(name)) {
                continue;
            }
            final Database database = catalog.databaseExact(name);
            if (!security.sees("DATABASE", database.getName(), database.getOwner())) {
                hidden.add(name);
            }
        }
        return hidden;
    }

    private static void collect(final ParseTree node, final QueryExecutor executor, final Set<String> written) {
        if (node instanceof FrostlakeParser.ObjectNameContext) {
            try {
                add(executor.resolveObjectNameParts((FrostlakeParser.ObjectNameContext) node), node, written);
            } catch (final RuntimeException unresolved) {
                // A name that resolves to nothing names no database.
            }
            return;
        }
        if (node instanceof FrostlakeParser.TableQualifiedNameContext) {
            add(ParseTreeText.qualifiedNameParts((FrostlakeParser.TableQualifiedNameContext) node), node, written);
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameContext) {
            add(ParseTreeText.qualifiedNameParts((FrostlakeParser.QualifiedNameContext) node), node, written);
            return;
        }
        if (node instanceof FrostlakeParser.ShowScopeNameContext) {
            add(scopeParts((FrostlakeParser.ShowScopeNameContext) node, executor), node, written);
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            getDdlName((FrostlakeParser.FunctionCallExprContext) node, written);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), executor, written);
        }
    }

    /** A SHOW scope's name parts, canonical, or null where it resolves to nothing. */
    private static String[] scopeParts(final FrostlakeParser.ShowScopeNameContext scope,
                                       final QueryExecutor executor) {
        try {
            if (scope.identifierArgument() != null) {
                return SqlIdentifiers.identifierReferenceParts(executor.identifierArgumentText(
                    scope.identifierArgument()));
            }
            final String[] parts = new String[scope.identifier().size()];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = SqlIdentifiers.canonical(scope.identifier(i));
            }
            return parts;
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /** The database a name's parts name, by the scope word beside it. */
    private static void add(final String[] parts, final ParseTree name, final Set<String> written) {
        if (parts == null || parts.length == 0) {
            return;
        }
        if (parts.length >= 3) {
            written.add(parts[0]);
            return;
        }
        final ParseTree scope = name.getParent();
        final boolean use = scope instanceof FrostlakeParser.UseStatementContext;
        if (parts.length == 2 && (use || holdsWord(scope, FrostlakeLexer.SCHEMA))
                || parts.length == 1 && (use && !holdsWord(scope, FrostlakeLexer.SCHEMA)
                    && !holdsWord(scope, FrostlakeLexer.WAREHOUSE) && !holdsWord(scope, FrostlakeLexer.ROLE)
                    || !use && holdsWord(scope, FrostlakeLexer.DATABASE))) {
            written.add(parts[0]);
        }
    }

    /** Whether a node holds a word of the given kind among its own children. */
    private static boolean holdsWord(final ParseTree node, final int type) {
        if (node == null) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChild(i) instanceof TerminalNode
                    && ((TerminalNode) node.getChild(i)).getSymbol().getType() == type) {
                return true;
            }
        }
        return false;
    }

    /** The database of a GET_DDL's object name written as a text of three parts. */
    private static void getDdlName(final FrostlakeParser.FunctionCallExprContext call, final Set<String> written) {
        if (call.functionName() == null || !"GET_DDL".equals(call.functionName().getText().toUpperCase(Locale.ROOT))
                || call.functionArgList() == null || call.functionArgList().functionArg().size() < 2) {
            return;
        }
        final ParseTree argument = call.functionArgList().functionArg(1);
        final String spelled = argument.getText();
        if (argument.getChildCount() == 0 || !spelled.startsWith("'") || !spelled.endsWith("'")
                || spelled.length() < 2) {
            return;
        }
        final String text;
        try {
            text = SqlStringLiterals.decode(spelled);
        } catch (final RuntimeException unread) {
            return;
        }
        try {
            final String[] parts = SqlIdentifiers.identifierReferenceParts(text);
            if (parts.length >= 3) {
                written.add(parts[0]);
            }
        } catch (final RuntimeException unread) {
            // A text that names nothing names no database.
        }
    }
}
