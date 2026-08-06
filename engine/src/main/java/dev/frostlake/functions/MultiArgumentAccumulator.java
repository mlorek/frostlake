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

package dev.frostlake.functions;

import java.util.List;

/**
 * How an accumulator that needs more than one argument per row receives them. The engine's own
 * multi-argument aggregates — MAX_BY, OBJECT_AGG, CORR, LISTAGG — are each fed by a branch that
 * knows their accumulator class by name; a pack contributed through the {@code FunctionProvider} SPI
 * has no such branch, so this interface is the seam it declares instead.
 *
 * <p>An accumulator implementing this is handed the whole row tuple in argument order, and the
 * single-value {@link AggregateFunction.Accumulator#accumulate} it still inherits is left for
 * whatever it wants to do with the first one.
 */
public interface MultiArgumentAccumulator {

    /** One row's values, one per declared argument, in the order the call wrote them. */
    void accumulate(final List<Object> argumentValues);
}
