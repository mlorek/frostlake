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

import java.util.List;

/**
 * A block variable written out in a refusal's echo as the conversion of its bound value's text, {@code
 * TO_NUMBER(NULL)} for a NUMBER holding NULL: the echo spells it as written, where a conversion the statement
 * itself writes is spelled as the plan holds it, {@code SYSTEM$NULL_TO_FIXED(NULL)} (live-verified).
 */
public final class BoundValueCall extends FunctionCallExpression {

    /**
     * @param functionName the conversion
     * @param arguments    the bound value's text, and the type's precision and scale where it carries them
     */
    public BoundValueCall(final String functionName, final List<Expression> arguments) {
        super(functionName, arguments);
    }
}
