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

/**
 * A statement of a block that failed and that no handler caught, as the outermost block of its body reports it:
 * "Uncaught exception of type 'STATEMENT_ERROR' on line L at position P : &lt;error&gt;". The place and the error are
 * kept apart for a reader that words them another way: a staged script names its file in front of them (see
 * StagedScriptFailure).
 */
public final class UncaughtStatementFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String kind;
    private final int line;
    private final int position;
    private final String error;

    /**
     * @param kind     the exception's type, STATEMENT_ERROR or EXPRESSION_ERROR
     * @param line     the line the failed statement stands on
     * @param position its position on that line
     * @param error    what the statement failed with
     * @param cause    the statement's failure
     */
    public UncaughtStatementFailure(final String kind, final int line, final int position, final String error,
                                    final Throwable cause) {
        super("Uncaught exception of type '" + kind + "' on line " + line + " at position " + position + " : "
            + error, cause);
        this.kind = kind;
        this.line = line;
        this.position = position;
        this.error = error;
    }

    public String getKind() {
        return kind;
    }

    public int getLine() {
        return line;
    }

    public int getPosition() {
        return position;
    }

    public String getError() {
        return error;
    }
}
