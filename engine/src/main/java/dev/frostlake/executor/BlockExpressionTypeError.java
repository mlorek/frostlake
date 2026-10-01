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

/**
 * A block's own expression refused as it compiles before it runs — an operator or a call given arguments
 * whose types it does not take (see {@link BlockExpressionTypes}). It is an EXPRESSION_ERROR, which a
 * {@code WHEN EXPRESSION_ERROR} handler catches and a {@code WHEN STATEMENT_ERROR} one does not, and its
 * message is already placed in the expression, as the account words it (live-verified).
 */
public class BlockExpressionTypeError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal, placed in the expression
     */
    public BlockExpressionTypeError(final String message) {
        super(message);
    }
}
