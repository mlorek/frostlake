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

import dev.frostlake.executor.expressions.SourcePosition;

/**
 * A {@code :name} inside a scripting block naming no declared variable — the same "invalid identifier"
 * refusal as any other unresolvable name, and deliberately worded identically.
 *
 * <p>It is typed apart for one reason: live resolves a block's own names when the BLOCK is compiled,
 * before any of it runs, so this refusal escapes bare — without the "Uncaught exception of type
 * 'STATEMENT_ERROR'" sentence that wraps a statement which ran and failed. Every other unresolvable
 * name (a column, a qualifier) belongs to the STATEMENT's compilation and is wrapped. Frostlake
 * discovers this one while the statement runs rather than while the block compiles, so the type is
 * what tells the block handler which side of that line it falls on.
 */
public class UndeclaredScriptVariableException extends InvalidQualifierException {

    public UndeclaredScriptVariableException(final String name) {
        super(name);
    }

    public UndeclaredScriptVariableException(final String name, final SourcePosition position) {
        super(name, position);
    }
}
