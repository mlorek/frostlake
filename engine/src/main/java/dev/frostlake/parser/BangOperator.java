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

package dev.frostlake.parser;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The {@code !} the account reads where it reads NOT. Before EXISTS it negates, through any NOT, any other
 * {@code !} and any parentheses between the two: {@code !EXISTS (…)}, {@code !(EXISTS (…))} and
 * {@code ! NOT EXISTS (…)} answer. Before anything else it is a call of a function named {@code "!"}, which no
 * account has: {@code !x} is {@code Unknown function "!".} (all live-verified).
 */
public final class BangOperator {

    private BangOperator() {
    }

    /**
     * Whether a {@code !} negates an EXISTS, and so is no call.
     *
     * @param bang the {@code !} and its operand
     * @return true when the operand, its negations and parentheses aside, is an EXISTS
     */
    public static boolean negatesExists(final FrostlakeParser.BangExprContext bang) {
        ParseTree operand = bang.booleanExpr();
        while (true) {
            if (operand instanceof FrostlakeParser.NotExprContext) {
                operand = ((FrostlakeParser.NotExprContext) operand).booleanExpr();
            } else if (operand instanceof FrostlakeParser.BangExprContext) {
                operand = ((FrostlakeParser.BangExprContext) operand).booleanExpr();
            } else if (operand instanceof FrostlakeParser.ValueExprContext) {
                operand = ((FrostlakeParser.ValueExprContext) operand).expression();
            } else if (operand instanceof FrostlakeParser.ParenExprContext) {
                operand = ((FrostlakeParser.ParenExprContext) operand).booleanExpr();
            } else {
                return operand instanceof FrostlakeParser.ExistsExprContext;
            }
        }
    }
}
