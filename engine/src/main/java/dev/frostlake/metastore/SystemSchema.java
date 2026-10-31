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

/**
 * The schemas an account's SNOWFLAKE database holds beside INFORMATION_SCHEMA — what {@code SHOW SCHEMAS IN DATABASE
 * SNOWFLAKE} lists (live-verified). The engine models nothing in most of them: each is there so that a name passing
 * through it resolves as far as the account resolves it — a listing over it is empty where a missing schema would be
 * refused, and a missing object in it is named where the schema would be.
 *
 * <p>Four facts differ schema by schema, all measured: who owns it (most have no owner, as INFORMATION_SCHEMA has
 * none; some are the SNOWFLAKE application's), whether INFORMATION_SCHEMA.SCHEMATA lists it, whether a USE reaches
 * it, and whether DESCRIBE SCHEMA answers it as a schema that is not there.
 */
public enum SystemSchema {

    ACCESS_INTELLIGENCE(true, false, true),
    ACCESS_INTELLIGENCE_INTERNAL(false, false, true, true),
    ACCOUNT_USAGE(false, true, true),
    ALERT(false, true, true),
    BCR_ROLLOUT(false, true, true),
    BILLING(false, true, true),
    CORE(false, true, true),
    CORTEX(false, true, true),
    DATA_PRIVACY(false, true, true),
    DATA_SECURITY(true, false, true),
    DATA_SHARING_USAGE(false, true, true),
    DEFAULT_IMAGE_STORE(true, false, true),
    IMAGES(false, true, true),
    LOCAL(true, false, true),
    MARKETPLACE_NOTIFICATION(false, true, true),
    ML(false, true, true),
    MODELS(true, false, true),
    MONITORING(false, true, true),
    NATIVE_APPS_SPCS(true, false, true),
    NETWORK_SECURITY(true, false, true),
    NOTIFICATION(false, true, true),
    ORGANIZATION_USAGE(false, true, true),
    ORG_USAGE_LOCAL(false, false, false, true),
    POSTGRES(true, false, true),
    READER_ACCOUNT_USAGE(false, true, true),
    SNOWPARK(false, true, true),
    SNOWPARK_CONNECT(false, true, true),
    SPCS(false, false, true),
    TAGS(true, false, true),
    TELEMETRY(true, false, true),
    TRUST_CENTER(true, false, true),
    TRUST_CENTER_INTERNAL(false, false, false, true),
    WORKLOAD_INSIGHTS(false, true, true),
    WORKLOAD_OPTIMIZATION(false, true, true);

    /** The owner SHOW SCHEMAS names for a schema of the SNOWFLAKE application. */
    public static final String APPLICATION_OWNER = "SNOWFLAKE";

    /** The comment the TAGS schema carries. */
    private static final String TAGS_COMMENT = "Out-of-the-box governance tags schema. These tags are ready to use"
        + " for common tagging use cases. Customers can modify the tags as needed.";

    private final boolean applicationOwned;
    private final boolean inSchemata;
    private final boolean usable;
    private final boolean hidden;

    SystemSchema(final boolean applicationOwned, final boolean inSchemata, final boolean usable) {
        this(applicationOwned, inSchemata, usable, false);
    }

    SystemSchema(final boolean applicationOwned, final boolean inSchemata, final boolean usable,
                 final boolean hidden) {
        this.applicationOwned = applicationOwned;
        this.inSchemata = inSchemata;
        this.usable = usable;
        this.hidden = hidden;
    }

    /** @return the owner SHOW SCHEMAS names, or null for a schema nobody owns */
    public String owner() {
        return applicationOwned ? APPLICATION_OWNER : null;
    }

    /** @return the schema's comment, or null */
    public String comment() {
        return this == TAGS ? TAGS_COMMENT : null;
    }

    /** @return whether INFORMATION_SCHEMA.SCHEMATA lists the schema */
    public boolean isInSchemata() {
        return inSchemata;
    }

    /** @return whether USE SCHEMA reaches the schema */
    public boolean isUsable() {
        return usable;
    }

    /** @return whether DESCRIBE SCHEMA answers the schema as one that does not exist, though SHOW SCHEMAS lists it */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * The system schema of a name, or null.
     *
     * @param databaseName the stored name of the schema's database
     * @param schemaName   the schema's stored name
     * @return the schema of the SNOWFLAKE database so named, or null
     */
    public static SystemSchema of(final String databaseName, final String schemaName) {
        if (!SystemDatabase.is(databaseName) || schemaName == null) {
            return null;
        }
        for (final SystemSchema schema : values()) {
            if (schema.name().equals(schemaName)) {
                return schema;
            }
        }
        return null;
    }
}
