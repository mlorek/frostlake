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

import dev.frostlake.executor.expressions.FunctionCallExpression;

/**
 * Spells a call named by IDENTIFIER as a plan writes it, where the name stands for an object of its own: a
 * procedure's SYSTEM$REFERENCE token is the procedure over its parameters' names, {@code PR_ARG(X)}.
 */
interface IdentifierCallText {

    /**
     * The plan's text of a call.
     *
     * @param call the call, named by IDENTIFIER
     * @return the text, or null to spell the call as it is written
     */
    String spell(FunctionCallExpression call);
}
