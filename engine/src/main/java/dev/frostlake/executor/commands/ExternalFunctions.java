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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.parser.FrostlakeParser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The integration an external function and a Git repository name, judged as the account judges it (live-verified):
 *
 * <pre>
 *   external function, no API_INTEGRATION    Property 'API_INTEGRATION' must be specified
 *   external function, no such integration   Integration 'X' does not exist or not authorized. + the privilege hint
 *   external function, another kind          Integration 'X' is not an API integration.
 *   external function, a Git provider        Invalid API Provider for 'External Function' object.
 *   Git repository, ORIGIN or API_INTEGRATION missing    Missing option(s): [ORIGIN]
 *   Git repository, no such API integration  Integration 'X' does not exist or not authorized.
 * </pre>
 *
 * <p>A call of an external function is refused: nothing outside the account is reached from this engine.
 */
public final class ExternalFunctions {

    /** The API providers an external function goes through: the clouds' API gateways and management services. */
    private static final Set<String> GATEWAYS = new HashSet<String>(Arrays.asList("AWS_API_GATEWAY",
        "AWS_PRIVATE_API_GATEWAY", "AWS_GOV_API_GATEWAY", "AWS_GOV_PRIVATE_API_GATEWAY", "AZURE_API_MANAGEMENT",
        "GOOGLE_API_GATEWAY"));

    private ExternalFunctions() {
    }

    /**
     * The refusal a call of an external function meets.
     *
     * @param function the external function
     * @return the refusal's text
     */
    public static String cannotCall(final Function function) {
        return "External function " + function.getName() + " cannot be called: the remote service at "
            + function.getExternalUrl() + " is not reached from this engine.";
    }

    /**
     * The API integration an external function's API_INTEGRATION names, which has to be one whose provider is a
     * cloud's API gateway.
     *
     * @param catalog the catalog
     * @param written the option's value, or null when the statement names none
     * @return the integration's name
     */
    static String requireFunctionIntegration(final Catalog catalog,
                                             final FrostlakeParser.QualifiedNameContext written) {
        if (written == null) {
            throw new RuntimeException("Property 'API_INTEGRATION' must be specified");
        }
        final String name = QualifiedName.join(ParseTreeText.qualifiedNameParts(written));
        final Integration integration = catalog.getIntegrations().find(name);
        if (integration == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", name));
        }
        if (integration.getKind() != IntegrationKind.API) {
            throw new RuntimeException(SqlCompilationError.of("Integration '" + integration.getName()
                + "' is not an API integration."));
        }
        final String provider = integration.text("API_PROVIDER");
        if (provider == null || !GATEWAYS.contains(provider.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Invalid API Provider for 'External Function' object.");
        }
        return integration.getName();
    }

    /**
     * The API integration a Git repository's API_INTEGRATION names: one that is no API integration names nothing,
     * and one of another provider is refused as an artifact repository's is.
     *
     * @param catalog the catalog
     * @param written the option's value
     * @return the integration's name
     */
    static String requireGitIntegration(final Catalog catalog, final FrostlakeParser.QualifiedNameContext written) {
        final String name = QualifiedName.join(ParseTreeText.qualifiedNameParts(written));
        final Integration integration = catalog.getIntegrations().find(name);
        if (integration == null || integration.getKind() != IntegrationKind.API) {
            throw new RuntimeException(SqlCompilationError.of("Integration '" + name
                + "' does not exist or not authorized."));
        }
        if (!"GIT_HTTPS_API".equalsIgnoreCase(integration.text("API_PROVIDER"))) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Invalid API Provider for 'Git Repository' object.");
        }
        return integration.getName();
    }

    /**
     * The syntax error an option name neither statement takes meets, at the name.
     *
     * @param name the option's name
     * @return the refusal
     */
    static RuntimeException unknownOption(final Token name) {
        return new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(name, name.getText())));
    }
}
