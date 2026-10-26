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
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.JsonArrayExpression;
import dev.frostlake.executor.expressions.JsonObjectExpression;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;

import java.util.Locale;

/**
 * An array or an object constructor is no item of a VALUES list in FROM, as it is none of an INSERT's: {@code FROM
 * VALUES ([1])} is refused while the statement compiles, {@code Invalid expression [ARRAY_CONSTRUCT(1)] in VALUES
 * clause}, the item echoed as the plan spells it (live-verified). A bracket or brace literal is the constructor call it
 * stands for.
 */
final class ValuesConstructorItems {

    private ValuesConstructorItems() {
    }

    /**
     * The refusal of a VALUES item that constructs an array or an object.
     *
     * @param item the item, parsed; null when it did not parse as an expression
     * @return the refusal, or null for any other item
     */
    static RuntimeException refusalOf(final Expression item) {
        if (!constructsAContainer(item)) {
            return null;
        }
        return new RuntimeException(SqlCompilationError.of("Invalid expression ["
            + ParsedEchoPrinter.print(item, new StructuredElementEcho()) + "] in VALUES clause"));
    }

    private static boolean constructsAContainer(final Expression item) {
        if (item instanceof JsonArrayExpression || item instanceof JsonObjectExpression) {
            return true;
        }
        if (!(item instanceof FunctionCallExpression) || ((FunctionCallExpression) item).getFunctionName() == null) {
            return false;
        }
        final String name = ((FunctionCallExpression) item).getFunctionName().toUpperCase(Locale.ROOT);
        return "ARRAY_CONSTRUCT".equals(name) || "OBJECT_CONSTRUCT".equals(name);
    }
}
