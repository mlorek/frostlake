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

package dev.frostlake.system;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import java.util.Arrays;

/**
 * The INFORMATION_SCHEMA views for objects this engine does not model — semantic views, native
 * apps, Cortex services, backups and snapshots, replication, sharing, container services, and the
 * rest. Each is exposed with its FULL live column shape (measured column for column on a real
 * account) and no rows, because that is exactly what a real account returns for a deployment that
 * uses none of those features.
 *
 * <p>Presence matters even when a view is always empty: a tool that introspects the catalog —
 * a migration framework listing constraints, a client enumerating file formats — issues the query
 * unconditionally, and answering "table does not exist" where a real account answers zero rows is
 * the same class of fidelity bug as returning wrong rows. INDEXES / INDEX_COLUMNS appear here for
 * that reason too: reporting no indexes is accurate for an engine that deliberately has none.
 *
 * <p>A view listed here graduates out of this class the moment the engine models its objects —
 * see {@link SystemViews} for the populated ones.
 */
public class UnmodeledSystemViews {

    /**
     * The named INFORMATION_SCHEMA view with its live column shape and no rows, or null when this
     * class does not define it (the caller then reports the object as non-existent, as live does
     * for a name that is not an INFORMATION_SCHEMA view at all).
     */
    public ResultSet query(final String viewName) {
        switch (viewName) {
            case "APPLICATION_CONFIGURATIONS":
                return empty(
                    col("NAME"), col("APPLICATION_NAME"), colTs("CREATED_ON"), colTs("UPDATED_ON"), col("TYPE"),
                    col("STATUS"), colBool("SENSITIVE"), col("VALUE"), colTs("VALUE_UPDATED_ON"), col("LABEL"),
                    col("DESCRIPTION"), col("APPLICATION_ROLES"), col("ADDITIONAL_PROPERTIES"));
            case "APPLICATION_SERVICES":
                return empty(
                    col("APPLICATION_SERVICE_CATALOG"), col("APPLICATION_SERVICE_SCHEMA"),
                    col("APPLICATION_SERVICE_NAME"), col("STATUS"), col("URL"), col("PRIVATELINK_URL"),
                    col("SOURCE"), col("APPLICATION_SERVICE_OWNER"), col("APPLICATION_SERVICE_OWNER_ROLE_TYPE"),
                    col("CREATED_BY"), col("COMMENT"), col("ADDITIONAL_PROPERTIES"), colTs("CREATED"),
                    colTs("LAST_ALTERED"), colTs("LAST_RESUMED"), colTs("LAST_SUSPENDED"), colBool("IS_UPGRADING"),
                    colBool("AUTO_RESUME"), colLong("AUTO_SUSPEND_SECS"), col("QUERY_WAREHOUSE"),
                    col("COMPUTE_RESOURCE"), colLong("MIN_INSTANCES"), colLong("MAX_INSTANCES"),
                    colLong("CURRENT_INSTANCES"), col("EXTERNAL_ACCESS_INTEGRATIONS"), col("URL_PREFIX"));
            case "APPLICATION_SPECIFICATIONS":
                return empty(
                    col("NAME"), col("APPLICATION_NAME"), col("TYPE"), colLong("SEQUENCE_NUMBER"),
                    colTs("REQUESTED_ON"), col("STATUS"), colTs("STATUS_UPDATED_ON"), col("LABEL"),
                    col("DESCRIPTION"), col("DEFINITION"));
            case "BACKUPS":
                return empty(
                    col("ID"), colTs("CREATED"), col("BACKUP_SET_NAME"), col("BACKUP_SET_SCHEMA"),
                    col("BACKUP_SET_CATALOG"), colTs("EXPIRATION_SCHEDULED_FOR", 9), colBool("IS_UNDER_LEGAL_HOLD"),
                    col("COMMENT"));
            case "BACKUP_POLICIES":
                return empty(
                    col("BACKUP_POLICY_NAME"), col("BACKUP_POLICY_SCHEMA"), col("BACKUP_POLICY_CATALOG"),
                    col("SCHEDULE"), colLong("EXPIRE_AFTER_DAYS"), colText("HAS_RETENTION_LOCK", 3), col("OWNER"),
                    col("OWNER_ROLE_TYPE"), colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "BACKUP_SETS":
                return empty(
                    col("BACKUP_SET_NAME"), col("BACKUP_SET_SCHEMA"), col("BACKUP_SET_CATALOG"),
                    colText("OBJECT_KIND", 16), col("OBJECT_NAME"), col("OBJECT_SCHEMA"), col("OBJECT_CATALOG"),
                    col("BACKUP_POLICY_NAME"), col("BACKUP_POLICY_SCHEMA"), col("BACKUP_POLICY_CATALOG"),
                    col("OWNER"), col("OWNER_ROLE_TYPE"), colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "CHECK_CONSTRAINTS":
                return empty(
                    col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_TABLE"),
                    col("CONSTRAINT_NAME"), col("CHECK_CLAUSE"));
            case "CLASSES":
                return empty(
                    col("NAME"), col("SCHEMA_NAME"), col("DATABASE_NAME"), colVariant("VERSION"), col("OWNER"),
                    colText("OWNER_ROLE_TYPE", 4), colText("IS_SERVICE_CLASS", 5), colTs("CREATED"),
                    col("COMMENT"));
            case "CLASS_INSTANCES":
                return empty(
                    col("NAME"), col("SCHEMA_NAME"), col("DATABASE_NAME"), col("CLASS_NAME"),
                    col("CLASS_SCHEMA_NAME"), col("CLASS_DATABASE_NAME"), col("VERSION"), col("OWNER"),
                    colText("OWNER_ROLE_TYPE", 4), colTs("CREATED"), col("COMMENT"));
            case "CLASS_INSTANCE_FUNCTIONS":
                return empty(
                    col("FUNCTION_NAME"), col("FUNCTION_INSTANCE_NAME"), col("FUNCTION_INSTANCE_SCHEMA"),
                    col("FUNCTION_INSTANCE_DATABASE"), col("FUNCTION_OWNER"), col("ARGUMENT_SIGNATURE"),
                    col("DATA_TYPE"), colLong("CHARACTER_MAXIMUM_LENGTH"), colLong("CHARACTER_OCTET_LENGTH"),
                    colLong("NUMERIC_PRECISION"), colNumber("NUMERIC_PRECISION_RADIX", 2, 0),
                    colLong("NUMERIC_SCALE"), col("FUNCTION_LANGUAGE"), col("FUNCTION_DEFINITION"),
                    col("VOLATILITY"), colText("IS_NULL_CALL", 3), colText("IS_SECURE", 3), colTs("CREATED"),
                    colTs("LAST_ALTERED"), col("COMMENT"), colText("IS_EXTERNAL", 3), col("API_INTEGRATION"),
                    col("CONTEXT_HEADERS"), colLong("MAX_BATCH_ROWS"), col("COMPRESSION"), col("PACKAGES"),
                    col("RUNTIME_VERSION"), col("INSTALLED_PACKAGES"), colText("IS_MEMOIZABLE", 3));
            case "CLASS_INSTANCE_PROCEDURES":
                return empty(
                    col("PROCEDURE_NAME"), col("PROCEDURE_INSTANCE_NAME"), col("PROCEDURE_INSTANCE_SCHEMA"),
                    col("PROCEDURE_INSTANCE_DATABASE"), col("PROCEDURE_OWNER"), col("ARGUMENT_SIGNATURE"),
                    col("DATA_TYPE"), colLong("CHARACTER_MAXIMUM_LENGTH"), colLong("CHARACTER_OCTET_LENGTH"),
                    colLong("NUMERIC_PRECISION"), colNumber("NUMERIC_PRECISION_RADIX", 2, 0),
                    colLong("NUMERIC_SCALE"), col("PROCEDURE_LANGUAGE"), col("PROCEDURE_DEFINITION"),
                    colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "CORTEX_SEARCH_SERVICES":
                return empty(
                    col("SERVICE_CATALOG"), col("SERVICE_SCHEMA"), col("SERVICE_NAME"), colTs("CREATED"),
                    col("DEFINITION"), col("SEARCH_COLUMN"), col("ATTRIBUTE_COLUMNS"), col("COLUMNS"),
                    col("TARGET_LAG"), col("WAREHOUSE"), col("COMMENT"), col("SERVICE_QUERY_URL"), col("OWNER"),
                    col("OWNER_ROLE_TYPE"), colTs("DATA_TIMESTAMP"), colLong("SOURCE_DATA_BYTES"),
                    colLong("SOURCE_DATA_NUM_ROWS"), col("INDEXING_STATE"), col("INDEXING_ERROR"),
                    col("SERVING_STATE"), colLong("SERVING_DATA_BYTES"), col("EMBEDDING_MODEL"),
                    col("PRIMARY_KEY_COLUMNS"), colLong("SCORING_PROFILE_COUNT"), col("AUTO_SUSPEND"),
                    col("REFRESH_MODE"), col("VECTOR_INDEXES"), colBool("REQUEST_LOGGING"),
                    colBool("INHERIT_DATA_GOVERNANCE_POLICIES"));
            case "CORTEX_SEARCH_SERVICE_SCORING_PROFILES":
                return empty(
                    col("SERVICE_CATALOG"), col("SERVICE_SCHEMA"), col("SERVICE_NAME"), col("SCORING_PROFILE_NAME"),
                    col("SCORING_PROFILE"));
            case "CURRENT_PACKAGES_POLICY":
                return empty(
                    col("NAME"), col("LANGUAGE"), col("ALLOWLIST"), col("BLOCKLIST"),
                    col("ADDITIONAL_CREATION_BLOCKLIST"), col("COMMENT"));
            case "ELEMENT_TYPES":
                return empty(
                    col("OBJECT_CATALOG"), col("OBJECT_SCHEMA"), col("OBJECT_NAME"), colText("OBJECT_TYPE", 5),
                    col("COLLECTION_TYPE_IDENTIFIER"), col("DATA_TYPE"), colLong("CHARACTER_MAXIMUM_LENGTH"),
                    colLong("CHARACTER_OCTET_LENGTH"), colLong("NUMERIC_PRECISION"),
                    colNumber("NUMERIC_PRECISION_RADIX", 2, 0), colLong("NUMERIC_SCALE"),
                    colLong("DATETIME_PRECISION"), colNull("INTERVAL_TYPE"), colLong("INTERVAL_PRECISION"),
                    colNull("CHARACTER_SET_CATALOG"), colNull("CHARACTER_SET_SCHEMA"),
                    colNull("CHARACTER_SET_NAME"), colNull("COLLATION_CATALOG"), colNull("COLLATION_SCHEMA"),
                    col("COLLATION_NAME"), colNull("UDT_CATALOG"), colNull("UDT_SCHEMA"), colNull("UDT_NAME"),
                    colNull("SCOPE_CATALOG"), colNull("SCOPE_SCHEMA"), colNull("SCOPE_NAME"),
                    colNull("MAXIMUM_CARDINALITY"), col("DTD_IDENTIFIER"));
            case "EVENT_TABLES":
                return empty(
                    col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"), col("TABLE_OWNER"),
                    colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "EXTERNAL_TABLES":
                return empty(
                    col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"), col("TABLE_OWNER"),
                    colTs("CREATED"), colTs("LAST_ALTERED"), colTs("LAST_DDL"), col("LAST_DDL_BY"), col("COMMENT"),
                    col("LOCATION"), col("FILE_FORMAT_NAME"), col("FILE_FORMAT_TYPE"));
            case "FIELDS":
                return empty(
                    col("OBJECT_CATALOG"), col("OBJECT_SCHEMA"), col("OBJECT_NAME"), colText("OBJECT_TYPE", 5),
                    col("ROW_IDENTIFIER"), col("FIELD_NAME"), colLong("ORDINAL_POSITION"), col("DATA_TYPE"),
                    colLong("CHARACTER_MAXIMUM_LENGTH"), colLong("CHARACTER_OCTET_LENGTH"),
                    colLong("NUMERIC_PRECISION"), colNumber("NUMERIC_PRECISION_RADIX", 2, 0),
                    colLong("NUMERIC_SCALE"), colLong("DATETIME_PRECISION"), colNull("INTERVAL_TYPE"),
                    colLong("INTERVAL_PRECISION"), colNull("CHARACTER_SET_CATALOG"),
                    colNull("CHARACTER_SET_SCHEMA"), colNull("CHARACTER_SET_NAME"), colNull("COLLATION_CATALOG"),
                    colNull("COLLATION_SCHEMA"), col("COLLATION_NAME"), colNull("UDT_CATALOG"),
                    colNull("UDT_SCHEMA"), colNull("UDT_NAME"), colNull("SCOPE_CATALOG"), colNull("SCOPE_SCHEMA"),
                    colNull("SCOPE_NAME"), colNull("MAXIMUM_CARDINALITY"), col("DTD_IDENTIFIER"));
            case "GIT_REPOSITORIES":
                return empty(
                    col("GIT_REPOSITORY_CATALOG"), col("GIT_REPOSITORY_SCHEMA"), col("GIT_REPOSITORY_NAME"),
                    col("GIT_REPOSITORY_OWNER"), col("ORIGIN"), col("API_INTEGRATION"), col("GIT_CREDENTIALS"),
                    col("COMMENT"), colTs("CREATED"), colTs("LAST_ALTERED"));
            case "INDEXES":
                return empty(
                    col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"), col("NAME"), col("OWNER"),
                    colText("IS_UNIQUE", 3), col("CONSTRAINT_NAME"), colText("STATUS", 6), colTs("CREATED"));
            case "INDEX_COLUMNS":
                return empty(
                    col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"), col("INDEX_NAME"), col("NAME"),
                    col("KEY_SEQUENCE"), col("INDEX_OWNER"), colText("IS_UNIQUE", 3), col("CONSTRAINT_NAME"),
                    colText("STATUS", 6), colTs("CREATED"));
            case "LISTINGS":
                return empty(
                    col("GLOBAL_NAME"), col("NAME"), col("OWNER"), colTs("CREATED_ON", 9), colTs("UPDATED_ON", 9),
                    colTs("PUBLISHED_ON", 9), col("TITLE"), col("SUBTITLE"), col("DESCRIPTION"),
                    col("LISTING_TERMS"), col("STATE"), col("SHARE"), col("APPLICATION_PACKAGE"),
                    col("DATA_ATTRIBUTES"), col("CATEGORIES"), col("PROFILE"), col("CUSTOMIZED_CONTACT_INFO"),
                    col("COMMENT"), col("TARGETS"), col("AUTO_FULFILLMENT"), colBool("IS_SHARE"),
                    colBool("IS_APPLICATION"), col("DISTRIBUTION"), colBool("IS_MOUNTLESS_QUERYABLE"),
                    col("ORGANIZATION_PROFILE_NAME"), col("UNIFORM_LISTING_LOCATOR"), col("APPROVER_CONTACT"),
                    col("SUPPORT_CONTACT"), col("RESHARING"), colBool("SHARE_RESTRICTIONS"),
                    col("CUSTOM_ATTRIBUTES"));
            case "LOAD_HISTORY":
                return empty(
                    col("SCHEMA_NAME"), col("FILE_NAME"), col("TABLE_NAME"), colTs("LAST_LOAD_TIME"), col("STATUS"),
                    colLong("ROW_COUNT"), colLong("ROW_PARSED"), col("FIRST_ERROR_MESSAGE"),
                    colLong("FIRST_ERROR_LINE_NUMBER"), colLong("FIRST_ERROR_CHARACTER_POSITION"),
                    col("FIRST_ERROR_COL_NAME"), colLong("ERROR_COUNT"), colLong("ERROR_LIMIT"));
            case "MODEL_VERSIONS":
                return empty(
                    col("CATALOG_NAME"), colLong("CATALOG_ID"), col("SCHEMA_NAME"), colLong("SCHEMA_ID"),
                    col("MODEL_NAME"), col("MODEL_VERSION_NAME"), col("VERSION_ALIASES"), col("COMMENT"),
                    col("MODEL_COMMENT"), col("OWNER"), colTs("CREATED_ON"), colTs("LAST_ALTERED_ON"),
                    col("FUNCTIONS"), colNull("MODEL_TYPE"), colNull("PYTHON_VERSION"), colNull("LANGUAGE"),
                    colNull("DEPENDENCIES"), colObject("METADATA"), colObject("USERDATA"));
            case "NOTEBOOKS":
                return empty(
                    col("NOTEBOOK_CATALOG"), col("NOTEBOOK_SCHEMA"), col("NOTEBOOK_NAME"), col("NOTEBOOK_OWNER"),
                    col("NOTEBOOK_MAIN_FILE"), col("NOTEBOOK_QUERY_WAREHOUSE"), col("NOTEBOOK_URL_ID"),
                    colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "PACKAGES":
                return empty(
                    col("PACKAGE_NAME"), col("VERSION"), col("LANGUAGE"), col("RUNTIME_VERSION"));
            case "REPLICATION_DATABASES":
                return empty(
                    col("REGION_GROUP"), col("SNOWFLAKE_REGION"), col("ACCOUNT_NAME"), col("DATABASE_NAME"),
                    col("COMMENT"), colTs("CREATED", 9), colBool("IS_PRIMARY"), col("PRIMARY"),
                    col("REPLICATION_ALLOWED_TO_ACCOUNTS"), col("FAILOVER_ALLOWED_TO_ACCOUNTS"));
            case "REPLICATION_GROUPS":
                return empty(
                    col("REGION_GROUP"), col("SNOWFLAKE_REGION"), colTs("CREATED_ON", 9), col("ACCOUNT_NAME"),
                    col("NAME"), col("TYPE"), col("COMMENT"), colBool("IS_PRIMARY"), col("PRIMARY"),
                    col("OBJECT_TYPES"), col("ALLOWED_INTEGRATION_TYPES"), col("ALLOWED_ACCOUNTS"),
                    col("ORGANIZATION_NAME"), col("ACCOUNT_LOCATOR"), col("REPLICATION_SCHEDULE"),
                    col("SECONDARY_STATE"), colTs("NEXT_SCHEDULED_REFRESH", 9), col("OWNER"),
                    colBool("IS_LISTING_AUTO_FULFILLMENT_GROUP"), col("ERROR_INTEGRATION"));
            case "SEMANTIC_DIMENSIONS":
                return empty(
                    col("SEMANTIC_VIEW_CATALOG"), col("SEMANTIC_VIEW_SCHEMA"), col("SEMANTIC_VIEW_NAME"),
                    col("TABLE_NAME"), col("NAME"), col("DATA_TYPE"), col("EXPRESSION"), colArray("SYNONYMS"),
                    col("COMMENT"), col("CORTEX_SEARCH_SERVICE_DATABASE_NAME"),
                    col("CORTEX_SEARCH_SERVICE_SCHEMA_NAME"), col("CORTEX_SEARCH_SERVICE_NAME"),
                    col("CORTEX_SEARCH_SERVICE_COLUMN_NAME"), colArray("LABELS"), colArray("LOD_DIMENSIONS"),
                    col("LOD_DIMENSION_TYPE"), colText("ACCESS_MODIFIER", 7));
            case "SEMANTIC_FACTS":
                return empty(
                    col("SEMANTIC_VIEW_CATALOG"), col("SEMANTIC_VIEW_SCHEMA"), col("SEMANTIC_VIEW_NAME"),
                    col("TABLE_NAME"), col("NAME"), col("DATA_TYPE"), col("EXPRESSION"), colArray("SYNONYMS"),
                    col("COMMENT"), colArray("LABELS"), colText("ACCESS_MODIFIER", 7), colArray("LOD_DIMENSIONS"),
                    col("LOD_DIMENSION_TYPE"));
            case "SEMANTIC_METRICS":
                return empty(
                    col("SEMANTIC_VIEW_CATALOG"), col("SEMANTIC_VIEW_SCHEMA"), col("SEMANTIC_VIEW_NAME"),
                    col("TABLE_NAME"), col("NAME"), col("DATA_TYPE"), col("EXPRESSION"), colArray("SYNONYMS"),
                    col("COMMENT"), colArray("ADDITIVE_DIMENSIONS"), colArray("NON_ADDITIVE_DIMENSIONS"),
                    colArray("USING_RELATIONSHIPS"), colText("ACCESS_MODIFIER", 7));
            case "SEMANTIC_RELATIONSHIPS":
                return empty(
                    col("SEMANTIC_VIEW_CATALOG"), col("SEMANTIC_VIEW_SCHEMA"), col("SEMANTIC_VIEW_NAME"),
                    col("NAME"), col("TABLE_NAME"), colArray("FOREIGN_KEYS"), col("REF_TABLE_NAME"),
                    colArray("REF_KEYS"));
            case "SEMANTIC_TABLES":
                return empty(
                    col("SEMANTIC_VIEW_CATALOG"), col("SEMANTIC_VIEW_SCHEMA"), col("SEMANTIC_VIEW_NAME"),
                    col("NAME"), col("BASE_TABLE_CATALOG"), col("BASE_TABLE_SCHEMA"), col("BASE_TABLE_NAME"),
                    colArray("PRIMARY_KEYS"), colArray("SYNONYMS"), colArray("UNIQUE_KEYS"),
                    colArray("DISTINCT_RANGES"), col("COMMENT"), col("DEFINITION"));
            case "SEMANTIC_VARIABLES":
                return empty(
                    col("SEMANTIC_VIEW_CATALOG"), col("SEMANTIC_VIEW_SCHEMA"), col("SEMANTIC_VIEW_NAME"),
                    col("NAME"), col("DATA_TYPE"), col("DEFAULT_VALUE"), col("COMMENT"));
            case "SEMANTIC_VIEWS":
                return empty(
                    col("CATALOG"), col("SCHEMA"), col("NAME"), col("OWNER"), colTs("CREATED"), col("COMMENT"),
                    colNumber("MAX_STALENESS_SEC", 38, 6), col("AI_SQL_GENERATION"),
                    col("AI_QUESTION_CATEGORIZATION"));
            case "SERVICES":
                return empty(
                    col("SERVICE_CATALOG"), col("SERVICE_SCHEMA"), col("SERVICE_NAME"), col("STATUS"),
                    col("SERVICE_OWNER"), col("SERVICE_OWNER_ROLE_TYPE"), col("COMPUTE_POOL_NAME"), col("DNS_NAME"),
                    colLong("CURRENT_INSTANCES"), colLong("TARGET_INSTANCES"), colLong("MIN_READY_INSTANCES"),
                    colLong("MIN_INSTANCES"), colLong("MAX_INSTANCES"), colBool("AUTO_RESUME"), col("COMMENT"),
                    col("QUERY_WAREHOUSE"), colTs("CREATED"), colTs("LAST_ALTERED"), colTs("LAST_RESUMED"),
                    colBool("IS_JOB"), col("SPEC_DIGEST"), colBool("IS_UPGRADING"), col("MANAGING_OBJECT_DOMAIN"),
                    col("MANAGING_OBJECT_NAME"));
            case "SHARES":
                return empty(
                    colTs("CREATED_ON", 9), col("KIND"), col("OWNER_ACCOUNT"), col("NAME"), col("DATABASE_NAME"),
                    col("TO"), col("OWNER"), col("COMMENT"), col("LISTING_GLOBAL_NAME"),
                    col("SECURE_OBJECTS_ONLY"));
            case "SNAPSHOTS":
                return empty(
                    col("ID"), colTs("CREATED"), col("SNAPSHOT_SET_NAME"), col("SNAPSHOT_SET_SCHEMA"),
                    col("SNAPSHOT_SET_CATALOG"), colTs("EXPIRATION_SCHEDULED_FOR", 9),
                    colBool("IS_UNDER_LEGAL_HOLD"), col("COMMENT"));
            case "SNAPSHOT_POLICIES":
                return empty(
                    col("SNAPSHOT_POLICY_NAME"), col("SNAPSHOT_POLICY_SCHEMA"), col("SNAPSHOT_POLICY_CATALOG"),
                    col("SCHEDULE"), colLong("EXPIRE_AFTER_DAYS"), colText("HAS_RETENTION_LOCK", 3), col("OWNER"),
                    col("OWNER_ROLE_TYPE"), colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "SNAPSHOT_SETS":
                return empty(
                    col("SNAPSHOT_SET_NAME"), col("SNAPSHOT_SET_SCHEMA"), col("SNAPSHOT_SET_CATALOG"),
                    colText("OBJECT_KIND", 16), col("OBJECT_NAME"), col("OBJECT_SCHEMA"), col("OBJECT_CATALOG"),
                    col("SNAPSHOT_POLICY_NAME"), col("SNAPSHOT_POLICY_SCHEMA"), col("SNAPSHOT_POLICY_CATALOG"),
                    col("OWNER"), col("OWNER_ROLE_TYPE"), colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            case "STREAMLITS":
                return empty(
                    col("STREAMLIT_CATALOG"), col("STREAMLIT_SCHEMA"), col("STREAMLIT_NAME"),
                    col("STREAMLIT_OWNER"), col("STREAMLIT_ROOT_LOCATION"), col("STREAMLIT_MAIN_FILE"),
                    col("STREAMLIT_QUERY_WAREHOUSE"), col("STREAMLIT_URL_ID"), colTs("CREATED"),
                    colTs("LAST_ALTERED"), col("COMMENT"), col("STREAMLIT_TITLE"));
            case "TYPES":
                return empty(
                    col("TYPE_CATALOG"), col("TYPE_SCHEMA"), col("TYPE_NAME"), col("TYPE_OWNER"),
                    col("BASE_DATA_TYPE"), colLong("CHARACTER_MAXIMUM_LENGTH"), colLong("CHARACTER_OCTET_LENGTH"),
                    colLong("NUMERIC_PRECISION"), colNumber("NUMERIC_PRECISION_RADIX", 2, 0),
                    colLong("NUMERIC_SCALE"), colLong("DATETIME_PRECISION"), colNull("CHECK_EXPRESSION"),
                    colNull("DEFAULT_EXPRESSION"), colNull("IS_NULLABLE_DEFAULT"), colNull("COLLATION_NAME"),
                    colTs("CREATED"), colTs("LAST_ALTERED"), col("COMMENT"));
            default:
                return null;
        }
    }

    private ResultSet empty(final ResultSetColumn... columns) {
        return new ResultSet(Arrays.asList(columns));
    }

    /** A name, a comment, a definition: the VARCHAR with no length of its own (see LengthlessStringType). */
    private ResultSetColumn col(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR, null, new LengthlessStringType());
    }

    /** A column the account's view fills with a bare NULL: VARCHAR(0) in the result metadata, no static type. */
    private ResultSetColumn colNull(final String name) {
        return new ResultSetColumn(name, new StringType("VARCHAR", 0));
    }

    /** A text column the account declares at a measured width — VARCHAR(3) for a YES / NO flag. */
    private ResultSetColumn colText(final String name, final int length) {
        return new ResultSetColumn(name, StringType.VARCHAR, null, new StringType("VARCHAR", length));
    }

    private ResultSetColumn colLong(final String name) {
        return new ResultSetColumn(name, NumericType.BIGINT, null, new NumericType("NUMBER", 38, 0));
    }

    /** A number the account declares narrower or scaled — NUMBER(2,0) for a radix, NUMBER(38,6) for seconds. */
    private ResultSetColumn colNumber(final String name, final int precision, final int scale) {
        return new ResultSetColumn(name, NumericType.BIGINT, null, new NumericType("NUMBER", precision, scale));
    }

    /** A creation or alteration instant: TIMESTAMP_LTZ(3) on the account. */
    private ResultSetColumn colTs(final String name) {
        return colTs(name, 3);
    }

    private ResultSetColumn colTs(final String name, final int precision) {
        final DateTimeType type = new DateTimeType("TIMESTAMP_LTZ", precision, true);
        return new ResultSetColumn(name, type, null, type);
    }

    private ResultSetColumn colBool(final String name) {
        return new ResultSetColumn(name, new BooleanType(), null, new BooleanType());
    }

    private ResultSetColumn colArray(final String name) {
        return new ResultSetColumn(name, new ArrayType(StringType.VARCHAR), null, new ArrayType(StringType.VARCHAR));
    }

    private ResultSetColumn colObject(final String name) {
        return new ResultSetColumn(name, new ObjectType(), null, new ObjectType());
    }

    private ResultSetColumn colVariant(final String name) {
        return new ResultSetColumn(name, new VariantType(), null, new VariantType());
    }
}
