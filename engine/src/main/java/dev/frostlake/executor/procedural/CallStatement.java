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

import java.util.ArrayList;
import java.util.List;

public class CallStatement extends Statement {
    private final String procedureName;
    private final List<BaseExpression> arguments;
    // Parallel to arguments: the argument name for a {@code name => value} named argument, or null for a
    // positional one. Null overall means every argument is positional.
    private final List<String> argumentNames;
    // CALL ... INTO :variable: the variable the value the procedure returns is assigned to, or null for a bare CALL.
    private final String intoTarget;
    // A CALL of a SYSTEM$ function, which runs as the statement it is written as; null for a procedure's CALL.
    private final String writtenCall;

    public CallStatement(final String procedureName, final List<BaseExpression> arguments) {
        this(procedureName, arguments, null);
    }

    public CallStatement(final String procedureName, final List<BaseExpression> arguments,
                         final List<String> argumentNames) {
        this(procedureName, arguments, argumentNames, null);
    }

    public CallStatement(final String procedureName, final List<BaseExpression> arguments,
                         final List<String> argumentNames, final String intoTarget) {
        super(StatementType.CALL);
        this.procedureName = procedureName;
        this.arguments = arguments;
        this.argumentNames = argumentNames;
        this.intoTarget = intoTarget;
        this.writtenCall = null;
    }

    /**
     * A CALL of a SYSTEM$ function whose answer goes INTO a variable: it runs as written.
     *
     * @param writtenCall the CALL as written, without its INTO clause
     * @param intoTarget  the variable its answer is assigned to
     */
    public CallStatement(final String writtenCall, final String intoTarget) {
        super(StatementType.CALL);
        this.procedureName = null;
        this.arguments = new ArrayList<>();
        this.argumentNames = null;
        this.intoTarget = intoTarget;
        this.writtenCall = writtenCall;
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

    /** The variable CALL ... INTO assigns the returned value to, or null for a bare CALL. */
    public String getIntoTarget() {
        return intoTarget;
    }

    /** The CALL as written, for a SYSTEM$ function's; null for a procedure's, which is re-issued from its parts. */
    public String getWrittenCall() {
        return writtenCall;
    }
}
