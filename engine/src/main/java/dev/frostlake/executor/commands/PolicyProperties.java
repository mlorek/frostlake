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
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;

/**
 * A policy's properties after its body: its COMMENT and, for a masking or a row access policy, EXEMPT_OTHER_POLICIES
 * set TRUE or FALSE, in any case. A property written twice is refused as {@code duplicate property 'COMMENT';},
 * another value as {@code invalid value [YES] for parameter 'EXEMPT_OTHER_POLICIES'}, the value as written, and
 * EXEMPT_OTHER_POLICIES on a kind that takes none as {@code invalid property 'EXEMPT_OTHER_POLICIES' for
 * 'PROJECTION_POLICY'} (live-verified).
 */
final class PolicyProperties {

    private static final String EXEMPT = "EXEMPT_OTHER_POLICIES";

    private final String comment;
    private final boolean exempt;

    private PolicyProperties(final String comment, final boolean exempt) {
        this.comment = comment;
        this.exempt = exempt;
    }

    /**
     * Reads a policy's properties, refusing what the account refuses, in the order they are written.
     *
     * @param ddl            the handler that reads a COMMENT's text
     * @param properties     the properties as written
     * @param kind           the policy's kind as the account names it, MASKING_POLICY or PROJECTION_POLICY
     * @param takesExemption whether the kind takes EXEMPT_OTHER_POLICIES
     * @return the properties
     */
    static PolicyProperties read(final DDLCommandHandler ddl,
                                 final List<FrostlakeParser.PolicyCreatePropertyContext> properties, final String kind,
                                 final boolean takesExemption) {
        String comment = null;
        boolean commented = false;
        boolean exempt = false;
        boolean exemptSet = false;
        for (final FrostlakeParser.PolicyCreatePropertyContext property : properties) {
            if (property.commentClause() != null) {
                if (commented) {
                    throw duplicate("COMMENT");
                }
                commented = true;
                comment = ddl.extractComment(property.commentClause());
                continue;
            }
            if (!takesExemption) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property '" + EXEMPT + "' for '" + kind + "'"));
            }
            if (exemptSet) {
                throw duplicate(EXEMPT);
            }
            exemptSet = true;
            final FrostlakeParser.PolicyFlagValueContext value = property.policyFlagValue();
            if (value.TRUE() == null && value.FALSE() == null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid value [" + value.getText() + "] for parameter '" + EXEMPT + "'"));
            }
            exempt = value.TRUE() != null;
        }
        return new PolicyProperties(comment, exempt);
    }

    private static RuntimeException duplicate(final String property) {
        return new RuntimeException(SqlCompilationError.of("duplicate property '" + property + "';"));
    }

    /** The COMMENT, or null when none is written. */
    String comment() {
        return comment;
    }

    /** Whether EXEMPT_OTHER_POLICIES is set TRUE. */
    boolean exempt() {
        return exempt;
    }
}
