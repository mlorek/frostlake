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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Locale;

/**
 * NETWORK_POLICY among a user's properties: {@code ALTER USER u SET COMMENT = 'c' NETWORK_POLICY = np} attaches
 * the policy as the statement of its own does, and {@code UNSET NETWORK_POLICY, COMMENT} takes it off. A name
 * written bare folds as an identifier does and a string is that name exactly; the policy has to exist before any
 * property of the list is applied (live-verified forms).
 */
final class UserNetworkPolicy {

    /** The property's name, as an UNSET list writes it. */
    static final String PROPERTY = "NETWORK_POLICY";

    private UserNetworkPolicy() {
    }

    /**
     * The check a NETWORK_POLICY property gets as its statement compiles, before the user is looked up: a number
     * and a BOOLEAN name no policy — {@code invalid value [5] for parameter 'NETWORK_POLICY'}.
     *
     * @param property the property
     */
    static void checkValue(final FrostlakeParser.UserPropertyContext property) {
        final Token word = property.identifier() == null ? null : property.identifier().getStart();
        final boolean flag = word != null
            && (word.getType() == FrostlakeParser.TRUE || word.getType() == FrostlakeParser.FALSE);
        if (property.INTEGER_LITERAL() != null || flag) {
            throw new RuntimeException(SqlCompilationError.of("invalid value ["
                + (flag ? word.getText().toUpperCase(Locale.ROOT) : property.INTEGER_LITERAL().getText())
                + "] for parameter 'NETWORK_POLICY'"));
        }
    }

    /**
     * Whether a property list takes the user's policy off: {@code NETWORK_POLICY = NULL} names none.
     *
     * @param properties the statement's properties
     * @return whether the list sets the policy to NULL
     */
    static boolean clears(final List<FrostlakeParser.UserPropertyContext> properties) {
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.NETWORK_POLICY() != null && property.NULL() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The policy a property list names, which has to exist.
     *
     * @param catalog the catalog
     * @param properties the statement's properties
     * @return the policy's name as the account holds it, or null when the list names none
     */
    static String named(final Catalog catalog, final List<FrostlakeParser.UserPropertyContext> properties) {
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.NETWORK_POLICY() == null || property.NULL() != null) {
                continue;
            }
            final boolean exact = property.identifier() == null;
            final String policy = exact ? SqlStringLiterals.decode(property.STRING_LITERAL().getText())
                : SqlIdentifiers.canonical(property.identifier());
            final SecurityObject found = catalog.getSecurityObjects().get(SecurityObjectKind.NETWORK_POLICY, policy);
            if (found == null || exact && !policy.equals(found.getName())) {
                throw new RuntimeException("Network policy " + policy + " does not exist or not authorized.");
            }
            return found.getName();
        }
        return null;
    }

    /**
     * Attaches a policy to a user.
     *
     * @param catalog the catalog
     * @param user the user
     * @param policy the policy {@link #named} found, or null for none
     */
    static void attach(final Catalog catalog, final User user, final String policy) {
        if (policy != null) {
            catalog.getSecurityObjects().attach(SecurityObjectCommandHandler.userNetworkPolicyKey(user.getName()),
                policy);
        }
    }

    /**
     * Takes the policy off a user.
     *
     * @param catalog the catalog
     * @param user the user
     */
    static void detach(final Catalog catalog, final User user) {
        catalog.getSecurityObjects().detach(SecurityObjectCommandHandler.userNetworkPolicyKey(user.getName()));
    }

    /**
     * Takes the policy off a user when an UNSET list names the property.
     *
     * @param catalog the catalog
     * @param user the user
     * @param properties the UNSET list
     */
    static void detach(final Catalog catalog, final User user,
                       final List<FrostlakeParser.UserUnsetPropertyContext> properties) {
        for (final FrostlakeParser.UserUnsetPropertyContext property : properties) {
            if (PROPERTY.equalsIgnoreCase(property.getText())) {
                detach(catalog, user);
            }
        }
    }
}
