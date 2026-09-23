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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies the properties of a CREATE USER or ALTER USER … SET to a {@link User}. Both statements
 * accept the same property list, so both route through here.
 */
final class UserProperties {

    /** The user type that carries no personal details. */
    private static final String SERVICE_USER_TYPE = "SERVICE";
    private static final String LEGACY_SERVICE_USER_TYPE = "LEGACY_SERVICE";

    /** The type a user has when none was asked for, and the one UNSET TYPE goes back to. */
    private static final String DEFAULT_USER_TYPE = "PERSON";

    /** Every value TYPE accepts. NULL is one of them, and clears the type. */
    private static final Set<String> USER_TYPES =
        Set.of(DEFAULT_USER_TYPE, SERVICE_USER_TYPE, "LEGACY_SERVICE", "NULL");

    /** Every property UNSET takes — the same set CREATE USER accepts. */
    private static final Set<String> UNSETTABLE = Set.of(
        "PASSWORD", "LOGIN_NAME", "DISPLAY_NAME", "FIRST_NAME", "MIDDLE_NAME", "LAST_NAME",
        "EMAIL", "DEFAULT_ROLE", "DEFAULT_WAREHOUSE", "DEFAULT_NAMESPACE",
        "DEFAULT_SECONDARY_ROLES", "MUST_CHANGE_PASSWORD", "DISABLED", "COMMENT", "TYPE",
        "DAYS_TO_EXPIRY", "MINS_TO_UNLOCK", "MINS_TO_BYPASS_MFA", "RSA_PUBLIC_KEY", "RSA_PUBLIC_KEY_2");

    /** The largest DAYS_TO_EXPIRY: a thousand years. */
    private static final long MAX_DAYS_TO_EXPIRY = 365_000L;
    /** The largest MINS_TO_UNLOCK: a thousand years in minutes. */
    private static final long MAX_MINS_TO_UNLOCK = 525_600_000L;
    /** The largest MINS_TO_BYPASS_MFA: one day. */
    private static final long MAX_MINS_TO_BYPASS_MFA = 1_440L;

    private UserProperties() {
    }

    /**
     * The checks an account makes as it compiles a property list, before it looks the user up: a property named
     * twice ({@code duplicate property 'EMAIL';}) and a value the property cannot take ({@code invalid value [1.5]
     * for parameter 'DAYS_TO_EXPIRY'}). They hold whether or not the user exists, so a CREATE USER IF NOT EXISTS that
     * finds its user and an ALTER USER IF EXISTS that misses it still make them.
     *
     * @param properties the statement's properties
     */
    static void checkForm(final List<FrostlakeParser.UserPropertyContext> properties) {
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            final String name = property.getStart().getText().toUpperCase(Locale.ROOT);
            if (names.contains(name)) {
                throw new RuntimeException(SqlCompilationError.of("duplicate property '" + name + "';"));
            }
            names.add(name);
        }
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (isCountdown(property)) {
                countdownValue(property);
            } else if (isKey(property)) {
                keyValue(property);
            }
        }
    }

    /**
     * The checks an account makes once it knows the user a property list is for, in its order: a property the
     * user's type may not carry; a value out of the property's range ({@code invalid value '1,441' for property …})
     * or an unknown TYPE; and last a public key the key policy turns down. A CREATE makes them before it changes
     * anything, so a refused CREATE leaves no user behind and a refused CREATE OR REPLACE leaves the old one
     * standing.
     *
     * @param userType the type the user has before the statement: PERSON for a new one
     * @param properties the statement's properties
     */
    private static void checkFor(final String userType, final List<FrostlakeParser.UserPropertyContext> properties) {
        rejectPersonOnlyProperties(userType, properties);
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.TYPE() != null) {
                validatedUserType(nameOrLiteral(property));
            } else if (isCountdown(property)) {
                final Long value = countdownValue(property);
                final long max = property.DAYS_TO_EXPIRY() != null ? MAX_DAYS_TO_EXPIRY
                    : property.MINS_TO_UNLOCK() != null ? MAX_MINS_TO_UNLOCK : MAX_MINS_TO_BYPASS_MFA;
                if (value != null && value.longValue() > max) {
                    throw new RuntimeException(SqlCompilationError.of("invalid value '"
                        + String.format(Locale.US, "%,d", value) + "' for property '"
                        + property.getStart().getText().toUpperCase(Locale.ROOT) + "'"));
                }
            }
        }
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (isKey(property)) {
                fingerprintOf(keyValue(property));
            }
        }
    }

    /** The checks {@link #apply} makes once the user is known, for a CREATE USER to make before it creates one. */
    static void checkNew(final List<FrostlakeParser.UserPropertyContext> properties) {
        checkFor(DEFAULT_USER_TYPE, properties);
    }

    /** Apply every property in the list to {@code user}, leaving unnamed properties untouched. */
    static void apply(final User user, final List<FrostlakeParser.UserPropertyContext> properties) {
        checkForm(properties);
        checkFor(user.getUserType(), properties);
        final Instant now = StatementClock.instant();
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.DAYS_TO_EXPIRY() != null) {
                final Long days = countdownValue(property);
                user.setExpiresAt(days == null || days.longValue() == 0L ? null
                    : now.plus(days.longValue(), ChronoUnit.DAYS));
            } else if (property.MINS_TO_UNLOCK() != null) {
                user.setLockedUntil(countdownEnd(now, countdownValue(property)));
            } else if (property.MINS_TO_BYPASS_MFA() != null) {
                user.setMfaBypassUntil(countdownEnd(now, countdownValue(property)));
            } else if (property.RSA_PUBLIC_KEY() != null) {
                final String key = keyValue(property);
                user.setRsaPublicKey(key, fingerprintOf(key), setTimeOf(key, now));
            } else if (property.RSA_PUBLIC_KEY_2() != null) {
                final String key = keyValue(property);
                user.setRsaPublicKey2(key, fingerprintOf(key), setTimeOf(key, now));
            } else if (property.PASSWORD() != null) {
                user.setPassword(text(property.STRING_LITERAL()));
            } else if (property.DEFAULT_ROLE() != null) {
                user.setDefaultRole(nameOrLiteral(property));
            } else if (property.DEFAULT_WAREHOUSE() != null) {
                user.setDefaultWarehouse(nameOrLiteral(property));
            } else if (property.DEFAULT_NAMESPACE() != null) {
                // Upper-cased however it is written, quoted or not — like LOGIN_NAME, and unlike
                // DEFAULT_WAREHOUSE and DEFAULT_ROLE beside it, which keep the case they were given.
                user.setDefaultNamespace((property.qualifiedName() != null
                    ? property.qualifiedName().getText()
                    : text(property.STRING_LITERAL())).toUpperCase());
            } else if (property.DEFAULT_SECONDARY_ROLES() != null) {
                user.setDefaultSecondaryRoles(rolesJson(property));
            } else if (property.LOGIN_NAME() != null) {
                // A login name is upper-cased however it is written — `LOGIN_NAME = 'ln'` stores LN.
                // DISPLAY_NAME beside it keeps the case it was given.
                user.setLoginName(nameOrLiteral(property).toUpperCase());
            } else if (property.DISPLAY_NAME() != null) {
                user.setDisplayName(nameOrLiteral(property));
            } else if (property.FIRST_NAME() != null) {
                user.setFirstName(text(property.STRING_LITERAL()));
            } else if (property.MIDDLE_NAME() != null) {
                user.setMiddleName(text(property.STRING_LITERAL()));
            } else if (property.LAST_NAME() != null) {
                user.setLastName(text(property.STRING_LITERAL()));
            } else if (property.EMAIL() != null) {
                user.setEmail(text(property.STRING_LITERAL()));
            } else if (property.MUST_CHANGE_PASSWORD() != null) {
                user.setMustChangePassword(isTrue(property));
            } else if (property.DISABLED() != null) {
                user.setEnabled(!isTrue(property));
            } else if (property.TYPE() != null) {
                user.setUserType(validatedUserType(nameOrLiteral(property)));
            } else if (property.COMMENT() != null) {
                user.setComment(text(property.STRING_LITERAL()));
            }
        }
    }

    private static boolean isCountdown(final FrostlakeParser.UserPropertyContext property) {
        return property.DAYS_TO_EXPIRY() != null || property.MINS_TO_UNLOCK() != null
            || property.MINS_TO_BYPASS_MFA() != null;
    }

    private static boolean isKey(final FrostlakeParser.UserPropertyContext property) {
        return property.RSA_PUBLIC_KEY() != null || property.RSA_PUBLIC_KEY_2() != null;
    }

    /**
     * A countdown's value: a whole number that fits in 32 bits, negative or not, or null for NULL. Anything else —
     * a decimal, a string, a word — is {@code invalid value [<as written>] for parameter '<name>'}.
     */
    private static Long countdownValue(final FrostlakeParser.UserPropertyContext property) {
        final FrostlakeParser.UserPropertyValueContext value = property.userPropertyValue();
        if (value.NULL() != null) {
            return null;
        }
        if (value.INTEGER_LITERAL() != null) {
            BigInteger number = new BigInteger(value.INTEGER_LITERAL().getText());
            if (value.MINUS() != null) {
                number = number.negate();
            }
            if (number.bitLength() < Integer.SIZE) {
                return Long.valueOf(number.longValue());
            }
        }
        throw invalidParameter(property);
    }

    /**
     * A public key's value: its text, quoted either way, the text of a word, or null for NULL; a number or a boolean
     * is refused.
     */
    private static String keyValue(final FrostlakeParser.UserPropertyContext property) {
        final FrostlakeParser.UserPropertyValueContext value = property.userPropertyValue();
        if (value.NULL() != null) {
            return null;
        }
        if (value.STRING_LITERAL() != null) {
            return text(value.STRING_LITERAL());
        }
        if (value.DOLLAR_QUOTED_STRING() != null) {
            final String token = value.DOLLAR_QUOTED_STRING().getText();
            return token.substring(2, token.length() - 2);
        }
        if (value.identifier() != null) {
            return SqlIdentifiers.canonical(value.identifier());
        }
        throw invalidParameter(property);
    }

    private static RuntimeException invalidParameter(final FrostlakeParser.UserPropertyContext property) {
        return new RuntimeException(SqlCompilationError.of("invalid value [" + property.userPropertyValue().getText()
            + "] for parameter '" + property.getStart().getText().toUpperCase(Locale.ROOT) + "'"));
    }

    /** When a lock or a bypass of {@code minutes} ends; zero, a negative count and NULL all clear it. */
    private static Instant countdownEnd(final Instant now, final Long minutes) {
        return minutes == null || minutes.longValue() <= 0L ? null
            : now.plus(minutes.longValue(), ChronoUnit.MINUTES);
    }

    /** A key's fingerprint, or null when the slot holds no key (NULL or the empty text). */
    private static String fingerprintOf(final String key) {
        return key == null || key.isEmpty() ? null : UserPublicKeys.fingerprint(key);
    }

    /** The moment a key is put in its slot; clearing the slot, or setting it empty, keeps the recorded one. */
    private static Instant setTimeOf(final String key, final Instant now) {
        return key == null || key.isEmpty() ? null : now;
    }

    /**
     * ALTER USER … UNSET p [, p …]. Every property CREATE takes can be unset, and what unsetting
     * MEANS is per property, live-verified: most go back to nothing, LOGIN_NAME reverts to the
     * user's own name, the two booleans go false, TYPE goes back to PERSON, and
     * DEFAULT_SECONDARY_ROLES is left alone — a real account keeps its {@code ["ALL"]} there.
     *
     * <p>Note the asymmetry on DISPLAY_NAME: a user created without one shows their NAME, but
     * unsetting one leaves nothing behind rather than restoring that default.
     *
     * <p>The whole list is validated before any of it is applied, so a statement naming an unknown
     * or repeated property changes nothing.
     */
    static void unset(final User user,
            final List<FrostlakeParser.UserUnsetPropertyContext> properties) {
        for (final String name : unsetNames(properties)) {
            applyUnset(user, name);
        }
    }

    /**
     * The checks an account makes as it compiles an UNSET list, before it looks the user up: a property users do not
     * have ({@code invalid property 'X' for 'USER'}) and a property named twice.
     *
     * @param properties the statement's properties
     */
    static void checkUnset(final List<FrostlakeParser.UserUnsetPropertyContext> properties) {
        unsetNames(properties);
    }

    private static List<String> unsetNames(final List<FrostlakeParser.UserUnsetPropertyContext> properties) {
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.UserUnsetPropertyContext property : properties) {
            final String name = property.getText().toUpperCase();
            if (!UNSETTABLE.contains(name)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property '" + name + "' for 'USER'"));
            }
            if (names.contains(name)) {
                // The trailing semicolon is a real account's, not a typo.
                throw new RuntimeException(SqlCompilationError.of(
                    "duplicate property '" + name + "';"));
            }
            names.add(name);
        }
        return names;
    }

    private static void applyUnset(final User user, final String name) {
        if ("PASSWORD".equals(name)) {
            user.setPassword(null);
        } else if ("LOGIN_NAME".equals(name)) {
            user.setLoginName(user.getName());
        } else if ("DISPLAY_NAME".equals(name)) {
            user.setDisplayName(null);
        } else if ("FIRST_NAME".equals(name)) {
            user.setFirstName(null);
        } else if ("MIDDLE_NAME".equals(name)) {
            user.setMiddleName(null);
        } else if ("LAST_NAME".equals(name)) {
            user.setLastName(null);
        } else if ("EMAIL".equals(name)) {
            user.setEmail(null);
        } else if ("DEFAULT_ROLE".equals(name)) {
            user.setDefaultRole(null);
        } else if ("DEFAULT_WAREHOUSE".equals(name)) {
            user.setDefaultWarehouse(null);
        } else if ("DEFAULT_NAMESPACE".equals(name)) {
            user.setDefaultNamespace(null);
        } else if ("MUST_CHANGE_PASSWORD".equals(name)) {
            user.setMustChangePassword(false);
        } else if ("DISABLED".equals(name)) {
            user.setEnabled(true);
        } else if ("COMMENT".equals(name)) {
            user.setComment(null);
        } else if ("TYPE".equals(name)) {
            user.setUserType(DEFAULT_USER_TYPE);
        } else if ("DAYS_TO_EXPIRY".equals(name)) {
            user.setExpiresAt(null);
        } else if ("MINS_TO_UNLOCK".equals(name)) {
            user.setLockedUntil(null);
        } else if ("MINS_TO_BYPASS_MFA".equals(name)) {
            user.setMfaBypassUntil(null);
        } else if ("RSA_PUBLIC_KEY".equals(name)) {
            user.setRsaPublicKey(null, null, null);
        } else if ("RSA_PUBLIC_KEY_2".equals(name)) {
            user.setRsaPublicKey2(null, null, null);
        }
        // DEFAULT_SECONDARY_ROLES is accepted and deliberately left as it is.
    }

    /**
     * TYPE takes one of a closed set of values, quoted or not, and anything else is refused at CREATE
     * and ALTER alike. The message names the value WITHOUT its quotes, so {@code TYPE = 'ROBOT'} and
     * {@code TYPE = ROBOT} read identically.
     */
    private static String validatedUserType(final String value) {
        final String userType = value == null ? null : value.toUpperCase();
        if (!USER_TYPES.contains(userType)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value '" + value + "' for property 'TYPE'"));
        }
        return userType;
    }

    /**
     * A service user carries no personal details, so Snowflake refuses the properties that describe
     * a person. The check is on the ASSIGNMENT, not on the resulting state: setting TYPE = SERVICE on
     * a user who already has a first name is allowed, and only a statement that tries to set one of
     * these alongside — or on — a service user is rejected.
     *
     * <p>The type in force is whichever this statement sets, else the one the user already has, so
     * CREATE USER is covered whichever order the properties are written in.
     */
    private static void rejectPersonOnlyProperties(
            final String currentType, final List<FrostlakeParser.UserPropertyContext> properties) {
        String userType = currentType;
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.TYPE() != null) {
                userType = nameOrLiteral(property).toUpperCase();
            }
        }
        final boolean service = SERVICE_USER_TYPE.equals(userType);
        final boolean legacyService = LEGACY_SERVICE_USER_TYPE.equals(userType);
        if (!service && !legacyService) {
            return;
        }
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            final String rejected = refusedPropertyName(property, service);
            if (rejected != null) {
                throw new RuntimeException("SQL execution error: Cannot set " + rejected
                    + " on users with TYPE=" + userType + ".");
            }
        }
    }

    /**
     * The property's name when this user type may not carry it, else null.
     *
     * <p>The two service types refuse DIFFERENT sets, live-verified against both:
     *
     * <pre>
     *                        PERSON  SERVICE  LEGACY_SERVICE
     * FIRST/MIDDLE/LAST_NAME   ok    refused     refused
     * PASSWORD                 ok    refused       ok
     * MUST_CHANGE_PASSWORD     ok    refused       ok
     * MINS_TO_BYPASS_MFA       ok    refused     refused
     * EMAIL, DISPLAY_NAME,     ok      ok          ok
     * DEFAULT_WAREHOUSE, COMMENT, DAYS_TO_EXPIRY,
     * MINS_TO_UNLOCK, RSA_PUBLIC_KEY
     * </pre>
     *
     * <p>A legacy service user keeping its password is the whole point of the type, so treating the
     * two as one set would refuse a statement Snowflake accepts. MINS_TO_BYPASS_MFA is refused whatever
     * its value — zero, negative or out of range — except NULL, which sets nothing.
     */
    private static String refusedPropertyName(final FrostlakeParser.UserPropertyContext property,
                                              final boolean service) {
        if (property.FIRST_NAME() != null) {
            return "FIRST_NAME";
        }
        if (property.MINS_TO_BYPASS_MFA() != null && property.userPropertyValue().NULL() == null) {
            return "MINS_TO_BYPASS_MFA";
        }
        if (property.MIDDLE_NAME() != null) {
            return "MIDDLE_NAME";
        }
        if (property.LAST_NAME() != null) {
            return "LAST_NAME";
        }
        if (service && property.PASSWORD() != null) {
            return "PASSWORD";
        }
        if (service && property.MUST_CHANGE_PASSWORD() != null) {
            return "MUST_CHANGE_PASSWORD";
        }
        return null;
    }

    /** DEFAULT_SECONDARY_ROLES = ('ALL') as the JSON array live reports it as. */
    private static String rolesJson(final FrostlakeParser.UserPropertyContext property) {
        final StringBuilder json = new StringBuilder("[");
        if (property.stringLiteralList() != null) {
            for (final TerminalNode role : property.stringLiteralList().STRING_LITERAL()) {
                if (json.length() > 1) {
                    json.append(',');
                }
                json.append('"').append(text(role).toUpperCase()).append('"');
            }
        }
        return json.append(']').toString();
    }

    /** A property written either bare or quoted; a bare one folds the way every identifier does. */
    private static String nameOrLiteral(final FrostlakeParser.UserPropertyContext property) {
        if (property.identifier() != null) {
            return SqlIdentifiers.canonical(property.identifier());
        }
        return text(property.STRING_LITERAL());
    }

    private static boolean isTrue(final FrostlakeParser.UserPropertyContext property) {
        return property.booleanValue() != null
            && "TRUE".equalsIgnoreCase(property.booleanValue().getText());
    }

    private static String text(final TerminalNode literal) {
        return literal == null ? null : SqlStringLiterals.decode(literal.getText());
    }
}
