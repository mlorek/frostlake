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
 * The failure of a call that met a SQL function again while that function's own body was being evaluated (see
 * {@link SqlFunctionCycle}). It is the statement's failure as it stands: no function it passes through on the
 * way out adds its own words to it.
 */
public final class SqlFunctionCycleFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the failure's sentence
     */
    public SqlFunctionCycleFailure(final String message) {
        super(message);
    }
}
