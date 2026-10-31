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

import dev.frostlake.security.SessionDirectory;
import dev.frostlake.types.DecimalText;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * SYSTEM$ABORT_SESSION (live-verified). The argument is a session's number, as CURRENT_SESSION() answers it, a
 * number or its digits in a string; the answer is {@code session [<number>] terminated.} whether or not a session
 * of that number exists — an unknown number, zero and a negative one answer the same sentence and end nothing. A
 * number wider than 64 bits wraps into them. NULL, text that is no number and a number with a fraction are
 * refused. The session named is ended: its next statement is refused, and its open transaction is rolled back as
 * the aborting statement ends — the engine's way with every session that is gone, where the account leaves such a
 * transaction running, its locks held, until SYSTEM$ABORT_TRANSACTION or its idle limit ends it.
 */
final class SessionAbort {

    private static final String FUNCTION = "SqlIdentifier{qualifierNames=[], identifierName=SYSTEM$ABORT_SESSION}";

    private SessionAbort() {
    }

    /**
     * Abort the session an argument names.
     *
     * @param argument  the argument's value
     * @param directory the engine's abortable sessions
     * @return the function's answer
     */
    static String abort(final Object argument, final SessionDirectory directory) {
        if (argument == null) {
            throw new RuntimeException("inputs may not be null");
        }
        final long number = number(argument);
        if (directory != null) {
            directory.abort(number);
        }
        return "session [" + number + "] terminated.";
    }

    private static long number(final Object argument) {
        if (argument instanceof Number) {
            final BigDecimal value = DecimalText.parse(argument.toString());
            if (value.stripTrailingZeros().scale() > 0) {
                throw notConstant("CAST(" + value.toPlainString() + " AS NUMBER(18,0))");
            }
            return value.toBigInteger().longValue();
        }
        final String text = String.valueOf(argument);
        try {
            return new BigInteger(text.trim()).longValue();
        } catch (final NumberFormatException notANumber) {
            throw notConstant("TO_NUMBER('" + text.replace("'", "''") + "', 18, 0)");
        }
    }

    private static RuntimeException notConstant(final String found) {
        return new RuntimeException(SqlCompilationError.of("argument 0 to function " + FUNCTION
            + " needs to be constant, found '" + found + "'"));
    }
}
