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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * An authentication policy's settings that are lists of properties: MFA_POLICY, PAT_POLICY, WORKLOAD_IDENTITY_POLICY
 * and CLIENT_POLICY. Each holds its properties at their defaults until a statement sets them, and is judged, told and
 * spelled as the account judges, describes and spells it (live-verified):
 * <ul>
 *   <li>a CREATE sets what it writes over the defaults, and a CREATE OR ALTER leaves a setting it does not write at
 *   its defaults;</li>
 *   <li>an ALTER … SET of MFA_POLICY or CLIENT_POLICY replaces the setting, and one of PAT_POLICY or
 *   WORKLOAD_IDENTITY_POLICY changes only the properties it writes;</li>
 *   <li>DESCRIBE tells each property in a fixed order, a list in brackets, the drivers of CLIENT_POLICY by name;</li>
 *   <li>GET_DDL spells them in the order a hash map of their names keeps, a list's items quoted.</li>
 * </ul>
 */
public final class AuthenticationPolicyLists {

    public static final String MFA_POLICY = "MFA_POLICY";
    public static final String PAT_POLICY = "PAT_POLICY";
    public static final String WORKLOAD_IDENTITY_POLICY = "WORKLOAD_IDENTITY_POLICY";
    public static final String CLIENT_POLICY = "CLIENT_POLICY";

    /** The four settings, in the order a policy's DESCRIBE and DDL tell them after CLIENT_POLICY. */
    public static final List<String> SETTINGS = Arrays.asList(MFA_POLICY, PAT_POLICY, WORKLOAD_IDENTITY_POLICY);

    /** The capitalized prefix the value checks of these settings open with. */
    private static final String COMPILATION_ERROR = "SQL Compilation Error:";

    private static final String ALLOWED_METHODS = "ALLOWED_METHODS";
    private static final String ENFORCE = "ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION";
    private static final String DEFAULT_EXPIRY = "DEFAULT_EXPIRY_IN_DAYS";
    private static final String MAX_EXPIRY = "MAX_EXPIRY_IN_DAYS";
    private static final String NETWORK_EVALUATION = "NETWORK_POLICY_EVALUATION";
    private static final String SERVICE_USERS = "REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS";
    private static final String PERSON_USERS = "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS";
    private static final String BLOCKED_ROLES = "BLOCKED_ROLES_LIST";
    private static final String PROVIDERS = "ALLOWED_PROVIDERS";
    private static final String AWS_ACCOUNTS = "ALLOWED_AWS_ACCOUNTS";
    private static final String AWS_PARTITIONS = "ALLOWED_AWS_PARTITIONS";
    private static final String AZURE_ISSUERS = "ALLOWED_AZURE_ISSUERS";
    private static final String OIDC_ISSUERS = "ALLOWED_OIDC_ISSUERS";
    private static final String MINIMUM_VERSION = "MINIMUM_VERSION";
    private static final String ALL = "ALL";

    private static final List<String> METHODS = Arrays.asList(ALL, "PASSKEY", "TOTP", "DUO", "OTP");
    private static final List<String> PROVIDER_NAMES = Arrays.asList(ALL, "AWS", "AZURE", "GCP", "OIDC");
    private static final List<String> PARTITIONS = Arrays.asList("aws", "aws-cn", "aws-us-gov");
    private static final List<String> EVALUATIONS =
        Arrays.asList("ENFORCED_REQUIRED", "ENFORCED_NOT_REQUIRED", "NOT_ENFORCED");
    private static final List<String> DRIVERS = Arrays.asList("JDBC_DRIVER", "ODBC_DRIVER", "PYTHON_DRIVER",
        "JAVASCRIPT_DRIVER", "C_DRIVER", "GO_DRIVER", "PHP_DRIVER", "DOTNET_DRIVER", "SQL_API",
        "SNOWPIPE_STREAMING_CLIENT_SDK", "PY_CORE", "SPROC_PYTHON", "PYTHON_SNOWPARK", "SQL_ALCHEMY", "SNOWPARK",
        "SNOWFLAKE_CLIENT");

    private static final Pattern AWS_ACCOUNT = Pattern.compile("[0-9]{12}");
    private static final Pattern AZURE_ISSUER =
        Pattern.compile("https://login\\.microsoftonline\\.com/[^/\\s]+/v2\\.0");
    private static final Pattern OIDC_ISSUER = Pattern.compile("https://[^/?#\\s]+(/[^?#\\s]*)?");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");

    private AuthenticationPolicyLists() {
    }

    /**
     * A setting at its defaults: every property it holds, in the order DESCRIBE tells them.
     *
     * @param setting the setting
     * @return its properties, the drivers of CLIENT_POLICY none
     */
    static LinkedHashMap<String, Object> defaults(final String setting) {
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (MFA_POLICY.equals(setting)) {
            values.put(ALLOWED_METHODS, all());
            values.put(ENFORCE, "NONE");
        } else if (PAT_POLICY.equals(setting)) {
            values.put(DEFAULT_EXPIRY, Long.valueOf(15));
            values.put(MAX_EXPIRY, Long.valueOf(365));
            values.put(NETWORK_EVALUATION, "ENFORCED_REQUIRED");
            values.put(SERVICE_USERS, Boolean.TRUE);
            values.put(PERSON_USERS, Boolean.FALSE);
            values.put(BLOCKED_ROLES, new ArrayList<String>());
        } else if (WORKLOAD_IDENTITY_POLICY.equals(setting)) {
            values.put(PROVIDERS, all());
            values.put(AWS_ACCOUNTS, all());
            values.put(AWS_PARTITIONS, all());
            values.put(AZURE_ISSUERS, all());
            values.put(OIDC_ISSUERS, all());
        }
        return values;
    }

    private static List<String> all() {
        final List<String> all = new ArrayList<>();
        all.add(ALL);
        return all;
    }

    /**
     * What a statement writes for a setting, before it is judged against what the policy holds: its list of
     * properties, or an empty list for {@code ()}, which leaves a setting at its defaults. Any other value is refused
     * as no property list, {@code invalid type of property 'Not a property list: TOK_CONSTANT_LIST' for 'MFA_POLICY'}.
     *
     * @param setting  the setting
     * @param property the property as written
     * @return the list of properties, or an empty list
     */
    static Object written(final String setting, final FrostlakeParser.SecurityPropertyContext property) {
        if (property.securityPropertyList() != null) {
            return property.securityPropertyList();
        }
        final FrostlakeParser.SecurityPropertyValueContext value = property.securityPropertyValue();
        final String shown;
        if (value.LPAREN() != null && value.STRING_LITERAL().isEmpty() && value.SESSION_VAR_REF().isEmpty()) {
            return new ArrayList<Object>();
        } else if (value.LPAREN() != null) {
            shown = "TOK_CONSTANT_LIST";
        } else if (value.NULL() != null) {
            shown = "NULL";
        } else {
            shown = ParseTreeText.getOriginalText(value);
        }
        throw new RuntimeException(SqlCompilationError.of("invalid type of property 'Not a property list: " + shown
            + "' for '" + setting + "'"));
    }

    /**
     * Whether an ALTER … SET of a setting changes only the properties it writes, where the others replace the
     * setting whole.
     *
     * @param setting the setting
     * @return whether it does
     */
    static boolean merges(final String setting) {
        return PAT_POLICY.equals(setting) || WORKLOAD_IDENTITY_POLICY.equals(setting);
    }

    /**
     * A setting's properties as the policy holds them, its defaults when none were set.
     *
     * @param policy  the policy
     * @param setting the setting
     * @return a copy of its properties
     */
    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, Object> held(final SecurityObject policy, final String setting) {
        final Object held = policy.getProperty(setting);
        return held instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) held) : defaults(setting);
    }

    /**
     * A setting as a statement leaves it: what it writes, judged, over the properties it starts from.
     *
     * @param setting the setting
     * @param written what {@link #written} read
     * @param base    the properties it starts from, which are not changed
     * @param catalog the catalog the blocked roles are looked up in
     * @return the setting's properties
     */
    static LinkedHashMap<String, Object> resolve(final String setting, final Object written,
                                                 final Map<String, Object> base, final Catalog catalog) {
        final LinkedHashMap<String, Object> values = new LinkedHashMap<>(base);
        if (!(written instanceof FrostlakeParser.SecurityPropertyListContext)) {
            return values;
        }
        final FrostlakeParser.SecurityPropertyListContext list = (FrostlakeParser.SecurityPropertyListContext) written;
        if (CLIENT_POLICY.equals(setting)) {
            return drivers(list);
        }
        final Set<String> seen = new HashSet<>();
        for (final FrostlakeParser.SecurityListEntryContext entry : list.securityListEntry()) {
            final String key = entry.optionKey().getText().toUpperCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(key));
            }
            if (!values.containsKey(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + setting + "." + key + "'"));
            }
            values.put(key, value(setting, key, entry, catalog));
        }
        if (PAT_POLICY.equals(setting)) {
            requireExpiry(values, seen.contains(DEFAULT_EXPIRY), seen.contains(MAX_EXPIRY));
        }
        return values;
    }

    /** One property's value, judged as that property judges it. */
    private static Object value(final String setting, final String key,
                                final FrostlakeParser.SecurityListEntryContext entry, final Catalog catalog) {
        final FrostlakeParser.SecurityListValueContext value = entry.securityListValue();
        if (value == null) {
            throw invalidValue(ParseTreeText.getOriginalText(entry.securityPropertyList()), setting, key);
        }
        switch (key) {
            case ALLOWED_METHODS:
                return choices(setting, key, value, METHODS, true);
            case ENFORCE:
                return enforcement(setting, value);
            case DEFAULT_EXPIRY:
            case MAX_EXPIRY:
                return days(setting, key, value);
            case NETWORK_EVALUATION:
                return evaluation(value);
            case SERVICE_USERS:
            case PERSON_USERS:
                if (value.TRUE() != null || value.FALSE() != null) {
                    return Boolean.valueOf(value.TRUE() != null);
                }
                throw invalidValue(ParseTreeText.getOriginalText(value), setting, key);
            case BLOCKED_ROLES:
                return roles(setting, key, value, catalog);
            case PROVIDERS:
                return choices(setting, key, value, PROVIDER_NAMES, false);
            default:
                return checked(setting, key, value);
        }
    }

    /**
     * A list of choices, each upper-cased, judged and kept once, in order; one that holds ALL is ALL alone. An MFA
     * method list may not be empty.
     */
    private static List<String> choices(final String setting, final String key,
                                        final FrostlakeParser.SecurityListValueContext value, final List<String> names,
                                        final boolean nonEmpty) {
        final List<String> items = items(setting, key, value);
        if (items.isEmpty() && nonEmpty) {
            throw new RuntimeException(COMPILATION_ERROR + " " + setting + "." + key
                + " cannot be empty in order to enforce MFA.");
        }
        final TreeSet<String> chosen = new TreeSet<>();
        for (final String item : items) {
            final String upper = item.toUpperCase(Locale.ROOT);
            if (!names.contains(upper)) {
                throw new RuntimeException(COMPILATION_ERROR + " " + upper + " invalid for " + key + ". ");
            }
            chosen.add(upper);
        }
        return chosen.contains(ALL) ? all() : new ArrayList<String>(chosen);
    }

    /** A list of AWS accounts, partitions or issuers, each checked against the shape that property takes. */
    private static List<String> checked(final String setting, final String key,
                                        final FrostlakeParser.SecurityListValueContext value) {
        final TreeSet<String> kept = new TreeSet<>();
        for (final String item : items(setting, key, value)) {
            final boolean valid = AWS_ACCOUNTS.equals(key) ? AWS_ACCOUNT.matcher(item).matches()
                : AWS_PARTITIONS.equals(key) ? PARTITIONS.contains(item.toLowerCase(Locale.ROOT))
                : AZURE_ISSUERS.equals(key) ? AZURE_ISSUER.matcher(item).matches()
                : OIDC_ISSUER.matcher(item).matches();
            if (!valid) {
                throw new RuntimeException(COMPILATION_ERROR + " " + item + " invalid for " + key + ". ");
            }
            kept.add(item);
        }
        return new ArrayList<String>(kept);
    }

    /** A list's items as written: a quoted one read, a bare word as it stands; a number is no item of any. */
    private static List<String> items(final String setting, final String key,
                                      final FrostlakeParser.SecurityListValueContext value) {
        if (value.LPAREN() == null) {
            if (value.STRING_LITERAL() != null) {
                throw new RuntimeException(SqlCompilationError.of("invalid type [" + value.STRING_LITERAL().getText()
                    + "] for parameter '" + setting + "." + key + "'"));
            }
            throw invalidValue(ParseTreeText.getOriginalText(value), setting, key);
        }
        final List<String> items = new ArrayList<>();
        for (final FrostlakeParser.SecurityListItemContext item : value.securityListItem()) {
            if (item.STRING_LITERAL() != null) {
                items.add(SqlStringLiterals.decode(item.STRING_LITERAL().getText()));
            } else if (item.identifier() != null || item.ALL() != null) {
                items.add(item.getText());
            } else {
                throw invalidValue(item.getText(), setting, key);
            }
        }
        return items;
    }

    /** Whether MFA is enforced on external authentication: ALL or NONE, quoted or not, in any case. */
    private static String enforcement(final String setting, final FrostlakeParser.SecurityListValueContext value) {
        if (value.LPAREN() != null) {
            throw invalidValue("TOK_CONSTANT_LIST", setting, ENFORCE);
        }
        final String text = word(value);
        final String upper = text.toUpperCase(Locale.ROOT);
        if (!ALL.equals(upper) && !"NONE".equals(upper)) {
            throw new RuntimeException(COMPILATION_ERROR + " " + text + " invalid for " + ENFORCE + ". ");
        }
        return upper;
    }

    /** How a network policy is evaluated for a token: one of three words, in capitals, quoted or not. */
    private static String evaluation(final FrostlakeParser.SecurityListValueContext value) {
        final String text = value.LPAREN() != null ? ParseTreeText.getOriginalText(value) : word(value);
        if (!EVALUATIONS.contains(text)) {
            throw new RuntimeException(COMPILATION_ERROR + " The value of " + text + " is invalid for "
                + NETWORK_EVALUATION + ". ");
        }
        return text;
    }

    /** A value read as a word: a quoted one read, anything else as written. */
    private static String word(final FrostlakeParser.SecurityListValueContext value) {
        return value.STRING_LITERAL() != null ? SqlStringLiterals.decode(value.STRING_LITERAL().getText())
            : ParseTreeText.getOriginalText(value);
    }

    /** A count of days: a whole number written as one; NULL does not parse. */
    private static Long days(final String setting, final String key,
                             final FrostlakeParser.SecurityListValueContext value) {
        if (value.NULL() != null) {
            throw new RuntimeException(SqlCompilationError.of("Error parsing null"));
        }
        if (value.INTEGER_LITERAL() == null) {
            throw invalidValue(ParseTreeText.getOriginalText(value), setting, key);
        }
        try {
            return Long.valueOf(Long.parseLong(value.getText()));
        } catch (final NumberFormatException tooLong) {
            throw invalidValue(value.getText(), setting, key);
        }
    }

    /**
     * A token's default and greatest lifetimes: the default from one day to the greatest, the greatest from the default
     * to a year, the one a statement writes judged first.
     */
    private static void requireExpiry(final Map<String, Object> values, final boolean wroteDefault,
                                      final boolean wroteMax) {
        final long byDefault = ((Long) values.get(DEFAULT_EXPIRY)).longValue();
        final long max = ((Long) values.get(MAX_EXPIRY)).longValue();
        if (wroteDefault && (byDefault < 1 || byDefault > 365 || byDefault > max)) {
            throw new RuntimeException(COMPILATION_ERROR + " The value of " + byDefault + " is invalid for "
                + DEFAULT_EXPIRY + ". ");
        }
        if (wroteMax && (max < 1 || max > 365 || max < byDefault)) {
            throw new RuntimeException(COMPILATION_ERROR + " The value of " + max + " is invalid for " + MAX_EXPIRY
                + ". ");
        }
    }

    /** The roles a token may not be restricted to, each of the account's, the missing ones refused together. */
    private static List<String> roles(final String setting, final String key,
                                      final FrostlakeParser.SecurityListValueContext value, final Catalog catalog) {
        final List<String> roles = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (final String item : items(setting, key, value)) {
            if (catalog.roleExistsExact(item)) {
                roles.add(item);
            } else if (catalog.roleExistsExact(item.toUpperCase(Locale.ROOT))) {
                roles.add(item.toUpperCase(Locale.ROOT));
            } else {
                missing.add(item);
            }
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(COMPILATION_ERROR + " Role(s) " + String.join(", ", missing)
                + " do not exist invalid for " + key + ". ");
        }
        return roles;
    }

    /**
     * CLIENT_POLICY's drivers, each named as the account names it and given its least version: three numbers,
     * {@code '3.13.0'}.
     */
    private static LinkedHashMap<String, Object> drivers(final FrostlakeParser.SecurityPropertyListContext list) {
        final LinkedHashMap<String, Object> drivers = new LinkedHashMap<>();
        for (final FrostlakeParser.SecurityListEntryContext entry : list.securityListEntry()) {
            final String written = entry.optionKey().getText();
            final String driver = written.toUpperCase(Locale.ROOT);
            if (!DRIVERS.contains(written)) {
                throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + CLIENT_POLICY + "."
                    + driver + "'"));
            }
            if (drivers.containsKey(driver)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(driver));
            }
            if (entry.securityPropertyList() == null) {
                final FrostlakeParser.SecurityListValueContext value = entry.securityListValue();
                throw invalidValue(value.LPAREN() != null ? "TOK_CONSTANT_LIST" : ParseTreeText.getOriginalText(value),
                    CLIENT_POLICY, driver);
            }
            String version = null;
            for (final FrostlakeParser.SecurityListEntryContext inner
                    : entry.securityPropertyList().securityListEntry()) {
                final String key = inner.optionKey().getText().toUpperCase(Locale.ROOT);
                if (!MINIMUM_VERSION.equals(key)) {
                    throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + CLIENT_POLICY + "."
                        + driver + "." + key + "'"));
                }
                final FrostlakeParser.SecurityListValueContext value = inner.securityListValue();
                if (value == null || value.STRING_LITERAL() == null) {
                    throw invalidValue(ParseTreeText.getOriginalText(value != null ? value
                        : inner.securityPropertyList()), CLIENT_POLICY + "." + driver, key);
                }
                version = SqlStringLiterals.decode(value.STRING_LITERAL().getText());
                if (!VERSION.matcher(version).matches()) {
                    throw new RuntimeException(COMPILATION_ERROR + " {" + driver + "={" + MINIMUM_VERSION + "="
                        + version + "}} invalid for " + CLIENT_POLICY + ". ");
                }
            }
            drivers.put(driver, version);
        }
        return drivers;
    }

    private static RuntimeException invalidValue(final String written, final String setting, final String key) {
        return new RuntimeException(SqlCompilationError.of("invalid value [" + written + "] for parameter '" + setting
            + "." + key + "'"));
    }

    /**
     * A setting as DESCRIBE tells it:
     * {@code {ALLOWED_METHODS=[PASSKEY, TOTP], ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}}, the drivers of
     * CLIENT_POLICY by name, {@code {JDBC_DRIVER={MINIMUM_VERSION=3.13.0}}}.
     *
     * @param setting the setting
     * @param values  its properties
     * @return the text
     */
    static String describe(final String setting, final Map<String, Object> values) {
        final StringBuilder text = new StringBuilder("{");
        final Map<String, Object> told = CLIENT_POLICY.equals(setting) ? new TreeMap<String, Object>(values) : values;
        for (final Map.Entry<String, Object> property : told.entrySet()) {
            if (text.length() > 1) {
                text.append(", ");
            }
            text.append(property.getKey()).append('=');
            final Object value = property.getValue();
            if (CLIENT_POLICY.equals(setting)) {
                text.append('{').append(MINIMUM_VERSION).append('=').append(value).append('}');
            } else if (value instanceof List) {
                text.append('[').append(String.join(", ", strings(value))).append(']');
            } else {
                text.append(value);
            }
        }
        return text.append('}').toString();
    }

    /**
     * A setting as GET_DDL spells it: {@code (ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION='NONE',ALLOWED_METHODS=('ALL'))},
     * its properties in the order a hash map of their names keeps them, a list's items and the MFA enforcement
     * quoted, a driver's version in braces.
     *
     * @param setting the setting
     * @param values  its properties
     * @return the text
     */
    public static String ddl(final String setting, final Map<String, Object> values) {
        final Map<String, String> spelled = new HashMap<>();
        for (final Map.Entry<String, Object> property : values.entrySet()) {
            final Object value = property.getValue();
            final String text;
            if (CLIENT_POLICY.equals(setting)) {
                text = "{" + MINIMUM_VERSION + "=" + value + "}";
            } else if (value instanceof List) {
                final StringBuilder items = new StringBuilder("(");
                for (final String item : strings(value)) {
                    items.append(items.length() > 1 ? "," : "").append('\'').append(item).append('\'');
                }
                text = items.append(')').toString();
            } else if (ENFORCE.equals(property.getKey())) {
                text = "'" + value + "'";
            } else {
                text = String.valueOf(value);
            }
            spelled.put(property.getKey(), text);
        }
        final StringBuilder ddl = new StringBuilder("(");
        for (final Map.Entry<String, String> property : spelled.entrySet()) {
            ddl.append(ddl.length() > 1 ? "," : "").append(property.getKey()).append('=').append(property.getValue());
        }
        return ddl.append(')').toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object value) {
        return (List<String>) value;
    }
}
