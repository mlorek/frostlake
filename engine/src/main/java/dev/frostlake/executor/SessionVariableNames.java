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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The names a session SET or UNSET writes, and what its values may not hold (live-verified).
 *
 * <p>A name written with a qualifier — {@code SET s.v = 1}, {@code UNSET db.s.v} — is a schema's variable, an
 * object no schema here holds: the statement is read and refused by the name as written, {@code Schema Variable
 * 'S.V' does not exist or not authorized.{2}} — the {@code {2}} is the account's own. A bind in a value has
 * nothing to hold outside a block: {@code SET x = :1} is {@code Bind variable :1 not set.} at the bind.
 */
final class SessionVariableNames {

    private SessionVariableNames() {
    }

    /**
     * The session variables a statement names, upper-cased, refusing the first that is a schema's.
     *
     * @param written the names as parsed
     * @return the names
     */
    static List<String> of(final List<FrostlakeParser.SessionVariableContext> written) {
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.SessionVariableContext name : written) {
            if (name.identifier().size() > 1) {
                final StringBuilder spelled = new StringBuilder();
                for (final FrostlakeParser.IdentifierContext part : name.identifier()) {
                    spelled.append(spelled.length() > 0 ? "." : "")
                        .append(SqlIdentifiers.spellCanonical(SqlIdentifiers.canonical(part)));
                }
                throw new RuntimeException(SqlCompilationError.of(
                    "Schema Variable '" + spelled + "' does not exist or not authorized.{2}"));
            }
            names.add(SqlIdentifiers.canonical(name.identifier(0)).toUpperCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * Refuse a value that still holds a named or numbered bind, at the bind.
     *
     * @param value the value's parse tree
     */
    static void refuseUnsetBinds(final ParseTree value) {
        if (value instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) value;
            final Token colon = bind.getStart();
            throw new RuntimeException(SqlCompilationError.at(colon.getLine(), colon.getCharPositionInLine(),
                "Bind variable " + bind.getText() + " not set."));
        }
        for (int i = 0; i < value.getChildCount(); i++) {
            refuseUnsetBinds(value.getChild(i));
        }
    }
}
