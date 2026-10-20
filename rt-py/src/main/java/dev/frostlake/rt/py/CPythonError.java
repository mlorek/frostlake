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

package dev.frostlake.rt.py;

/**
 * A Python exception raised in the CPython worker, carried to the engine. Its message is the exception's type and
 * text ({@code ZeroDivisionError: division by zero}), the form the GraalPy path reports; the traceback is kept for
 * the log.
 */
public final class CPythonError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String pythonType;
    private final String traceback;

    CPythonError(final String message, final String pythonType, final String traceback) {
        super(message);
        this.pythonType = pythonType;
        this.traceback = traceback;
    }

    /** The exception's class name, e.g. {@code SyntaxError}. */
    public String getPythonType() {
        return pythonType;
    }

    /** The formatted Python traceback. */
    public String getTraceback() {
        return traceback;
    }
}
