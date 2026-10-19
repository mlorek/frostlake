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
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

/**
 * The integration a STORAGE_INTEGRATION property names, on a COPY and on a stage alike. The name is written as an
 * identifier — a quoted text is refused where it stands — and has to reach an integration, which has to be a
 * storage integration: {@code Integration 'SI1' does not exist or not authorized.} and {@code Integration 'X' is
 * not a storage integration of type EXTERNAL_STAGE.} (live-verified).
 */
public final class StorageIntegrationReference {

    /** The property's name. */
    public static final String PROPERTY = "STORAGE_INTEGRATION";

    private StorageIntegrationReference() {
    }

    /**
     * Refuses a value that names no storage integration.
     *
     * @param catalog the catalog
     * @param value the property's value as written
     */
    public static void require(final Catalog catalog, final FrostlakeParser.CopyOptionValueContext value) {
        if (value == null) {
            return;
        }
        if (value.STRING_LITERAL() != null) {
            final Token at = value.STRING_LITERAL().getSymbol();
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "Invalid value '" + at.getText() + "' for property '" + PROPERTY + "'. String literal identifier is"
                    + " unsupported for this property. Please use an unquoted or double-quoted identifier."));
        }
        // Any other value is read as a name: a number names the integration "5".
        final String name = value.qualifiedName() != null
            ? ParseTreeText.getQualifiedName(value.qualifiedName()) : value.getText();
        final Integration integration = catalog.getIntegrations().find(name);
        if (integration == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", name));
        }
        if (integration.getKind() != IntegrationKind.STORAGE) {
            throw new RuntimeException(SqlCompilationError.of("Integration '"
                + SqlIdentifiers.spellCanonical(integration.getName())
                + "' is not a storage integration of type EXTERNAL_STAGE."));
        }
    }
}
