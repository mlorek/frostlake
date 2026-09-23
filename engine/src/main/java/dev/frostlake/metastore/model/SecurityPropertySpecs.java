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
package dev.frostlake.metastore.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The documented properties of network rules, network policies, password policies and secrets, and which secret
 * types take which of them.
 */
public final class SecurityPropertySpecs {

    /** The secret types, as TYPE names them. */
    public static final List<String> SECRET_TYPES = Collections.unmodifiableList(Arrays.asList(
        "OAUTH2", "CLOUD_PROVIDER_TOKEN", "PASSWORD", "GENERIC_STRING", "SYMMETRIC_KEY",
        "WORKLOAD_IDENTITY_FEDERATION"));

    private static final Map<SecurityObjectKind, List<SecurityPropertySpec>> SPECS =
        new EnumMap<>(SecurityObjectKind.class);

    static {
        final List<SecurityPropertySpec> rule = new ArrayList<>();
        add(rule, new SecurityPropertySpec("TYPE", SecurityPropertyType.CHOICE).choices("IPV4", "IPV6", "AWSVPCEID",
            "AZURELINKID", "GCPPSCID", "HOST_PORT", "PRIVATE_HOST_PORT", "COMPUTE_POOL"));
        add(rule, new SecurityPropertySpec("VALUE_LIST", SecurityPropertyType.TEXT_LIST).alterable().unsettable());
        add(rule, new SecurityPropertySpec("MODE", SecurityPropertyType.CHOICE).choices("INGRESS", "INTERNAL_STAGE",
            "SNOWFLAKE_MANAGED_STORAGE_VOLUME", "EGRESS", "POSTGRES_INGRESS", "POSTGRES_EGRESS").defaults("INGRESS"));
        add(rule, comment());
        SPECS.put(SecurityObjectKind.NETWORK_RULE, rule);

        final List<SecurityPropertySpec> networkPolicy = new ArrayList<>();
        add(networkPolicy, new SecurityPropertySpec("ALLOWED_IP_LIST", SecurityPropertyType.TEXT_LIST).alterable()
            .unsettable());
        add(networkPolicy, new SecurityPropertySpec("BLOCKED_IP_LIST", SecurityPropertyType.TEXT_LIST).alterable()
            .unsettable());
        add(networkPolicy, new SecurityPropertySpec("ALLOWED_NETWORK_RULE_LIST", SecurityPropertyType.TEXT_LIST)
            .alterable().unsettable());
        add(networkPolicy, new SecurityPropertySpec("BLOCKED_NETWORK_RULE_LIST", SecurityPropertyType.TEXT_LIST)
            .alterable().unsettable());
        add(networkPolicy, comment());
        SPECS.put(SecurityObjectKind.NETWORK_POLICY, networkPolicy);

        final List<SecurityPropertySpec> passwordPolicy = new ArrayList<>();
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MIN_LENGTH", SecurityPropertyType.INTEGER).range(8, 256).defaults("14")
            .describedAs("Minimum length of new password.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MAX_LENGTH", SecurityPropertyType.INTEGER).range(8, 256).defaults("256")
            .describedAs("Maximum length of new password.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MIN_UPPER_CASE_CHARS", SecurityPropertyType.INTEGER).range(0, 256).defaults("1")
            .describedAs("Minimum number of uppercase characters in new password.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MIN_LOWER_CASE_CHARS", SecurityPropertyType.INTEGER).range(0, 256).defaults("1")
            .describedAs("Minimum number of lowercase characters in new password.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MIN_NUMERIC_CHARS", SecurityPropertyType.INTEGER).range(0, 256).defaults("1")
            .describedAs("Minimum number of numeric characters in new password.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MIN_SPECIAL_CHARS", SecurityPropertyType.INTEGER).range(0, 256).defaults("0")
            .describedAs("Minimum number of special characters in new password.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MIN_AGE_DAYS", SecurityPropertyType.INTEGER).range(0, 999).defaults("0")
            .describedAs("Period after a password is changed during which a password cannot be changed again, in days.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MAX_AGE_DAYS", SecurityPropertyType.INTEGER).range(0, 999).defaults("90")
            .describedAs("Period after which password must be changed, in days.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_MAX_RETRIES", SecurityPropertyType.INTEGER).range(1, 10).defaults("5")
            .describedAs("Number of attempts users have to enter the correct password before their account is locked.").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_LOCKOUT_TIME_MINS", SecurityPropertyType.INTEGER).range(1, 999).defaults("15")
            .describedAs("Period of time for which users will be locked after entering their password incorrectly many times (specified by MAX_RETRIES), in minutes").alterable().unsettable());
        add(passwordPolicy, new SecurityPropertySpec("PASSWORD_HISTORY", SecurityPropertyType.INTEGER).range(0, 24).defaults("5")
            .describedAs("Number of distinct passwords that a user must create before re-using a previous password").alterable().unsettable());
        add(passwordPolicy, comment());
        SPECS.put(SecurityObjectKind.PASSWORD_POLICY, passwordPolicy);

        final List<SecurityPropertySpec> secret = new ArrayList<>();
        add(secret, new SecurityPropertySpec("TYPE", SecurityPropertyType.CHOICE)
            .choices(SECRET_TYPES.toArray(new String[0])));
        add(secret, new SecurityPropertySpec("API_AUTHENTICATION", SecurityPropertyType.NAME).alterable());
        add(secret, new SecurityPropertySpec("OAUTH_SCOPES", SecurityPropertyType.TEXT_LIST).alterable());
        add(secret, new SecurityPropertySpec("OAUTH_REFRESH_TOKEN", SecurityPropertyType.TEXT).alterable().writeOnly());
        add(secret, new SecurityPropertySpec("OAUTH_REFRESH_TOKEN_EXPIRY_TIME", SecurityPropertyType.TEXT).alterable());
        add(secret, new SecurityPropertySpec("ENABLED", SecurityPropertyType.BOOLEAN));
        add(secret, new SecurityPropertySpec("USERNAME", SecurityPropertyType.TEXT).alterable());
        add(secret, new SecurityPropertySpec("PASSWORD", SecurityPropertyType.TEXT).alterable().writeOnly());
        add(secret, new SecurityPropertySpec("SECRET_STRING", SecurityPropertyType.TEXT).alterable().writeOnly());
        add(secret, new SecurityPropertySpec("ALGORITHM", SecurityPropertyType.CHOICE).choices("GENERIC"));
        add(secret, comment());
        SPECS.put(SecurityObjectKind.SECRET, secret);
    }

    private SecurityPropertySpecs() {
    }

    private static void add(final List<SecurityPropertySpec> specs, final SecurityPropertySpec spec) {
        specs.add(spec);
    }

    private static SecurityPropertySpec comment() {
        return new SecurityPropertySpec("COMMENT", SecurityPropertyType.TEXT)
            .describedAs("user comment associated to an object in the dictionary").alterable().unsettable();
    }

    /** Every property of a kind, in the order its CREATE page lists them. */
    public static List<SecurityPropertySpec> of(final SecurityObjectKind kind) {
        return Collections.unmodifiableList(SPECS.get(kind));
    }

    /** The property of a kind by its upper-case name, or null when the kind has none of that name. */
    public static SecurityPropertySpec find(final SecurityObjectKind kind, final String name) {
        for (final SecurityPropertySpec spec : SPECS.get(kind)) {
            if (spec.getName().equals(name)) {
                return spec;
            }
        }
        return null;
    }

    /** The properties CREATE SECRET takes with a secret type, TYPE aside. */
    public static List<String> secretCreateProperties(final String type) {
        if ("OAUTH2".equals(type)) {
            return names("API_AUTHENTICATION", "OAUTH_SCOPES", "OAUTH_REFRESH_TOKEN", "OAUTH_REFRESH_TOKEN_EXPIRY_TIME",
                "COMMENT");
        }
        if ("CLOUD_PROVIDER_TOKEN".equals(type)) {
            return names("API_AUTHENTICATION", "ENABLED", "COMMENT");
        }
        if ("PASSWORD".equals(type)) {
            return names("USERNAME", "PASSWORD", "COMMENT");
        }
        if ("GENERIC_STRING".equals(type)) {
            return names("SECRET_STRING", "COMMENT");
        }
        if ("SYMMETRIC_KEY".equals(type)) {
            return names("ALGORITHM", "COMMENT");
        }
        return names("COMMENT");
    }

    /** The properties CREATE SECRET requires with a secret type. */
    public static List<String> secretRequiredProperties(final String type) {
        if ("OAUTH2".equals(type) || "CLOUD_PROVIDER_TOKEN".equals(type)) {
            return names("API_AUTHENTICATION");
        }
        if ("PASSWORD".equals(type)) {
            return names("USERNAME", "PASSWORD");
        }
        if ("GENERIC_STRING".equals(type)) {
            return names("SECRET_STRING");
        }
        if ("SYMMETRIC_KEY".equals(type)) {
            return names("ALGORITHM");
        }
        return names();
    }

    /** The properties ALTER SECRET … SET changes on a secret of a type. */
    public static List<String> secretAlterableProperties(final String type) {
        if ("OAUTH2".equals(type)) {
            return names("OAUTH_SCOPES", "OAUTH_REFRESH_TOKEN", "OAUTH_REFRESH_TOKEN_EXPIRY_TIME", "COMMENT");
        }
        if ("CLOUD_PROVIDER_TOKEN".equals(type)) {
            return names("API_AUTHENTICATION", "COMMENT");
        }
        if ("PASSWORD".equals(type)) {
            return names("USERNAME", "PASSWORD", "COMMENT");
        }
        if ("GENERIC_STRING".equals(type)) {
            return names("SECRET_STRING", "COMMENT");
        }
        return names("COMMENT");
    }

    private static List<String> names(final String... names) {
        return Collections.unmodifiableList(Arrays.asList(names));
    }
}
