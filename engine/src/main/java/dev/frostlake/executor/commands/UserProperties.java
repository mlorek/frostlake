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
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
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
        "DEFAULT_SECONDARY_ROLES", "MUST_CHANGE_PASSWORD", "DISABLED", "COMMENT", "TYPE");

    private UserProperties() {
    }

    /** Apply every property in the list to {@code user}, leaving unnamed properties untouched. */
    static void apply(final User user, final List<FrostlakeParser.UserPropertyContext> properties) {
        rejectPersonOnlyProperties(user, properties);
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.PASSWORD() != null) {
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
        for (final String name : names) {
            applyUnset(user, name);
        }
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
            final User user, final List<FrostlakeParser.UserPropertyContext> properties) {
        String userType = user.getUserType();
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
     * EMAIL, DISPLAY_NAME,     ok      ok          ok
     * DEFAULT_WAREHOUSE, COMMENT
     * </pre>
     *
     * <p>A legacy service user keeping its password is the whole point of the type, so treating the
     * two as one set would refuse a statement Snowflake accepts.
     */
    private static String refusedPropertyName(final FrostlakeParser.UserPropertyContext property,
                                              final boolean service) {
        if (property.FIRST_NAME() != null) {
            return "FIRST_NAME";
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
