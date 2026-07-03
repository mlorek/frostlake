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

package dev.frostlake.executor;

import org.antlr.v4.runtime.Token;

import java.util.regex.Pattern;

/**
 * Small predicates over lexed SQL tokens, shared by the lexer-driven substitution paths
 * ({@code BindVariableSubstitutor}, {@code MaskingPolicyApplier}, {@code JdbcMarshaling}).
 */
public final class SqlTokens {

    /**
     * The shape of a bare word — the same as the grammar's {@code IDENTIFIER} lexer rule. It matches
     * both {@code IDENTIFIER} tokens and keyword tokens (Snowflake lets most keywords stand in as
     * identifiers, so {@code :result} / a column named {@code value} must still be recognized), while
     * excluding string literals, quoted identifiers, numbers and operators. This classifies a single
     * token the lexer already produced; it does not re-scan the SQL.
     */
    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private SqlTokens() {
    }

    /** Whether {@code token} is a bare word (an identifier or a keyword usable as one). */
    public static boolean isWord(final Token token) {
        return WORD.matcher(token.getText()).matches();
    }
}
