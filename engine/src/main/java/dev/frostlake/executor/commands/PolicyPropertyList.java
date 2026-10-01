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
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The property list an ALTER of a policy sets or unsets — a masking, row access, projection, aggregation or join
 * policy alike — judged the way live judges it, before the policy itself is looked up (all live-verified):
 *
 * <pre>
 *   SET foo = 1, foo = 2              duplicate property 'foo';
 *   SET foo = 1 / UNSET "COMMENT"     invalid property 'foo' for 'MASKING_POLICY'
 *   SET COMMENT = 1                   invalid value [1] for parameter 'COMMENT'
 * </pre>
 *
 * <p>A repeat outranks an unknown name and an unknown name outranks a bad value, wherever each is written. A name
 * counts as written, so {@code COMMENT} and {@code comment} are two names that both set the comment, and a quoted
 * {@code "COMMENT"} is an unknown one. Of two unknown names live reports the one a hash map keyed by the names as
 * written yields first ({@code bar} before {@code foo}, {@code zeta} before {@code alpha}), so the names are gathered
 * into one here.
 *
 * <p>COMMENT is the only property a policy has. It takes a string, a name (an unquoted one upper-cased, a longer
 * path as written) or a hex literal as written, and NULL clears it as UNSET COMMENT does; a number, a boolean or a
 * parenthesised value is refused as an invalid value.
 */
final class PolicyPropertyList {

    private static final String COMMENT = "COMMENT";

    private PolicyPropertyList() {
    }

    /**
     * Refuse the SET or UNSET property list of an ALTER's policy action as live does; a RENAME, a new body and a
     * TAG list hold no property to judge.
     *
     * @param action the ALTER's policy action
     * @param kind the policy kind as the refusal names it, e.g. {@code MASKING_POLICY}
     */
    static void validate(final FrostlakeParser.PolicyActionContext action, final String kind) {
        final List<String> names = new ArrayList<>();
        final List<FrostlakeParser.PolicyPropertyValueContext> values = new ArrayList<>();
        if (!action.policyProperty().isEmpty()) {
            for (final FrostlakeParser.PolicyPropertyContext property : action.policyProperty()) {
                names.add(property.identifier().getText());
                values.add(property.policyPropertyValue());
            }
        } else if (action.UNSET() != null) {
            for (final FrostlakeParser.IdentifierContext name : action.identifier()) {
                names.add(name.getText());
            }
        }
        final Map<String, Boolean> byName = new HashMap<>();
        for (final String name : names) {
            if (byName.containsKey(name)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(name));
            }
            byName.put(name, Boolean.TRUE);
        }
        for (final String name : byName.keySet()) {
            if (!COMMENT.equals(name.toUpperCase(Locale.ROOT))) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(name, kind));
            }
        }
        for (final FrostlakeParser.PolicyPropertyValueContext value : values) {
            commentOf(value);
        }
    }

    /**
     * The comment a validated SET or UNSET property list leaves on the policy: the last COMMENT a SET list writes,
     * or null when the list clears it.
     *
     * @param action the ALTER's policy action, already {@link #validate validated}
     * @return the comment, or null for none
     */
    static String comment(final FrostlakeParser.PolicyActionContext action) {
        String comment = null;
        for (final FrostlakeParser.PolicyPropertyContext property : action.policyProperty()) {
            comment = commentOf(property.policyPropertyValue());
        }
        return comment;
    }

    /** The comment a COMMENT property's value spells, null for NULL, refusing a value no comment takes. */
    private static String commentOf(final FrostlakeParser.PolicyPropertyValueContext value) {
        final FrostlakeParser.PolicyScalarValueContext scalar = value.LPAREN() == null
            ? value.policyScalarValue(0) : null;
        if (scalar != null && scalar.literal() != null) {
            final FrostlakeParser.LiteralContext literal = scalar.literal();
            if (literal.STRING_LITERAL() != null) {
                return SqlStringLiterals.decode(literal.STRING_LITERAL().getText());
            }
            if (literal.DOLLAR_QUOTED_STRING() != null) {
                final String quoted = literal.DOLLAR_QUOTED_STRING().getText();
                return quoted.substring(2, quoted.length() - 2);
            }
            if (literal.NULL() != null) {
                return null;
            }
            if (literal.HEX_LITERAL() != null) {
                return literal.getText();
            }
        } else if (scalar != null && scalar.MINUS() == null) {
            final List<FrostlakeParser.IdentifierContext> parts = scalar.identifier();
            return parts.size() == 1 ? SqlIdentifiers.canonical(parts.get(0)) : scalar.getText();
        }
        final StringBuilder written = new StringBuilder();
        for (final FrostlakeParser.PolicyScalarValueContext each : value.policyScalarValue()) {
            written.append(written.length() > 0 ? ", " : "").append(each.getText());
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(written.toString(), COMMENT));
    }
}
