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
 * The documented properties of network rules, network policies, password, session, authentication and packages
 * policies and secrets, and which secret types take which of them.
 */
public final class SecurityPropertySpecs {

    /** The secret types, as TYPE names them. */
    public static final List<String> SECRET_TYPES = Collections.unmodifiableList(Arrays.asList(
        "OAUTH2", "CLOUD_PROVIDER_TOKEN", "PASSWORD", "GENERIC_STRING", "SYMMETRIC_KEY",
        "WORKLOAD_IDENTITY_FEDERATION"));

    /** The session policy property naming the secondary roles a session may activate. */
    public static final String ALLOWED_SECONDARY_ROLES = "ALLOWED_SECONDARY_ROLES";

    /** The session policy property naming the secondary roles a session may not activate. */
    public static final String BLOCKED_SECONDARY_ROLES = "BLOCKED_SECONDARY_ROLES";

    /**
     * The row a session policy's description and DDL end on: a scope applied to an agent's sessions, which no
     * statement here sets.
     */
    public static final String AGENT_RESTRICTED_SESSION_SCOPE = "AGENT_RESTRICTED_SESSION_SCOPE";

    /** What DESCRIBE SESSION POLICY says of {@link #AGENT_RESTRICTED_SESSION_SCOPE}. */
    public static final String AGENT_RESTRICTED_SESSION_SCOPE_DESCRIPTION =
        "Name of a Restricted Session Scope applied to sessions attributed to an agent identity.";

    /** The Restricted Session Scopes the account defines, which an agent scope may name. */
    public static final List<String> PREDEFINED_SESSION_SCOPES = Collections.unmodifiableList(Arrays.asList(
        "SNOWFLAKE$DATA_READ", "SNOWFLAKE$DATA_READ_PROGRAM_USAGE", "SNOWFLAKE$DATA_READ_WITH_AI"));

    /** The authentication policy property naming the security integrations users may authenticate through. */
    public static final String SECURITY_INTEGRATIONS = "SECURITY_INTEGRATIONS";

    /** The packages policy property naming the packages a handler may use. */
    public static final String ALLOWLIST = "ALLOWLIST";

    /** The packages policy property naming the packages a handler may not use. */
    public static final String BLOCKLIST = "BLOCKLIST";

    private static final Map<SecurityObjectKind, List<SecurityPropertySpec>> SPECS =
        new EnumMap<>(SecurityObjectKind.class);

    static {
        final List<SecurityPropertySpec> rule = new ArrayList<>();
        add(rule, new SecurityPropertySpec("TYPE", SecurityPropertyType.CHOICE).choices("IPV4", "IPV6", "AWSVPCEID",
            "AZURELINKID", "GCPPSCID", "HOST_PORT", "PRIVATE_HOST_PORT", "COMPUTE_POOL"));
        add(rule, new SecurityPropertySpec("VALUE_LIST", SecurityPropertyType.TEXT_LIST).alterable().unsettable());
        add(rule, new SecurityPropertySpec("MODE", SecurityPropertyType.CHOICE).choices("INGRESS", "INTERNAL_STAGE",
            "SNOWFLAKE_MANAGED_STORAGE_VOLUME", "EGRESS", "POSTGRES_INGRESS", "POSTGRES_EGRESS",
            "DATA_CONNECTIVITY_PROXY_EGRESS").defaults("INGRESS"));
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

        final List<SecurityPropertySpec> sessionPolicy = new ArrayList<>();
        add(sessionPolicy, minutes("SESSION_IDLE_TIMEOUT_MINS", 5, 1440, "240", """
            Number of minutes of idle time in a non-UI session (e.g. driver session) before the user must \
            reauthenticate."""));
        add(sessionPolicy, minutes("SESSION_UI_IDLE_TIMEOUT_MINS", 5, 1440, "1080", """
            Number of minutes of idle time in a UI session before the user must reauthenticate."""));
        add(sessionPolicy, minutes("SESSION_MAX_LIFESPAN_MINS", 0, 43200, "0", """
            Maximum lifespan in minutes for a non-UI session (e.g. driver session) before the user must \
            reauthenticate. 0 means no maximum lifespan enforcement."""));
        add(sessionPolicy, minutes("SESSION_UI_MAX_LIFESPAN_MINS", 0, 43200, "0", """
            Maximum lifespan in minutes for a UI session before the user must reauthenticate. 0 means no maximum \
            lifespan enforcement."""));
        add(sessionPolicy, new SecurityPropertySpec(ALLOWED_SECONDARY_ROLES, SecurityPropertyType.TEXT_LIST)
            .describedAs("List of secondary roles allowed to be activated in a user session.").alterable()
            .unsettable());
        add(sessionPolicy, new SecurityPropertySpec(BLOCKED_SECONDARY_ROLES, SecurityPropertyType.TEXT_LIST)
            .describedAs("List of secondary roles blocked from activation in a user session.").alterable()
            .unsettable());
        add(sessionPolicy, comment());
        add(sessionPolicy, new SecurityPropertySpec(AGENT_RESTRICTED_SESSION_SCOPE, SecurityPropertyType.SESSION_SCOPE)
            .describedAs(AGENT_RESTRICTED_SESSION_SCOPE_DESCRIPTION).alterable().unsettable());
        SPECS.put(SecurityObjectKind.SESSION_POLICY, sessionPolicy);

        final List<SecurityPropertySpec> authenticationPolicy = new ArrayList<>();
        add(authenticationPolicy, new SecurityPropertySpec("AUTHENTICATION_METHODS", SecurityPropertyType.CHOICE_LIST)
            .choices("ALL", "SAML", "OIDC", "PASSWORD", "OAUTH", "KEYPAIR", "PROGRAMMATIC_ACCESS_TOKEN",
                "WORKLOAD_IDENTITY").defaults("[ALL]")
            .describedAs("Authentication methods that are allowed for usage.").alterable().unsettable());
        add(authenticationPolicy, new SecurityPropertySpec("CLIENT_TYPES", SecurityPropertyType.CHOICE_LIST)
            .choices("ALL", "SNOWFLAKE_UI", "DRIVERS", "SNOWFLAKE_CLI", "SNOWSQL").defaults("[ALL]")
            .describedAs("Client types that are allowed for access.").alterable().unsettable());
        add(authenticationPolicy, new SecurityPropertySpec(SECURITY_INTEGRATIONS, SecurityPropertyType.TEXT_LIST)
            .defaults("[ALL]").describedAs("Security integrations that are allowed for authentication.").alterable()
            .unsettable());
        add(authenticationPolicy, new SecurityPropertySpec("MFA_ENROLLMENT", SecurityPropertyType.CHOICE)
            .choices("REQUIRED", "REQUIRED_PASSWORD_ONLY", "OPTIONAL").defaults("OPTIONAL").describedAs("""
                When set to REQUIRED, force a user to enroll in MFA according to the MFA_AUTHENTICATION_METHODS. \
                Allowed values are REQUIRED | OPTIONAL""").alterable().unsettable());
        add(authenticationPolicy, new SecurityPropertySpec("COMMENT", SecurityPropertyType.TEXT)
            .describedAs("Comment associated with authentication policy.").alterable().unsettable());
        SPECS.put(SecurityObjectKind.AUTHENTICATION_POLICY, authenticationPolicy);

        final List<SecurityPropertySpec> packagesPolicy = new ArrayList<>();
        add(packagesPolicy, new SecurityPropertySpec(ALLOWLIST, SecurityPropertyType.TEXT_LIST).alterable()
            .unsettable());
        add(packagesPolicy, new SecurityPropertySpec(BLOCKLIST, SecurityPropertyType.TEXT_LIST).alterable()
            .unsettable());
        add(packagesPolicy, new SecurityPropertySpec("ADDITIONAL_CREATION_BLOCKLIST", SecurityPropertyType.TEXT_LIST)
            .alterable().unsettable());
        add(packagesPolicy, new SecurityPropertySpec("COMMENT", SecurityPropertyType.TEXT).alterable().unsettable());
        SPECS.put(SecurityObjectKind.PACKAGES_POLICY, packagesPolicy);

        final List<SecurityPropertySpec> secret = new ArrayList<>();
        add(secret, new SecurityPropertySpec("TYPE", SecurityPropertyType.CHOICE)
            .choices(SECRET_TYPES.toArray(new String[0])));
        add(secret, new SecurityPropertySpec("API_AUTHENTICATION", SecurityPropertyType.NAME).alterable());
        add(secret, new SecurityPropertySpec("OAUTH_SCOPES", SecurityPropertyType.TEXT_LIST).alterable());
        add(secret, new SecurityPropertySpec("OAUTH_REFRESH_TOKEN", SecurityPropertyType.TEXT).alterable().writeOnly());
        add(secret, new SecurityPropertySpec("OAUTH_REFRESH_TOKEN_EXPIRY_TIME", SecurityPropertyType.TEXT).alterable());
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

    /** An alterable, unsettable number of minutes within a closed range, with its default and its description. */
    private static SecurityPropertySpec minutes(final String name, final long lowest, final long highest,
                                                final String defaultValue, final String description) {
        return new SecurityPropertySpec(name, SecurityPropertyType.INTEGER).range(lowest, highest)
            .defaults(defaultValue).describedAs(description).alterable().unsettable();
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

    /**
     * The value a property holds when a CREATE leaves it out or an ALTER unsets it: a network rule's value list, a
     * session policy's blocked roles and a packages policy's blocklist are empty, a session policy's allowed roles
     * and an authentication policy's methods, client types and security integrations are {@code ('ALL')}, a
     * packages policy's allowlist is {@code ('*')}, and any other property holds none.
     *
     * @param name the property's name
     * @return the value, a fresh list for a list property
     */
    public static Object unsetValue(final String name) {
        if ("VALUE_LIST".equals(name) || BLOCKED_SECONDARY_ROLES.equals(name) || BLOCKLIST.equals(name)) {
            return new ArrayList<String>();
        }
        if (ALLOWED_SECONDARY_ROLES.equals(name) || "AUTHENTICATION_METHODS".equals(name)
                || "CLIENT_TYPES".equals(name) || SECURITY_INTEGRATIONS.equals(name)) {
            return new ArrayList<String>(Collections.singletonList("ALL"));
        }
        if (ALLOWLIST.equals(name)) {
            return new ArrayList<String>(Collections.singletonList("*"));
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
            return names("API_AUTHENTICATION", "COMMENT");
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
