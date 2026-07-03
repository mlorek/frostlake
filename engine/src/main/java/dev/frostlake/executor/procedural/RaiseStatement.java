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

public class RaiseStatement extends Statement {
    private final String exceptionName;
    private final BaseExpression message;

    public RaiseStatement(final BaseExpression message) {
        super(StatementType.RAISE);
        this.exceptionName = null;
        this.message = message;
    }

    public RaiseStatement(final String exceptionName) {
        super(StatementType.RAISE);
        this.exceptionName = exceptionName;
        this.message = null;
    }

    public String getExceptionName() {
        return exceptionName;
    }

    public BaseExpression getMessage() {
        return message;
    }

    public boolean isUserDefinedException() {
        return exceptionName != null;
    }
}
