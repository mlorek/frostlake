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
 * Statement for declaring user-defined exceptions
 */
public class DeclareExceptionStatement extends Statement {
    private final String exceptionName;
    private final int errorCode;
    private final String message;

    public DeclareExceptionStatement(final String exceptionName, final int errorCode, final String message) {
        super(StatementType.DECLARE_EXCEPTION);
        this.exceptionName = exceptionName;
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getExceptionName() {
        return exceptionName;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
