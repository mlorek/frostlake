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

package dev.frostlake.executor.commands;

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

/**
 * Compiles, while the block compiles, what the account compiles with it — see {@code ScriptingNameValidator}: a
 * FROM-less scalar subquery an untyped declaration infers its type from, and the argument types of an untyped
 * declaration's initialiser and of a simple CASE's operand, over the types the block has declared so far.
 */
interface InferredInitialiserCompiler {

    /**
     * Refuse the subquery as the account's compilation of it does, at its place in the block.
     *
     * @param query the subquery's SELECT
     */
    void compile(FrostlakeParser.SelectStatementContext query);

    /**
     * A name the block declares with a written type.
     *
     * @param name       the name, folded
     * @param type       the type as written
     * @param parameters its parameters, or null
     */
    void declare(String name, FrostlakeParser.DataTypeNameContext type, FrostlakeParser.TypeParametersContext parameters);

    /**
     * A FOR loop's counter.
     *
     * @param name the counter, folded
     */
    void declareCounter(String name);

    /**
     * Judge an untyped declaration's initialiser, then declare the name with the type it infers — refused at
     * {@code at} when that type is a VARIANT, which the account does not infer.
     *
     * @param name        the name, folded
     * @param initialiser the initialiser
     * @param at          where the declaration's refusal is placed: the LET, or a DECLARE item's name
     */
    void infer(String name, FrostlakeParser.BooleanExprContext initialiser, Token at);

    /**
     * Judge a simple CASE's operand.
     *
     * @param operand the operand
     */
    void judge(FrostlakeParser.BooleanExprContext operand);

    /**
     * A nested body opens — an IF or CASE branch, a loop body, a nested block, an exception handler: what it
     * declares hides an outer name of the same name until it closes.
     */
    void enterScope();

    /** The innermost nested body closes: the types in scope are the ones it opened with. */
    void exitScope();
}
