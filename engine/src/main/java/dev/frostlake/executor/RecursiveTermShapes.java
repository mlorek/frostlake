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

import dev.frostlake.storage.ResultSetColumn;
import java.util.List;

/**
 * Plans a recursive CTE's recursive terms for their columns alone, over columns the CTE itself is given: how
 * {@link RecursiveCteColumns} learns what each term computes once the CTE's own columns are typed.
 */
interface RecursiveTermShapes {

    /**
     * The recursive terms' columns.
     *
     * @param cteColumns the columns the CTE's reference to itself carries
     * @return each term's columns, in the order the terms are written
     */
    List<List<ResultSetColumn>> over(List<ResultSetColumn> cteColumns);
}
