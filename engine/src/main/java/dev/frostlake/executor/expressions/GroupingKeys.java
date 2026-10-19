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
 * The keys of the grouping set a ROLLUP, CUBE or GROUPING SETS super-group row is computed for, as GROUPING and
 * GROUPING_ID read them (see {@link GroupingSetScope}).
 */
public interface GroupingKeys {

    /**
     * Whether the grouping set groups by an expression, which GROUPING then answers with a 0 bit.
     *
     * @param argument one of GROUPING's arguments
     * @return whether a key of the set is that expression
     */
    boolean groups(Expression argument);
}
