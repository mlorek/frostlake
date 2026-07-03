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

public class ProceduralException extends RuntimeException {
    private final int errorCode;
    private final String exceptionName;

    public ProceduralException(final String message) {
        super(message);
        this.errorCode = -1;
        this.exceptionName = null;
    }

    public ProceduralException(final int errorCode, final String message) {
        this(errorCode, message, null);
    }

    public ProceduralException(final int errorCode, final String message, final String exceptionName) {
        super(message);
        this.errorCode = errorCode;
        this.exceptionName = exceptionName;
    }

    public int getErrorCode() {
        return errorCode;
    }

    /** The declared name of the user-defined exception that was RAISEd, or null for an anonymous RAISE. */
    public String getExceptionName() {
        return exceptionName;
    }
}
