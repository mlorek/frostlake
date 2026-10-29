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
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.types.DecimalText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

/**
 * The value one ALTER SESSION SET assignment writes, read by its form alone (live-verified):
 *
 * <ul>
 *   <li>a whole number, a minus sign before it or apart from it ({@code -5}, {@code - 5}); leading zeros are
 *       digits like any other;</li>
 *   <li>a number with a point or an exponent, which no parameter takes;</li>
 *   <li>text — a string, a dollar-quoted string, a hexadecimal literal kept as written ({@code x'41'} is the
 *       five characters), or a word: an unquoted word upper-folds ({@code abc} is {@code ABC}), a quoted one
 *       keeps its content, a name of several parts stays as written ({@code a.B}), and the keywords listed in
 *       {@link #SPELLED_AS_WRITTEN} keep their spelling ({@code timestamp_ltz}, {@code auto});</li>
 *   <li>TRUE or FALSE in any case, and NULL;</li>
 *   <li>a session variable, read as the value it holds — a variable no SET defined is refused.</li>
 * </ul>
 *
 * <p>{@link #written()} is what a refusal of the value's KIND echoes: the value as the statement spells it, a
 * string with its quotes and a number with its sign joined to it. A variable echoes the value it holds.
 */
final class SessionParameterValue {

    /** The words that keep the spelling they were written in rather than upper-folding. */
    private static final String[] SPELLED_AS_WRITTEN = {"TIMESTAMP_NTZ", "TIMESTAMP_LTZ", "TIMESTAMP_TZ", "AUTO",
        "FAIL", "INFO"};

    private final SessionValueKind kind;
    private final String written;
    private final BigInteger integer;
    private final String text;
    private final boolean flag;

    private SessionParameterValue(final SessionValueKind kind, final String written, final BigInteger integer,
                                  final String text, final boolean flag) {
        this.kind = kind;
        this.written = written;
        this.integer = integer;
        this.text = text;
        this.flag = flag;
    }

    SessionValueKind kind() {
        return kind;
    }

    /** The value as a refusal of its kind echoes it. */
    String written() {
        return written;
    }

    /** A whole number's value; null for every other kind. */
    BigInteger integer() {
        return integer;
    }

    /** Text as the parameter would hold it; null for every other kind. */
    String text() {
        return text;
    }

    /** A boolean's value; false for every other kind. */
    boolean flag() {
        return flag;
    }

    /**
     * Read one assignment's value.
     *
     * @param value         the value as parsed
     * @param queryExecutor the executor, for the session's variables
     * @return the value, by its form
     */
    static SessionParameterValue read(final FrostlakeParser.SessionValueContext value,
                                      final QueryExecutor queryExecutor) {
        final String sign = value.MINUS() != null ? "-" : "";
        if (value.INTEGER_LITERAL() != null) {
            final String digits = value.INTEGER_LITERAL().getText();
            final BigInteger magnitude = new BigInteger(digits);
            return new SessionParameterValue(SessionValueKind.INTEGER, sign + digits,
                sign.isEmpty() ? magnitude : magnitude.negate(), null, false);
        }
        if (value.FLOAT_LITERAL() != null) {
            return new SessionParameterValue(SessionValueKind.DECIMAL, sign + value.FLOAT_LITERAL().getText(), null,
                null, false);
        }
        if (value.STRING_LITERAL() != null) {
            final String raw = value.STRING_LITERAL().getText();
            return text(raw, SqlStringLiterals.decode(raw));
        }
        if (value.DOLLAR_QUOTED_STRING() != null) {
            final String raw = value.DOLLAR_QUOTED_STRING().getText();
            return text(raw, raw.substring(2, raw.length() - 2));
        }
        if (value.HEX_LITERAL() != null) {
            final String raw = value.HEX_LITERAL().getText();
            return text(raw, raw);
        }
        if (value.TRUE() != null) {
            return new SessionParameterValue(SessionValueKind.BOOLEAN, value.TRUE().getText(), null, null, true);
        }
        if (value.FALSE() != null) {
            return new SessionParameterValue(SessionValueKind.BOOLEAN, value.FALSE().getText(), null, null, false);
        }
        if (value.NULL() != null) {
            return new SessionParameterValue(SessionValueKind.NULL, value.NULL().getText(), null, null, false);
        }
        if (value.SESSION_VAR_REF() != null) {
            return held(variable(value.SESSION_VAR_REF().getText().substring(1), queryExecutor));
        }
        if (value.listValue() != null) {
            // A parenthesized list reads as the parser's word for it, written bare (live-verified).
            final String word = ListValues.word(value.listValue());
            return text(word, word);
        }
        return word(value);
    }

    private static SessionParameterValue text(final String written, final String held) {
        return new SessionParameterValue(SessionValueKind.TEXT, written, null, held, false);
    }

    /** A word: one part folds as an identifier does, several parts stay as written. */
    private static SessionParameterValue word(final FrostlakeParser.SessionValueContext value) {
        final String raw = value.getText();
        if (value.identifier().size() > 1) {
            return text(raw, raw);
        }
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return text(raw, raw.substring(1, raw.length() - 1).replace("\"\"", "\""));
        }
        final String folded = raw.toUpperCase(Locale.ROOT);
        for (final String keyword : SPELLED_AS_WRITTEN) {
            if (keyword.equals(folded)) {
                return text(raw, raw);
            }
        }
        return text(raw, folded);
    }

    /** The value a session variable holds, read as the kind it is. */
    private static SessionParameterValue held(final Object held) {
        if (held == null) {
            return new SessionParameterValue(SessionValueKind.NULL, "NULL", null, null, false);
        }
        if (held instanceof Boolean) {
            final boolean flag = ((Boolean) held).booleanValue();
            return new SessionParameterValue(SessionValueKind.BOOLEAN, flag ? "TRUE" : "FALSE", null, null, flag);
        }
        if (held instanceof Number) {
            final BigDecimal number = DecimalText.parse(held.toString());
            if (number.scale() <= 0) {
                return new SessionParameterValue(SessionValueKind.INTEGER, number.toPlainString(),
                    number.toBigInteger(), null, false);
            }
            return new SessionParameterValue(SessionValueKind.DECIMAL, number.toPlainString(), null, null, false);
        }
        final String content = String.valueOf(held);
        return text(content, content);
    }

    /** A session variable's value; a variable no SET defined is refused, named upper-cased. */
    private static Object variable(final String name, final QueryExecutor queryExecutor) {
        final String folded = name.toUpperCase(Locale.ROOT);
        final SecurityManager security = queryExecutor.getSecurityManager();
        final boolean session = security != null && security.getSessionContext() != null;
        final boolean defined = session ? security.getSessionContext().isSessionVariable(folded)
            : queryExecutor.getSessionVariables().containsKey(folded);
        if (!defined) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " error line -1 at position -1\n"
                + "Session variable '$" + folded + "' does not exist");
        }
        return session ? security.getSessionContext().getSessionVariable(folded)
            : queryExecutor.getSessionVariables().get(folded);
    }
}
