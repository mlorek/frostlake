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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.List;

/**
 * The canonical parts of the class a {@code SHOW <class>} or {@code DROP <class> <instance>} names. An empty middle
 * part, {@code db..c}, is the database's PUBLIC schema, and an IDENTIFIER() reference spells its parts as it does
 * for any object. A word live's lexer keeps as a keyword — {@code T}, {@code ALERT}, … — never gets here: the
 * parser refuses it where it stands.
 */
final class ClassNameParts {

    private ClassNameParts() {
    }

    /**
     * The name's parts.
     *
     * @param name          the written class name
     * @param queryExecutor the executor that reads an IDENTIFIER() argument
     * @return its canonical parts
     */
    static String[] of(final FrostlakeParser.ClassNameContext name, final QueryExecutor queryExecutor) {
        if (name.identifierArgument() != null) {
            return queryExecutor.resolveIdentifierArgument(name.identifierArgument());
        }
        if (name.nameStartPart() == null) {
            return SqlIdentifiers.canonicalTextParts(name.getStart().getText());
        }
        final List<String> parts = new ArrayList<>();
        parts.add(ParseTreeText.namePartText(name.nameStartPart()));
        if (name.DOT().size() > name.namePart().size()) {
            parts.add("PUBLIC");
        }
        for (final FrostlakeParser.NamePartContext part : name.namePart()) {
            parts.add(ParseTreeText.namePartText(part));
        }
        return parts.toArray(new String[0]);
    }
}
