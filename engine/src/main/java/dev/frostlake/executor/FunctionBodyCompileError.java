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
 * An expression of a SQL function's block body that does not compile. The body compiles with the statement that
 * calls the function, so the refusal is that statement's, in the expression's own words: no handler of the body
 * catches it, a query that calls the function is refused bare, and so is a block's LET or assignment whose value
 * calls it — where a block's RETURN or condition that calls it words the refusal as its own EXPRESSION_ERROR
 * (live-verified).
 */
public class FunctionBodyCompileError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param refused the expression's compilation error
     */
    public FunctionBodyCompileError(final RuntimeException refused) {
        super(refused.getMessage(), refused);
    }
}
