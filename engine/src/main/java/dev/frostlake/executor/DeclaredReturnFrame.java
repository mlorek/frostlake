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

import dev.frostlake.types.DataType;

import java.util.Collections;
import java.util.Set;

/**
 * The declared RETURNS type of the procedure whose body is running, with the block and compound
 * depths its body started at — what tells a RETURN written directly in that body (the one live
 * converts to the declared type) from one nested in an IF, a loop, a handler or an inner block (kept
 * as it is). A CALL pushes a frame and puts the caller's back afterwards, so nested calls each see
 * their own.
 */
public final class DeclaredReturnFrame {
    private final DataType type;
    private final boolean table;
    private final int blockDepth;
    private final int compoundDepth;
    private final Set<String> parameterNames;

    public DeclaredReturnFrame(final DataType type, final boolean table, final int blockDepth,
                               final int compoundDepth, final Set<String> parameterNames) {
        this.type = type;
        this.table = table;
        this.blockDepth = blockDepth;
        this.compoundDepth = compoundDepth;
        this.parameterNames = Collections.unmodifiableSet(parameterNames);
    }

    /** The declared RETURNS type, or null for a table-returning or untyped body. */
    public DataType getType() {
        return type;
    }

    /** Whether the procedure declares RETURNS TABLE, so every RETURN in its body must return a table. */
    public boolean returnsTable() {
        return table;
    }

    /** The block depth outside the body: the body's own block is one deeper. */
    public int getBlockDepth() {
        return blockDepth;
    }

    /** The compound-statement depth outside the body. */
    public int getCompoundDepth() {
        return compoundDepth;
    }

    /** The names the procedure's signature declares, as its parameters store them. */
    public Set<String> getParameterNames() {
        return parameterNames;
    }
}
