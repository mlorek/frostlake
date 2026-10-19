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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Locale;

/**
 * The names a GRANT or a REVOKE reads beside its object, each written as a name or as an IDENTIFIER() reference:
 * the role or the user that is granted or holds a grant, a bulk grant's schema or database, a database role. A
 * reference that spells a path names no role and no user, and is answered by the sentence that names nothing
 * (live-verified).
 */
final class PrincipalNames {

    private PrincipalNames() {
    }

    /**
     * @param name          a role's or a user's name as the statement writes it
     * @param queryExecutor the executor that resolves an IDENTIFIER() reference
     * @return the canonical name
     */
    static String of(final FrostlakeParser.PrincipalNameContext name, final QueryExecutor queryExecutor) {
        if (name.identifier() != null) {
            return SqlIdentifiers.canonical(name.identifier());
        }
        if (name.identifierArgument() == null) {
            return name.getText().toUpperCase(Locale.ROOT);
        }
        final String[] parts = queryExecutor.resolveIdentifierArgument(name.identifierArgument());
        if (parts.length != 1) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        return parts[0];
    }

    /**
     * @param name          a schema's, a database's or a database role's name as the statement writes it
     * @param queryExecutor the executor that resolves an IDENTIFIER() reference
     * @return the canonical name parts
     */
    static String[] parts(final FrostlakeParser.ReferencedNameContext name, final QueryExecutor queryExecutor) {
        if (name.wholeObjectName() == null) {
            return new String[] {name.getText().toUpperCase(Locale.ROOT)};
        }
        return queryExecutor.resolveWholeObjectNameParts(name.wholeObjectName());
    }

    /**
     * Whether a name is written as an IDENTIFIER() reference, whose path of too many parts names nothing where a
     * written path is judged by its statement.
     */
    static boolean isReference(final FrostlakeParser.ReferencedNameContext name) {
        return name.wholeObjectName() != null && name.wholeObjectName().identifierArgument() != null;
    }
}
