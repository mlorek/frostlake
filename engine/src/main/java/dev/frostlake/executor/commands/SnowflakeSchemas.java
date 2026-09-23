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

/**
 * The schemas of the shared SNOWFLAKE database that a class name, or the name of a built-in class's instance,
 * resolves in: what {@code SHOW SCHEMAS IN DATABASE SNOWFLAKE} lists, less LOCAL, MODELS, TAGS and TELEMETRY, which
 * it lists but no name reaches — {@code SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.TAGS.m} is
 * {@code Schema 'SNOWFLAKE.TAGS' does not exist or not authorized.} where ACCOUNT_USAGE gives the missing instance.
 * The database has no PUBLIC schema at all (live-verified).
 */
final class SnowflakeSchemas {

    private static final String[] RESOLVING = {
        "ACCOUNT_USAGE", "ALERT", "BCR_ROLLOUT", "BILLING", "CORE", "CORTEX", "DATA_PRIVACY", "DATA_SECURITY",
        "DATA_SHARING_USAGE", "DEFAULT_IMAGE_STORE", "IMAGES", "INFORMATION_SCHEMA", "MARKETPLACE_NOTIFICATION", "ML",
        "MONITORING", "NETWORK_SECURITY", "NOTIFICATION", "ORGANIZATION_USAGE", "ORG_USAGE_LOCAL", "POSTGRES",
        "READER_ACCOUNT_USAGE", "SNOWPARK", "SNOWPARK_CONNECT", "SPCS", "TRUST_CENTER", "TRUST_CENTER_INTERNAL",
        "WORKLOAD_INSIGHTS", "WORKLOAD_OPTIMIZATION"
    };

    private SnowflakeSchemas() {
    }

    /**
     * Whether a name resolves the schema of the SNOWFLAKE database it names.
     *
     * @param schema the schema's canonical name
     * @return whether it resolves
     */
    static boolean resolves(final String schema) {
        for (final String resolving : RESOLVING) {
            if (resolving.equals(schema)) {
                return true;
            }
        }
        return false;
    }
}
