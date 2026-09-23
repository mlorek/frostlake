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
import dev.frostlake.config.AccountIdentity;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An account carries FOUR identifiers and they are genuinely different values — measured together on
 * one account, where the organization-account identifier is {@code TWEPWDT-WJ64893}:
 *
 * <pre>
 *   CURRENT_ORGANIZATION_NAME()  TWEPWDT
 *   CURRENT_ACCOUNT_NAME()       WJ64893     the NAME
 *   CURRENT_ACCOUNT()            PG65914     the LOCATOR — not the name
 *   CURRENT_REGION()             AWS_EU_WEST_1
 * </pre>
 *
 * <p>All four are UPPER-cased. {@code CURRENT_REGION()} is BARE: the {@code PUBLIC.} region-group
 * prefix belongs to an organization spanning multiple region groups, so it is configurable rather than
 * the default — Frostlake used to answer {@code PUBLIC.AWS_US_EAST_1}, encoding the rarer case.
 *
 * <p>The point of the identity model is that these cannot drift: SHOW ACCOUNTS reports the same four
 * facts, and it used to hardcode SIMORG / SIMACCOUNT / AWS_US_EAST_1 while the functions answered
 * something else entirely.
 */
public class AccountIdentityTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private Object accountCell(final String column) {
        final ResultSet rs = engine.executeQuery("SHOW ACCOUNTS");
        return rs.getRows().get(0).getValue(rs.getColumnIndex(column));
    }

    @Test
    public void theFourIdentifiersAreAnsweredAndUpperCased() {
        for (final String function : List.of("CURRENT_ORGANIZATION_NAME()", "CURRENT_ACCOUNT()",
                "CURRENT_ACCOUNT_NAME()", "CURRENT_REGION()")) {
            final String value = scalar("SELECT " + function);
            assertNotNull(value, function);
            assertEquals(value.toUpperCase(), value, function + " is upper-cased");
        }
    }

    /** The region carries no region-group prefix unless one is configured. */
    @Test
    public void theRegionIsBareByDefault() {
        // The value is the ACCOUNT's region (AWS_EU_WEST_1 here, AWS_US_EAST_1 on another), so pin the
        // shape the test is really about: a bare <cloud>_<region> with no region-group prefix. The
        // prefixed spelling is exercised by the next test.
        final String region = String.valueOf(scalar("SELECT CURRENT_REGION()"));
        assertTrue(region.matches("[A-Z0-9]+_[A-Z0-9_]+"), "bare region shape, was: " + region);
        assertFalse(region.contains("."), "no region-group prefix, was: " + region);
    }

    /** …and the prefixed spelling is still reachable, because that organization shape is real. */
    @Test
    public void aRegionGroupPrefixIsKeptWhenConfigured() {
        final Properties overrides = new Properties();
        overrides.setProperty(EngineConfig.PROP_SNOWFLAKE_REGION, "PUBLIC.AWS_US_WEST_2");
        assertEquals("PUBLIC.AWS_US_WEST_2",
            AccountIdentity.of(new EngineConfig(overrides)).getRegion());
    }

    /** SHOW ACCOUNTS reports the same identity the functions do, rather than its own hardcoded one. */
    @Test
    public void showAccountsAgreesWithTheContextFunctions() {
        assertEquals(scalar("SELECT CURRENT_ORGANIZATION_NAME()"), accountCell("organization_name"));
        assertEquals(scalar("SELECT CURRENT_ACCOUNT_NAME()"), accountCell("account_name"));
        assertEquals(scalar("SELECT CURRENT_ACCOUNT()"), accountCell("account_locator"));
        assertEquals(scalar("SELECT CURRENT_REGION()"), accountCell("snowflake_region"));
    }

    /**
     * The 25-column shape SHOW ACCOUNTS and SHOW ORGANIZATION ACCOUNTS share, ending with {@code contract_number}.
     * Note what is NOT here: {@code region_group} and {@code org_default_region} were Frostlake's own inventions,
     * and asking a real account for either is an invalid identifier.
     */
    @Test
    public void bothAccountListingsCarryTheMeasuredShape() {
        final List<String> shape = List.of(
            "organization_name", "account_name", "snowflake_region", "edition", "account_url",
            "created_on", "comment", "account_locator", "account_locator_url", "managed_accounts",
            "consumption_billing_entity_name", "marketplace_consumer_billing_entity_name",
            "marketplace_provider_billing_entity_name", "old_account_url", "is_org_admin",
            "account_old_url_saved_on", "account_old_url_last_used", "organization_old_url",
            "organization_old_url_saved_on", "organization_old_url_last_used", "is_events_account",
            "is_organization_account", "tenant_type", "domain_names", "contract_number");
        for (final String listing : List.of("SHOW ACCOUNTS", "SHOW ORGANIZATION ACCOUNTS")) {
            final ResultSet rs = engine.executeQuery(listing);
            assertEquals(shape.size(), rs.getColumns().size(), listing);
            for (int i = 0; i < shape.size(); i++) {
                assertEquals(shape.get(i), rs.getColumns().get(i).getName(), listing + " column " + i);
            }
        }
    }

    /**
     * The two URLs are built from different halves of the identity and both are lower-cased where the
     * identifiers are upper — measured: {@code https://twepwdt-wj64893.snowflakecomputing.com} and
     * {@code https://pg65914.eu-west-1.snowflakecomputing.com}, the second carrying a cloud-region
     * slug rather than the region name.
     */
    @Test
    public void theUrlsAreBuiltFromTheIdentity() {
        final AccountIdentity identity =
            new AccountIdentity("TWEPWDT", "WJ64893", "PG65914", "AWS_EU_WEST_1");
        assertEquals("https://twepwdt-wj64893.snowflakecomputing.com", identity.getAccountUrl());
        assertEquals("https://pg65914.eu-west-1.snowflakecomputing.com",
            identity.getAccountLocatorUrl());
    }

    /** A configured region group is not part of the host, so the locator URL drops it. */
    @Test
    public void theLocatorUrlDropsARegionGroup() {
        assertEquals("https://abc.us-west-2.snowflakecomputing.com",
            new AccountIdentity("ORG", "ACC", "ABC", "PUBLIC.AWS_US_WEST_2").getAccountLocatorUrl());
    }

    /** An absent old URL is the empty string; the moments beside it are null. Measured. */
    @Test
    public void anAbsentOldUrlIsEmptyAndItsMomentsAreNull() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "a real account's old-URL history is its own, not something a test may assert");
        assertEquals("", accountCell("old_account_url"));
        assertEquals(null, accountCell("account_old_url_saved_on"));
        assertEquals(null, accountCell("organization_old_url_last_used"));
    }
}
