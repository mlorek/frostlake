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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.List;

/**
 * The listings of the object kinds this engine models no object of (live-verified, column for column). Each
 * answers the columns the account declares for it and no rows: a client that reads a listing reads its shape even
 * when nothing is listed. A column is text unless its name is marked: {@code @} for a moment, TIMESTAMP_LTZ(3),
 * {@code #} for a count, NUMBER(9,0), and {@code ##} for a size or a sequencer, NUMBER(38,0). TERSE narrows three
 * of the listings to the five columns a terse listing of schema objects has, and leaves the others as they are.
 */
enum UnmodelledListing {

    RESOURCE_MONITORS("""
        name credit_quota used_credits remaining_credits level frequency start_time@ end_time@ notify_at suspend_at
        suspend_immediately_at created_on@ owner comment notify_users
        """, false),
    SHARES("""
        created_on@ kind owner_account name database_name to owner comment listing_global_name secure_objects_only
        """, false),
    INDEXES("""
        created_on@ name is_unique table columns included_columns database_name schema_name owner status status_info
        owner_role_type
        """, true),
    /** The indexes of a table named, whose listing words their columns otherwise and goes on past the schema's. */
    TABLE_INDEXES("""
        created_on@ name is_unique table key_expressions included_expressions database_name schema_name owner status
        status_info owner_role_type kind progress_percentage# storage_bytes##
        """, true),
    REGIONS("snowflake_region cloud region display_name", false),
    SESSION_POLICIES("created_on@ name database_name schema_name kind owner comment owner_role_type options", false),
    AUTHENTICATION_POLICIES(SESSION_POLICIES.spec, false),
    PACKAGES_POLICIES(SESSION_POLICIES.spec, false),
    PRIVACY_POLICIES(SESSION_POLICIES.spec, false),
    FUNCTIONS("""
        created_on@ name schema_name is_builtin is_aggregate is_ansi min_num_arguments# max_num_arguments# arguments
        description catalog_name is_table_function valid_for_clustering is_secure secrets external_access_integrations
        is_external_function language is_memoizable is_data_metric is_ai_function
        """, false),
    REPLICATION_DATABASES("""
        snowflake_region created_on@ account_name name comment is_primary primary replication_allowed_to_accounts
        failover_allowed_to_accounts organization_name account_locator
        """, false),
    REPLICATION_GROUPS("""
        snowflake_region created_on@ account_name name type comment is_primary primary object_types
        allowed_integration_types allowed_accounts organization_name account_locator replication_schedule
        secondary_state next_scheduled_refresh@ owner is_listing_auto_fulfillment_group error_integration
        is_primary_read_only is_optimized_refresh_enabled
        """, false),
    REPLICATION_ACCOUNTS("""
        snowflake_region created_on@ account_name account_locator comment organization_name is_org_admin
        """, false),
    GIT_REPOSITORIES("""
        created_on@ name database_name schema_name origin api_integration git_credentials owner owner_role_type comment
        last_fetched_at@ use_private_link repository_size
        """, false),
    CHANNELS("""
        created_on@ name database_name schema_name table_name client_sequencer## row_sequencer## offset_token
        parent_name parent_domain
        """, false),
    SNAPSHOTS("""
        name state database_name schema_name service_name volume_name instance size## comment owner owner_role_type
        created_on@ updated_on@ encryption
        """, false),
    DELEGATED_AUTHORIZATIONS("""
        created_on@ user_name role_name consented_roles integration_name integration_status can_switch_roles
        """, false),
    CONNECTIONS("""
        snowflake_region created_on@ account_name name comment primary replication_group external_id connection_url
        """, false),
    APPLICATIONS("""
        created_on@ name is_default is_current source_type source owner comment version label patch# options
        retention_time upgrade_state disablement_reasons last_upgraded_on@ release_channel_name type
        """, false),
    APPLICATION_PACKAGES("""
        created_on@ name is_default is_current distribution multiple_instances owner comment options retention_time
        dropped_on@ type
        """, true),
    SEMANTIC_VIEWS("""
        created_on@ name database_name schema_name comment owner owner_role_type extension max_staleness
        """, true);

    /** What TERSE lists for the listings it narrows. */
    private static final String TERSE = "created_on@ name kind database_name schema_name";

    private static final NumericType WIDE_NUMBER = new NumericType("NUMBER", 38, 0);

    private final String spec;
    private final boolean terseNarrows;

    UnmodelledListing(final String spec, final boolean terseNarrows) {
        this.spec = spec;
        this.terseNarrows = terseNarrows;
    }

    /**
     * The columns of a listing.
     *
     * @param listing a listing of a kind no object is modelled of
     * @return its columns' description
     */
    static UnmodelledListing of(final ShowListing listing) {
        switch (listing) {
            case EXTERNAL_FUNCTIONS:
            case DATA_METRIC_FUNCTIONS:
                return FUNCTIONS;
            case FAILOVER_GROUPS:
                return REPLICATION_GROUPS;
            default:
                return valueOf(listing.name());
        }
    }

    /**
     * The listing's columns.
     *
     * @param terse whether the statement was written SHOW TERSE
     * @return the columns, in the account's order
     */
    List<ResultSetColumn> columns(final boolean terse) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String marked : (terse && terseNarrows ? TERSE : spec).trim().split("\\s+")) {
            final DataType type;
            final String name;
            if (marked.endsWith("##")) {
                name = marked.substring(0, marked.length() - 2);
                type = WIDE_NUMBER;
            } else if (marked.endsWith("#")) {
                name = marked.substring(0, marked.length() - 1);
                type = ShowResultHelpers.COUNT;
            } else if (marked.endsWith("@")) {
                name = marked.substring(0, marked.length() - 1);
                type = ShowResultHelpers.CREATED_ON;
            } else {
                name = marked;
                type = StringType.VARCHAR;
            }
            columns.add(new ResultSetColumn(name, type));
        }
        return columns;
    }

    /**
     * The empty listing.
     *
     * @param terse whether the statement was written SHOW TERSE
     * @return the listing's columns and no rows
     */
    ResultSet listing(final boolean terse) {
        return new ResultSet(columns(terse));
    }
}
