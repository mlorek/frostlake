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

package dev.frostlake.executor.udf;

import dev.frostlake.executor.commands.FailureCode;

import java.sql.SQLException;
import java.util.Arrays;

/**
 * A statement a handler ran through its Snowpark session that failed, as the handler sees it (live-verified): the
 * driver's exception, printed {@code net.snowflake.client.jdbc.SnowflakeSQLException: } and the statement's
 * refusal, in the report of a handler that lets it go as in a handler's own print of it, carrying the code and the
 * state a block's handler reads for that refusal. It is a checked SQLException the session raises without
 * declaring it, as Snowpark's own code does, so a handler catches it as an Exception and never as a
 * RuntimeException. Its frames start at the session's method the handler called.
 */
public final class SnowparkStatementFailure extends SQLException {

    private static final long serialVersionUID = 1L;

    /** The exception's name as the driver beneath Snowpark gives it. */
    private static final String DRIVER_EXCEPTION = "net.snowflake.client.jdbc.SnowflakeSQLException";

    /** The package of the session's classes, whose frames lie between the refusal and the handler. */
    static final String SNOWPARK_PACKAGE = "com.snowflake.snowpark_java.";

    private SnowparkStatementFailure(final String message, final String state, final int code) {
        super(message, state, code);
    }

    /**
     * Raises a statement's refusal as the handler that ran it sees it.
     *
     * @param refused the statement's refusal
     * @return never: the failure is thrown
     */
    public static RuntimeException raise(final RuntimeException refused) {
        final FailureCode code = FailureCode.of(refused);
        return raise(refused.getMessage(), code.state(), code.code());
    }

    /**
     * Raises a refusal of the driver itself, one of a value it cannot read as the class asked of it.
     *
     * @param message the refusal
     * @param state   its SQLSTATE
     * @param code    its code
     * @return never: the failure is thrown
     */
    public static RuntimeException raise(final String message, final String state, final int code) {
        final SnowparkStatementFailure failure = new SnowparkStatementFailure(message, state, code);
        failure.setStackTrace(fromSessionCall(failure.getStackTrace()));
        return SnowparkStatementFailure.<RuntimeException>undeclared(failure);
    }

    @Override
    public String toString() {
        final String message = getLocalizedMessage();
        return message == null ? DRIVER_EXCEPTION : DRIVER_EXCEPTION + ": " + message;
    }

    /** The frames from the session's method the handler called on: the session's own below it are not shown. */
    private static StackTraceElement[] fromSessionCall(final StackTraceElement[] trace) {
        int first = 0;
        while (first < trace.length && (trace[first].getClassName().equals(SnowparkStatementFailure.class.getName())
                || trace[first].getClassName().startsWith(SNOWPARK_PACKAGE))) {
            first++;
        }
        if (first == 0 || !trace[first - 1].getClassName().startsWith(SNOWPARK_PACKAGE)) {
            return trace;
        }
        return Arrays.copyOfRange(trace, first - 1, trace.length);
    }

    /** Throws a checked exception through a method that declares none. */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException undeclared(final Throwable thrown) throws E {
        throw (E) thrown;
    }
}
