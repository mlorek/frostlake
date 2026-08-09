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

package dev.frostlake.config;

import java.util.Locale;

/**
 * Who this engine says it is: one organization, one account, one region — assembled once and read by
 * everything that reports any of it, so the same fact cannot be spelled three different ways.
 *
 * <p>An account carries FOUR identifiers, not two, and they are genuinely distinct. Measured together
 * on one account:
 *
 * <pre>
 *   CURRENT_ORGANIZATION_NAME()  TWEPWDT      the organization
 *   CURRENT_ACCOUNT_NAME()       WJ64893      the account's NAME
 *   CURRENT_ACCOUNT()            PG65914      the account's LOCATOR — a different value
 *   CURRENT_REGION()             AWS_EU_WEST_1
 * </pre>
 *
 * <p>The two URLs are built from different halves of that, and both are LOWER-cased where the
 * identifiers themselves are upper — also measured:
 *
 * <pre>
 *   account_url          https://twepwdt-wj64893.snowflakecomputing.com     org-name
 *   account_locator_url  https://pg65914.eu-west-1.snowflakecomputing.com   locator.cloud-region
 * </pre>
 *
 * <p>The locator URL's region slug drops the cloud prefix and swaps underscores for dashes
 * ({@code AWS_EU_WEST_1} to {@code eu-west-1}). That derivation comes from a single measured pair, so
 * it is a reasonable reading rather than a confirmed rule across clouds.
 *
 * <p><b>The region is bare.</b> {@code CURRENT_REGION()} answers {@code AWS_EU_WEST_1}, not
 * {@code PUBLIC.AWS_EU_WEST_1}. The {@code <region_group>.<region>} spelling is real but belongs to an
 * organization spanning multiple region groups, so it is reachable by configuring
 * {@code snowflake.region} with the prefix rather than being the default. Note that SHOW ACCOUNTS has
 * no {@code region_group} column at all — asking for one is an invalid identifier there.
 */
public final class AccountIdentity {

    private static final String DOMAIN = ".snowflakecomputing.com";

    private final String organization;
    private final String accountName;
    private final String accountLocator;
    private final String region;

    public AccountIdentity(final String organization, final String accountName,
                           final String accountLocator, final String region) {
        this.organization = organization;
        this.accountName = accountName;
        this.accountLocator = accountLocator;
        this.region = region;
    }

    /** The identity a configuration describes. */
    public static AccountIdentity of(final EngineConfig config) {
        if (config == null) {
            return of(new EngineConfig());
        }
        return new AccountIdentity(config.getOrganizationName(), config.getAccountName(),
            config.getAccountId(), config.getRegion());
    }

    public String getOrganization() {
        return organization;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountLocator() {
        return accountLocator;
    }

    /** As {@code CURRENT_REGION()} answers it — whatever was configured, prefix and all. */
    public String getRegion() {
        return region;
    }

    /** {@code https://<org>-<account>.snowflakecomputing.com}, lower-cased. */
    public String getAccountUrl() {
        return "https://" + lower(organization) + "-" + lower(accountName) + DOMAIN;
    }

    /** {@code https://<locator>.<cloud-region>.snowflakecomputing.com}, lower-cased. */
    public String getAccountLocatorUrl() {
        return "https://" + lower(accountLocator) + "." + cloudRegionSlug() + DOMAIN;
    }

    /**
     * The region as a URL host part: the cloud prefix dropped, dashes for underscores, lower-cased —
     * {@code AWS_EU_WEST_1} becomes {@code eu-west-1}. A region carrying a region-group prefix keeps
     * only the region itself, since the group is not part of the host.
     */
    private String cloudRegionSlug() {
        String bare = region == null ? "" : region;
        final int group = bare.indexOf('.');
        if (group >= 0) {
            bare = bare.substring(group + 1);
        }
        for (final String cloud : new String[] {"AWS_", "AZURE_", "GCP_"}) {
            if (bare.toUpperCase(Locale.ROOT).startsWith(cloud)) {
                bare = bare.substring(cloud.length());
                break;
            }
        }
        return lower(bare).replace('_', '-');
    }

    private String lower(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
