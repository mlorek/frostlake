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

package dev.frostlake.executor.procedural;

/**
 * An exception a called procedure's script raised and let out, as the statement that called it sees it: a failed
 * statement, whatever the exception was named inside — a handler catches it as STATEMENT_ERROR, not by that name —
 * whose text is the body's own uncaught-exception sentence and whose code is the exception's (live-verified).
 */
public final class CalledProcedureFailure extends ProceduralException {

    private static final long serialVersionUID = 1L;

    private CalledProcedureFailure(final int errorCode, final String message, final Throwable cause) {
        super(errorCode, message);
        initCause(cause);
    }

    /**
     * What the calling statement sees of a failure a procedure's body let out: the failure itself, unless an
     * exception the script raised is behind it.
     *
     * @param failure what the body let out
     * @return the failure, or the failed statement that keeps the raised exception's code
     */
    public static RuntimeException of(final RuntimeException failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof CalledProcedureFailure || cause instanceof ProceduralException
                    && ((ProceduralException) cause).getExceptionName() != null) {
                return new CalledProcedureFailure(((ProceduralException) cause).getErrorCode(),
                    failure.getMessage(), failure);
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return failure;
    }
}
