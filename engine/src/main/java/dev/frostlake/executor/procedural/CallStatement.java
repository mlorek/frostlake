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
 * A CALL a block runs. It is a statement of its own and runs as it is written: its {@code :names} are bound from the
 * block, and a bare name among its arguments is an identifier of the statement's, never a variable of the block
 * (live-verified).
 */
public class CallStatement extends Statement {
    // The CALL as written, without its INTO clause.
    private final String writtenCall;
    // CALL ... INTO :variable: the variable the value the procedure returns is assigned to, or null for a bare CALL.
    private final String intoTarget;

    /**
     * @param writtenCall the CALL as written, without its INTO clause
     * @param intoTarget  the variable its answer is assigned to, or null for a bare CALL
     */
    public CallStatement(final String writtenCall, final String intoTarget) {
        super(StatementType.CALL);
        this.writtenCall = writtenCall;
        this.intoTarget = intoTarget;
    }

    /** The variable CALL ... INTO assigns the returned value to, or null for a bare CALL. */
    public String getIntoTarget() {
        return intoTarget;
    }

    /** The CALL as written, without its INTO clause. */
    public String getWrittenCall() {
        return writtenCall;
    }
}
