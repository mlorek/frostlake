/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The integrations other statements name. A pipe, a task, a secret and an artifact repository each name an
 * integration that has to exist and to be of the kind the clause takes; each clause refuses a miss and a wrong
 * kind in words of its own (live-verified).
 *
 * <p>A name written as an identifier folds as one does, and a name written as a string is the integration's
 * exact name.
 */
final class IntegrationReferences {

    /** The cloud an internal stage lives in: the account's own. */
    private static final String ACCOUNT_CLOUD = "AWS";

    private final Catalog catalog;

    IntegrationReferences(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * A pipe's ERROR_INTEGRATION names a queue that Snowflake writes to.
     *
     * @param name the integration's canonical name
     */
    void requirePipeError(final String name) {
        final Integration integration = existing(name);
        requireQueue(integration);
        if (!"OUTBOUND".equals(direction(integration))) {
            throw new RuntimeException(SqlCompilationError.of("Invalid direction type of error_integration "
                + integration.getName() + ", expected OUTBOUND, got " + direction(integration) + "."));
        }
    }

    /**
     * A pipe's INTEGRATION names a queue that Snowflake reads from, in the cloud of the pipe's stage, and belongs
     * to a pipe that ingests automatically.
     *
     * @param name the integration's exact name
     * @param autoIngest what the pipe writes for AUTO_INGEST, or null when it writes none
     * @param stage the stage the pipe loads from, or null when it is a table's own
     */
    void requirePipeNotification(final String name, final Boolean autoIngest, final Stage stage) {
        final Integration integration = existing(name);
        requireQueue(integration);
        if (!"INBOUND".equals(direction(integration))) {
            throw new RuntimeException(SqlCompilationError.of("Invalid direction type of integration "
                + integration.getName() + ", expected INBOUND, got " + direction(integration) + "."));
        }
        if (!Boolean.TRUE.equals(autoIngest)) {
            // The account's sentence breaks after a space here.
            throw new RuntimeException(SqlCompilationError.PREFIX + " \nPROPERTY AUTO_INGEST must be "
                + (autoIngest == null ? "supplied" : "set to true") + " if INTEGRATION is used to create a pipe.");
        }
        final String integrationCloud = cloudOf(integration);
        final String stageCloud = cloudOf(stage);
        if (!integrationCloud.equals(stageCloud)) {
            throw new RuntimeException("Pipe Notifications bind failure \"Integration and stage should be both from"
                + " the same cloud provider. Integration is in " + integrationCloud + " cloud and stage is in "
                + stageCloud + " cloud.\"");
        }
    }

    /**
     * A pipe that ingests automatically from an Azure stage needs an integration.
     *
     * @param stage the stage the pipe loads from, or null when it is a table's own
     */
    void requireNotificationFor(final Stage stage) {
        if ("AZURE".equals(cloudOf(stage))) {
            throw new RuntimeException("Pipe Notifications bind failure \"Integration cannot be null for an"
                + " auto-ingest pipe that uses a stage in AZURE.\"");
        }
    }

    /**
     * A task's ERROR_INTEGRATION or SUCCESS_INTEGRATION names a queue, of either direction.
     *
     * @param integration the integration the task names
     */
    static void requireTaskNotification(final Integration integration) {
        if (!isQueue(integration)) {
            throw new RuntimeException(SqlCompilationError.of("Integration '" + integration.getName()
                + "' is not a valid notification integration for UserTasks."));
        }
    }

    /**
     * A secret's API_AUTHENTICATION names a security integration that authenticates the way the secret's type
     * needs, and an OAuth secret asks for scopes the integration allows.
     *
     * @param secretType the secret's TYPE
     * @param name the integration's canonical name
     * @param scopes the secret's OAUTH_SCOPES, empty when it names none
     */
    /**
     * The integration a secret's API_AUTHENTICATION names, judged before anything about the secret's type: it has
     * to exist and to be a security integration (live-verified).
     *
     * @param name the integration's canonical name
     * @return the integration
     */
    Integration requireAuthenticationIntegration(final String name) {
        final Integration integration = catalog.getIntegrations().find(name);
        if (integration == null) {
            throw new RuntimeException(SqlCompilationError.of("Integration '" + name
                + "' does not exist or not authorized."));
        }
        if (integration.getKind() != IntegrationKind.SECURITY) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Integration '" + integration.getName()
                + "' is not of type API Authentication.");
        }
        return integration;
    }

    void requireSecretAuthentication(final String secretType, final String name, final List<String> scopes) {
        final Integration integration = requireAuthenticationIntegration(name);
        requireEnabled(integration);
        final String needed = "CLOUD_PROVIDER_TOKEN".equals(secretType) ? "AWS_IAM" : "OAUTH2";
        if (!needed.equals(integration.text("AUTH_TYPE"))) {
            throw new RuntimeException(SqlCompilationError.of("invalid value [" + integration.getName()
                + "] for parameter 'API_AUTHENTICATION'"));
        }
        requireAllowedScopes(integration, scopes);
    }

    /**
     * An OAuth secret's new OAUTH_SCOPES, set by an ALTER, are scopes the integration it authenticates through
     * allows, as a CREATE's are.
     *
     * @param name the integration's canonical name, or null when the secret names none
     * @param scopes the scopes set
     */
    void requireSecretScopes(final String name, final List<String> scopes) {
        final Integration integration = name == null ? null : catalog.getIntegrations().find(name);
        if (integration != null) {
            requireAllowedScopes(integration, scopes);
        }
    }

    private static void requireAllowedScopes(final Integration integration, final List<String> scopes) {
        if (!allowedScopes(integration).containsAll(scopes)) {
            throw new RuntimeException(SqlCompilationError.of("invalid value '" + scopes
                + "' for property 'OAUTH_SCOPES'"));
        }
    }

    /**
     * An artifact repository's API_INTEGRATION names an API integration of the package index's provider.
     *
     * @param name the integration's canonical name
     */
    void requireArtifactIntegration(final String name) {
        final Integration integration = catalog.getIntegrations().find(name);
        if (integration == null) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + name
                + "' does not exist or not authorized."));
        }
        if (integration.getKind() != IntegrationKind.API || !"PYPI".equals(integration.text("API_PROVIDER"))) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Invalid API Provider for 'Artifact Repository' object.");
        }
        requireEnabled(integration);
    }

    private static void requireEnabled(final Integration integration) {
        if (!integration.isEnabled()) {
            throw new RuntimeException(SqlCompilationError.of("Integration '" + integration.getName()
                + "' is not enabled."));
        }
    }

    /**
     * The secrets that authenticate through an integration, by their full names in order.
     *
     * @param integration the integration
     * @return the secrets' full names, empty when no secret names it
     */
    List<String> secretsNaming(final Integration integration) {
        final List<String> names = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final SecurityObject secret : schema.getSecurityObjects().list(SecurityObjectKind.SECRET)) {
                    if (integration.getName().equals(secret.text("API_AUTHENTICATION"))) {
                        names.add(schema.qualifiedName(secret.getName()));
                    }
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /** The integration of that name; a miss is refused as a securable object's is. */
    private Integration existing(final String name) {
        final Integration integration = catalog.getIntegrations().find(name);
        if (integration == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Integration", name));
        }
        return integration;
    }

    private static void requireQueue(final Integration integration) {
        if (!isQueue(integration)) {
            throw new RuntimeException(SqlCompilationError.of("Integration '" + integration.getName()
                + "' is not a notification integration."));
        }
    }

    private static boolean isQueue(final Integration integration) {
        return integration.getKind() == IntegrationKind.NOTIFICATION && "QUEUE".equals(integration.text("TYPE"));
    }

    /** A queue's direction: as written, else INBOUND. */
    private static String direction(final Integration integration) {
        final String written = integration.text("DIRECTION");
        return written != null ? written : "INBOUND";
    }

    private static String cloudOf(final Integration integration) {
        final String provider = String.valueOf(integration.text("NOTIFICATION_PROVIDER"));
        if (provider.startsWith("AZURE_")) {
            return "AZURE";
        }
        return provider.startsWith("GCP_") ? "GCP" : "AWS";
    }

    /** The cloud a stage's files are in: an external stage's by its URL, an internal one's the account's. */
    private static String cloudOf(final Stage stage) {
        if (stage == null || stage.getType() != StageType.EXTERNAL || stage.getUrl() == null) {
            return ACCOUNT_CLOUD;
        }
        final String cloud = stage.locationCloud();
        return "AZURE".equals(cloud) || "GCP".equals(cloud) ? cloud : ACCOUNT_CLOUD;
    }

    private static List<String> allowedScopes(final Integration integration) {
        final List<String> scopes = new ArrayList<>();
        final PropertyValue allowed = integration.property("OAUTH_ALLOWED_SCOPES");
        if (allowed != null) {
            for (final PropertyValue scope : allowed.getItems()) {
                scopes.add(scope.getText());
            }
        }
        return scopes;
    }
}
