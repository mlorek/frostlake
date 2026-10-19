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

import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.parser.FrostlakeParser;

/**
 * A {@code ->>} chain of more than one statement fails as a scripting block's statement does: the failing stage's
 * refusal is an uncaught STATEMENT_ERROR placed at the stage's first token, counted past the request's opening
 * comments, and a refusal raised while the stage compiles counts its own places from that token (live-verified):
 *
 * <pre>
 *   SELECT 1/0 ->> SELECT * FROM $1     Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 :
 *                                       Division by zero
 *   SELECT 1 ->> SELECT nope FROM $1    ... on line 1 at position 13 : SQL compilation error: error line 1 at
 *                                       position 7 invalid identifier 'NOPE'
 *   SELECT 1 ->> SELECT * FROM $2       ... on line 1 at position 13 : Statement null not found
 * </pre>
 *
 * <p>A chain of one statement is no block: its refusal stays its own. A canceled statement keeps its own refusal and
 * codes, and a refusal of the chain as a whole stays its own (see {@link FlowChainRefusal}).
 */
public final class FlowChainFailure {

    private FlowChainFailure() {
    }

    /**
     * The refusal a chain answers for a stage that failed.
     *
     * @param stage   the stage that failed
     * @param failure what it failed with
     * @param sql     the request's text, whose opening comments do not count
     * @return the refusal to throw
     */
    static RuntimeException wrapping(final FrostlakeParser.StatementContext stage, final RuntimeException failure,
                                     final String sql) {
        if (failure instanceof StatementCanceledException || failure instanceof FlowChainRefusal) {
            return failure;
        }
        final int[] placed = LeadingCommentOffset.rebase(LeadingCommentOffset.of(sql), stage.getStart().getLine(),
            stage.getStart().getCharPositionInLine());
        return new RuntimeException("Uncaught exception of type 'STATEMENT_ERROR' on line " + placed[0]
            + " at position " + placed[1] + " : " + failure.getMessage(), failure);
    }

    /**
     * Where a stage's own places count from: its first token.
     *
     * @param stage the stage
     * @return the place
     */
    static SourcePosition origin(final FrostlakeParser.StatementContext stage) {
        return new SourcePosition(stage.getStart().getLine(), stage.getStart().getCharPositionInLine());
    }
}
