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

import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The Snowflake Scripting expressions evaluated whole, as the SQL they are, rather than node by node over the
 * values of their parts. A unit-suffixed interval literal ({@code INTERVAL '1' DAY}, {@code INTERVAL '1.25'
 * SECOND(2,3)}) is typed by its qualifier, and that type decides what arithmetic on it answers, how the result
 * prints and when it is out of range: in a block and a stored procedure as in a query, {@code INTERVAL '1' DAY / 2}
 * is zero days and {@code TO_VARCHAR} prints it {@code +0}, {@code INTERVAL '1' DAY + INTERVAL '1' HOUR} prints
 * {@code +1 01}, and {@code INTERVAL '999999999' DAY * 10} is refused as out of range. A value alone carries no
 * such type, so an expression that holds one of these literals runs as SQL, where the type is known. A subquery
 * inside the expression is a query of its own and is not searched.
 */
final class ScriptIntervalExpressions {

    private ScriptIntervalExpressions() {
    }

    /**
     * Whether an expression holds a unit-suffixed interval literal outside the subqueries it contains.
     *
     * @param expression a parsed expression
     * @return whether it is evaluated whole, as SQL
     */
    static boolean runsAsSql(final ParseTree expression) {
        if (expression instanceof FrostlakeParser.IntervalExprContext) {
            return true;
        }
        if (expression instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        for (int i = 0; i < expression.getChildCount(); i++) {
            if (runsAsSql(expression.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
