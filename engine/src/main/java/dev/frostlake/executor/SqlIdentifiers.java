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

/**
 * Canonical form of a SQL identifier, matching Snowflake's identifier resolution:
 *
 * <ul>
 *   <li>an <b>unquoted</b> identifier folds to UPPERCASE ({@code site} &rarr; {@code SITE}, so it lands in
 *       the catalog, INFORMATION_SCHEMA, SHOW output and result-set column labels the same way Snowflake
 *       stores it);</li>
 *   <li>a <b>"double-quoted"</b> identifier keeps its exact case (the surrounding quotes are stripped);</li>
 *   <li>a {@code $n} positional parameter becomes {@code COLUMNn}.</li>
 * </ul>
 *
 * <p>Name resolution is already case-insensitive (storage keys are upper-cased and column lookups use
 * {@code equalsIgnoreCase}), so folding changes only the canonical/display name, never whether a name
 * resolves. Every identifier-name extraction point routes through here so the behaviour is uniform.
 */
public final class SqlIdentifiers {

    private SqlIdentifiers() {
    }

    public static String canonical(final FrostlakeParser.IdentifierContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.QUOTED_IDENTIFIER() != null) {
            final String quoted = ctx.QUOTED_IDENTIFIER().getText();
            return quoted.substring(1, quoted.length() - 1);
        }
        if (ctx.POSITIONAL_PARAMETER() != null) {
            final String param = ctx.POSITIONAL_PARAMETER().getText();
            return "COLUMN" + Integer.parseInt(param.substring(1));
        }
        return ctx.getText().toUpperCase();
    }
}
