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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.User;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What an ALTER USER … SET writes beside the properties in their own form (live-verified): the session parameters,
 * which stand among the properties with or without commas, and a property given a word or NULL — which the text
 * properties take — or a value of a kind it does not take. The list is judged whole before the user is looked up:
 *
 * <ol>
 *   <li>a name written twice, property or parameter: {@code duplicate property '<name>';};</li>
 *   <li>a name that is neither: {@code invalid property '<name as written>' for 'USER'};</li>
 *   <li>a value of the wrong kind, the parameters before the properties — NUMBER, STRING, then BOOLEAN ones, each
 *       in the order of the names' text, the network policy among the STRING ones — and the properties in the
 *       order the account keeps them: {@code invalid value [<as written>] for parameter '<NAME>'}.</li>
 * </ol>
 *
 * <p>A value of the right kind that its parameter does not take — a number out of range, an unknown time zone — is
 * refused only once the user is found, so an ALTER USER IF EXISTS that misses its user answers without it.
 */
final class UserParameterList {

    /** The properties in the order the account judges their values in. */
    private static final List<String> PROPERTY_ORDER = Arrays.asList("PASSWORD", "LOGIN_NAME", "DISPLAY_NAME",
        "FIRST_NAME", "MIDDLE_NAME", "LAST_NAME", "EMAIL", "MUST_CHANGE_PASSWORD", "DISABLED", "DEFAULT_WAREHOUSE",
        "DEFAULT_NAMESPACE", "DEFAULT_ROLE", "DEFAULT_SECONDARY_ROLES", "TYPE", "COMMENT");

    /** The properties that take text, and so a word. */
    private static final List<String> TEXT_PROPERTIES = Arrays.asList("PASSWORD", "LOGIN_NAME", "DISPLAY_NAME",
        "FIRST_NAME", "MIDDLE_NAME", "LAST_NAME", "EMAIL", "DEFAULT_WAREHOUSE", "DEFAULT_NAMESPACE", "DEFAULT_ROLE",
        "TYPE", "COMMENT");

    private static final String LEVEL = "USER";

    private final List<FrostlakeParser.UserPropertyContext> properties;
    private final List<FrostlakeParser.UserPropertyAssignmentContext> propertyAssignments;
    private final List<SessionParameterSetting> parameters;
    private final List<SessionParameterValue> propertyValues = new ArrayList<SessionParameterValue>();

    /**
     * @param action the SET action of the statement
     */
    UserParameterList(final FrostlakeParser.UserActionContext action) {
        this.properties = action.userProperty();
        this.propertyAssignments = action.userPropertyAssignment();
        this.parameters = LeveledParameterSettings.assignments(action.sessionAssignment());
    }

    /** Whether the action writes anything this list judges. */
    static boolean reads(final FrostlakeParser.UserActionContext action) {
        return !action.userPropertyAssignment().isEmpty() || !action.sessionAssignment().isEmpty();
    }

    /**
     * The checks made as the list compiles, before the user is looked up.
     *
     * @param queryExecutor the executor, for the session's variables
     */
    void compile(final QueryExecutor queryExecutor) {
        requireNoDuplicates();
        LeveledParameterSettings.requireNames(parameters, LEVEL);
        LeveledParameterSettings.requireKind(parameters, "NUMBER", queryExecutor);
        requireTextKinds(queryExecutor);
        LeveledParameterSettings.requireKind(parameters, "BOOLEAN", queryExecutor);
        requirePropertyKinds(queryExecutor);
    }

    /**
     * Refuses a parameter's value that is of the right kind and still not one the parameter takes.
     *
     * @param catalog the catalog, for the schemas a SEARCH_PATH names
     */
    void requireTaken(final Catalog catalog) {
        LeveledParameterSettings.requireTaken(parameters, catalog);
    }

    /**
     * Writes the list's parameters and its properties given as a word or NULL to the user.
     *
     * @param user the user
     */
    void apply(final User user) {
        for (int i = 0; i < propertyAssignments.size(); i++) {
            final String name = nameOf(propertyAssignments.get(i));
            final SessionParameterValue value = propertyValues.get(i);
            if (value.kind() == SessionValueKind.NULL) {
                UserProperties.applyUnset(user, name);
            } else {
                UserProperties.applyWord(user, name, value.text());
            }
        }
        LeveledParameterSettings.set(user.getParameters(), parameters);
    }

    /** A name written twice among the properties and the parameters, whichever form each is written in. */
    private void requireNoDuplicates() {
        final List<String> names = new ArrayList<String>();
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            names.add(property.getStart().getText().toUpperCase(Locale.ROOT));
        }
        for (final FrostlakeParser.UserPropertyAssignmentContext assignment : propertyAssignments) {
            names.add(nameOf(assignment));
        }
        for (final SessionParameterSetting parameter : parameters) {
            names.add(parameter.written());
        }
        Collections.sort(names);
        for (int i = 1; i < names.size(); i++) {
            if (names.get(i).equals(names.get(i - 1))) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(names.get(i)));
            }
        }
    }

    /** The STRING parameters, the network policy judged where its name stands among theirs. */
    private void requireTextKinds(final QueryExecutor queryExecutor) {
        FrostlakeParser.UserPropertyContext policy = null;
        for (final FrostlakeParser.UserPropertyContext property : properties) {
            if (property.NETWORK_POLICY() != null) {
                policy = property;
            }
        }
        for (final SessionParameterSetting parameter : parameters) {
            if (!LeveledParameterSettings.isOfKind(parameter, "STRING")) {
                continue;
            }
            if (policy != null && parameter.ordering().compareTo(UserNetworkPolicy.PROPERTY) > 0) {
                UserNetworkPolicy.checkValue(policy);
                policy = null;
            }
            LeveledParameterSettings.judge(parameter, queryExecutor);
        }
        if (policy != null) {
            UserNetworkPolicy.checkValue(policy);
        }
    }

    /** The properties written as an assignment: a text property takes a word or NULL, any property NULL. */
    private void requirePropertyKinds(final QueryExecutor queryExecutor) {
        for (int i = 0; i < propertyAssignments.size(); i++) {
            propertyValues.add(null);
        }
        for (final String ordered : PROPERTY_ORDER) {
            for (int i = 0; i < propertyAssignments.size(); i++) {
                final String name = nameOf(propertyAssignments.get(i));
                if (!ordered.equals(name)) {
                    continue;
                }
                final SessionParameterValue value = SessionParameterValue.read(
                    propertyAssignments.get(i).sessionValue(), queryExecutor);
                final boolean taken = value.kind() == SessionValueKind.NULL
                    || value.kind() == SessionValueKind.TEXT && TEXT_PROPERTIES.contains(name);
                if (!taken) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.written(), name));
                }
                propertyValues.set(i, value);
            }
        }
    }

    private static String nameOf(final FrostlakeParser.UserPropertyAssignmentContext assignment) {
        return assignment.userPropertyName().getText().toUpperCase(Locale.ROOT);
    }
}
