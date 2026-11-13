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

import dev.frostlake.executor.expressions.Expression;

import java.util.ArrayList;
import java.util.List;

/** A SQL function's body as a plan writes a call of it in its place, with the names its arguments bind. */
final class InlinedFunctionBody {

    private final Expression body;
    private final List<String> parameters;

    /**
     * @param body       the body's expression
     * @param parameters the parameters' names, upper-cased, in declaration order
     */
    InlinedFunctionBody(final Expression body, final List<String> parameters) {
        this.body = body;
        this.parameters = new ArrayList<>(parameters);
    }

    /** @return the body's expression */
    Expression getBody() {
        return body;
    }

    /** @return the parameters' names, upper-cased, in declaration order */
    List<String> getParameters() {
        return new ArrayList<>(parameters);
    }
}
