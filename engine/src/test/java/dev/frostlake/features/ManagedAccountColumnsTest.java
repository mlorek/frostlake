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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The columns of the account listings the account answers without creating anything: SHOW MANAGED ACCOUNTS'
 * eighteen, and SHOW ACCOUNTS HISTORY's, which add the dropped, moved and organization-URL columns.
 */
public class ManagedAccountColumnsTest extends BaseDatabaseTest {

    private List<String> columns(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    @Test
    public void showManagedAccountsHasEighteenColumns() {
        assertEquals(List.of("account_name", "cloud", "region", "account_locator", "created_on", "account_url",
            "account_locator_url", "is_reader", "comment", "region_group", "old_account_url",
            "account_old_url_saved_on", "account_old_url_last_used", "organization_old_url",
            "organization_old_url_saved_on", "organization_old_url_last_used", "tenant_type", "domain_names"),
            columns("SHOW MANAGED ACCOUNTS LIKE 'NO_SUCH_ACCOUNT_%'"));
    }

    @Test
    public void showAccountsHistoryAddsTheDroppedMovedAndUrlColumns() {
        // The account answers HISTORY only to a role that manages organization accounts, which the test session's
        // primary role does not.
        Assumptions.assumeFalse(isLiveSnowflake());
        assertEquals(List.of("organization_name", "account_name", "snowflake_region", "edition", "account_url",
            "created_on", "comment", "account_locator", "account_locator_url", "managed_accounts",
            "consumption_billing_entity_name", "marketplace_consumer_billing_entity_name",
            "marketplace_provider_billing_entity_name", "old_account_url", "is_org_admin", "dropped_on",
            "scheduled_deletion_time", "restored_on", "account_old_url_saved_on", "account_old_url_last_used",
            "organization_old_url", "organization_old_url_saved_on", "organization_old_url_last_used",
            "moved_to_organization", "moved_on", "organization_URL_expiration_on", "is_events_account",
            "is_organization_account", "tenant_type", "domain_names", "contract_number"),
            columns("SHOW ACCOUNTS HISTORY LIKE 'NO_SUCH_ACCOUNT_%'"));
    }
}
