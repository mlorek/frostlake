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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.SqlCompilationError;

/**
 * The refusal of an Oracle {@code (+)} outer-join marker written after anything but a column —
 * {@code Invalid argument for (+): 1.} — raised while the query compiles. A SQL UDF body refuses it as a
 * compilation of the body itself, which is why it has a type of its own.
 */
public class OuterJoinOperandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param operand the marked operand as written
     */
    public OuterJoinOperandException(final String operand) {
        super(SqlCompilationError.of("Invalid argument for (+): " + operand + "."));
    }
}
