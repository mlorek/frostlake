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

import java.util.List;

public class CallStatement extends Statement {
    private final String procedureName;
    private final List<BaseExpression> arguments;
    // Parallel to arguments: the argument name for a {@code name => value} named argument, or null for a
    // positional one. Null overall means every argument is positional.
    private final List<String> argumentNames;

    public CallStatement(final String procedureName, final List<BaseExpression> arguments) {
        this(procedureName, arguments, null);
    }

    public CallStatement(final String procedureName, final List<BaseExpression> arguments,
                         final List<String> argumentNames) {
        super(StatementType.CALL);
        this.procedureName = procedureName;
        this.arguments = arguments;
        this.argumentNames = argumentNames;
    }

    public String getProcedureName() {
        return procedureName;
    }

    public List<BaseExpression> getArguments() {
        return arguments;
    }

    /** Argument names parallel to {@link #getArguments()} (null entry = positional), or null if all positional. */
    public List<String> getArgumentNames() {
        return argumentNames;
    }
}
