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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Locale;
import java.util.Set;

/**
 * The functions that act on the account when they are called, which no stored query may hold (live-verified for
 * SYSTEM$ABORT_TRANSACTION): a view's query, the query a table is created from and a SQL function's body are each
 * refused while they compile, with {@code View or UDF contains a function with side effects [<name>]}. A column's
 * DEFAULT, an INSERT's source and an UPDATE's value may call one, and SYSTEM$WAIT is none of them.
 */
public final class SideEffectFunctions {

    private static final Set<String> NAMES = Set.of("SYSTEM$ABORT_TRANSACTION");

    private SideEffectFunctions() {
    }

    /**
     * Refuse a CREATE of a view, or of a table from a query, whose query calls one of the functions.
     *
     * @param statement the statement
     */
    static void rejectInCreate(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.CreateStatementContext create = statement.ddlStatement() == null ? null
            : statement.ddlStatement().createStatement();
        if (create == null || create.VIEW() == null && create.TABLE() == null || create.selectStatement() == null) {
            return;
        }
        rejectInStoredQuery(create.selectStatement());
    }

    /**
     * Refuse a query, or an expression, that is being stored when it calls one of the functions.
     *
     * @param stored the parsed query or expression
     */
    public static void rejectInStoredQuery(final ParseTree stored) {
        if (stored instanceof TerminalNode) {
            final Token token = ((TerminalNode) stored).getSymbol();
            if (token.getType() == FrostlakeParser.SYSTEM_FUNC
                    && NAMES.contains(token.getText().toUpperCase(Locale.ROOT))) {
                throw new RuntimeException("SQL compilation error: \nView or UDF contains a function with side"
                    + " effects [" + token.getText().toUpperCase(Locale.ROOT) + "]");
            }
            return;
        }
        for (int i = 0; i < stored.getChildCount(); i++) {
            rejectInStoredQuery(stored.getChild(i));
        }
    }
}
