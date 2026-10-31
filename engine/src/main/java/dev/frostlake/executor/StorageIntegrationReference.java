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
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.PropertyValueKind;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;

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
     * Refuses a value that names no storage integration, or one the session may not use ({@link IntegrationUsage}).
     *
     * @param catalog the catalog
     * @param security the session's rights, or null when nothing is checked
     * @param value the property's value as written
     * @return the integration it names, or null when no value is written
     */
    public static Integration require(final Catalog catalog, final SecurityManager security,
                                      final FrostlakeParser.CopyOptionValueContext value) {
        if (value == null) {
            return null;
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
        IntegrationUsage.require(security, integration, name);
        if (integration.getKind() != IntegrationKind.STORAGE) {
            throw new RuntimeException(SqlCompilationError.of("Integration '"
                + SqlIdentifiers.spellCanonical(integration.getName())
                + "' is not a storage integration of type EXTERNAL_STAGE."));
        }
        return integration;
    }

    /**
     * Refuses a stage location the storage integration does not allow: one under none of its allowed locations
     * (live-verified), or under one of its blocked locations. An allowed location {@code *} allows every location.
     *
     * @param integration the storage integration, or null when the stage names none
     * @param url the stage's location, or null for an internal stage
     */
    public static void requireLocation(final Integration integration, final String url) {
        if (integration == null || url == null) {
            return;
        }
        if (!under(url, integration.property("STORAGE_ALLOWED_LOCATIONS"), true)
                || under(url, integration.property("STORAGE_BLOCKED_LOCATIONS"), false)) {
            throw new RuntimeException(SqlCompilationError.of("Location '" + url + "' is not allowed by integration "
                + SqlIdentifiers.spellCanonical(integration.getName())
                + ". Please use DESC INTEGRATION to check out allowed and blocked locations."));
        }
    }

    /** Whether the location lies under one of the listed locations; {@code *} lists every one where it may. */
    private static boolean under(final String url, final PropertyValue locations, final boolean wildcard) {
        if (locations == null || locations.getKind() != PropertyValueKind.LIST) {
            return false;
        }
        for (final PropertyValue location : locations.getItems()) {
            final String text = location.getText();
            if (text != null && (wildcard && "*".equals(text) || url.startsWith(text))) {
                return true;
            }
        }
        return false;
    }
}
