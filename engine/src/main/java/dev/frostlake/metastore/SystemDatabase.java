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

package dev.frostlake.metastore;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The SNOWFLAKE database, which every account has and nobody changes: it cannot be cloned, renamed, altered,
 * commented on, dropped or replaced, and its schemas are its own — the ones an account's holds
 * ({@link SystemSchema}); none is created in it, dropped from it, altered, or moved into or out of it.
 *
 * <p>Its PUBLIC schema is another matter. A real SNOWFLAKE database has none (live-verified, see
 * {@code SnowflakeSchemas}); here it is the engine's default working schema ({@code database.default} and
 * {@code schema.default}), where a session that names no database creates its tables and writes its rows. What is
 * created in SNOWFLAKE.PUBLIC therefore stays writable, which is also why the DML read-only check passes this
 * database by. Snowflake's own sentences for these refusals are not known; the engine's read-only sentence stands in.
 */
public final class SystemDatabase {

    /** The database's name. */
    public static final String NAME = "SNOWFLAKE";

    /** Where SHOW DATABASES says the database comes from: the share of its ACCOUNT_USAGE (live-verified). */
    public static final String ORIGIN = "SNOWFLAKE.ACCOUNT_USAGE";

    /**
     * The database roles the database holds, as SHOW DATABASE ROLES IN DATABASE SNOWFLAKE lists them: the share's
     * own, inherited, owned by no role and commented on by none (live-verified).
     */
    private static final List<String> ROLES = Collections.unmodifiableList(Arrays.asList("AI_FUNCTIONS_USER",
        "ALERT_VIEWER", "ANACONDA_REPOSITORY_USER", "BUDGET_CREATOR", "CLASSIFICATION_ADMIN", "CLASSIFICATION_VIEWER",
        "COPILOT_USER", "CORE_VIEWER", "CORTEX_AGENT_USER", "CORTEX_ANALYST_USER", "CORTEX_EMBED_USER",
        "CORTEX_REST_API_USER", "CORTEX_SENSE_BUILDER", "CORTEX_SENSE_USER", "CORTEX_USER", "DATA_METRIC_USER",
        "DATA_PRIVACY_VIEWER", "DOCUMENT_INTELLIGENCE_CREATOR", "GOVERNANCE_ADMIN", "GOVERNANCE_VIEWER", "ML_USER",
        "MONITORING_VIEWER", "NOTIFICATION_VIEWER", "OBJECT_VIEWER", "ORGANIZATION_ACCOUNTS_VIEWER",
        "ORGANIZATION_BILLING_VIEWER", "ORGANIZATION_GOVERNANCE_VIEWER", "ORGANIZATION_USAGE_VIEWER",
        "ORG_BUDGET_CREATOR", "PYPI_REPOSITORY_USER", "QUOTA_CREATOR", "READER_USAGE_VIEWER", "SECURITY_VIEWER",
        "SHARING_USAGE_VIEWER", "SNOWFLAKE_MARKETPLACE_INVOICE_VIEWER", "SNOWPARK_CONNECT_VIEWER", "SPARK_USER",
        "SPCS_REGISTRY_VIEWER", "SPCS_VIEWER", "USAGE_ADMIN", "USAGE_VIEWER", "WORKLOAD_INSIGHTS_USER",
        "WORKLOAD_OPTIMIZATION_USER"));

    private SystemDatabase() {
    }

    /**
     * Whether a database name is the SNOWFLAKE database's, as the catalog stores it.
     *
     * @param databaseName the stored (canonical) name, or null
     * @return whether it is
     */
    public static boolean is(final String databaseName) {
        return NAME.equals(databaseName);
    }

    /**
     * Refuses a statement that would change the SNOWFLAKE database itself or the set of its schemas.
     *
     * @param databaseName the stored name of the database the statement changes
     */
    public static void refuseChange(final String databaseName) {
        if (is(databaseName)) {
            throw new RuntimeException(readOnly());
        }
    }

    /** @return the names of the database's own database roles */
    public static List<String> roles() {
        return ROLES;
    }

    /**
     * The engine's read-only sentence for the SNOWFLAKE database.
     *
     * @return the sentence
     */
    public static String readOnly() {
        return "Database '" + NAME + "' is read-only";
    }
}
