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

import dev.frostlake.executor.FlowChainRefusal;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * What the account refuses about the body a task is given, beyond the body's own syntax (live-verified): a body is
 * one statement, so a flow chain is none — {@code CREATE TASK t AS SELECT 1 ->> SELECT * FROM $1} and the same after
 * {@code ALTER TASK t MODIFY AS} are "Invalid statement for task definition. Expecting one of the following:
 * STATEMENT". A COMMENT written after the body is the body's to read: the grammar gives the task none there.
 */
final class TaskBodySyntax {

    private TaskBodySyntax() {
    }

    /**
     * Refuse a task statement whose body goes on as a flow chain.
     *
     * @param task the CREATE TASK or ALTER TASK
     */
    static void requireOneStatement(final ParserRuleContext task) {
        ParserRuleContext statement = task;
        while (statement != null && !(statement instanceof FrostlakeParser.StatementContext)) {
            statement = statement.getParent();
        }
        if (statement == null || !(statement.getParent() instanceof FrostlakeParser.FlowChainContext)) {
            return;
        }
        final List<FrostlakeParser.StatementContext> chained =
            ((FrostlakeParser.FlowChainContext) statement.getParent()).statement();
        if (chained.get(chained.size() - 1) != statement) {
            throw new FlowChainRefusal(
                "Invalid statement for task definition. Expecting one of the following: STATEMENT");
        }
    }
}
