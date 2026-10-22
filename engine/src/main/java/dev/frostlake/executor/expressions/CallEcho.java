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

/**
 * How an echo spells a call it does not print as the tree as parsed names it (see
 * {@link ParsedEchoPrinter#print(Expression, CallEcho)}).
 */
public interface CallEcho {

    /**
     * The call's text, or null to print it as parsed.
     *
     * @param call    the call
     * @param printer the printer, which prints the call's arguments
     * @return the text, or null
     */
    String spell(FunctionCallExpression call, ParsedEchoPrinter printer);

    /**
     * An operand's text beside its operator, its own parentheses included, or null to print it as parsed.
     *
     * @param operation the operation the operand stands in
     * @param operand   the operand
     * @param printer   the printer, which prints the operand's parts
     * @return the text, or null
     */
    default String spellOperand(final BinaryOperationExpression operation, final Expression operand,
                                final ParsedEchoPrinter printer) {
        return null;
    }
}
