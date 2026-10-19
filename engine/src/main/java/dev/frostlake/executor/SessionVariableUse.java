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

/**
 * A {@code $name} is the session's variable, and a stored procedure that runs with its owner's rights may not
 * read one — in an expression of its block, in a statement it runs or in one its handler issues, and in a
 * procedure it calls, whatever rights that one was created with: {@code Use of session variable '$V' is not
 * allowed in owners rights stored procedure}. GETVARIABLE answers there all the same, and a procedure that runs
 * with its caller's rights reads the variable (all live-verified). The value is handed in as an argument instead.
 */
public final class SessionVariableUse {

    private SessionVariableUse() {
    }

    /**
     * Refuse the read of a session variable inside an owner's-rights procedure, or do nothing.
     *
     * @param name the variable's name, upper-cased
     */
    public static void requireCallersRights(final String name) {
        if (SessionRole.runsWithOwnersRights()) {
            throw refusal(name);
        }
    }

    /**
     * The refusal of a session variable an owner's-rights procedure names.
     *
     * @param name the variable's name, upper-cased
     * @return the refusal
     */
    public static RuntimeException refusal(final String name) {
        return new RuntimeException("Use of session variable '$" + name
            + "' is not allowed in owners rights stored procedure");
    }
}
