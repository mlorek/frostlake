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
 * A scalar subquery whose select list has more than one item — refused while the statement compiles
 * ("Unsupported: Scalar subquery with multi-column SELECT clause.", anchored on the subquery's own
 * SELECT), a refusal that has to escape the typing pass that finds it while every other planning
 * fault there merely leaves the subquery untyped.
 */
public class MultiColumnScalarSubqueryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MultiColumnScalarSubqueryException(final String message) {
        super(message);
    }
}
