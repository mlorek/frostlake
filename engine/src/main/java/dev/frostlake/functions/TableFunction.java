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

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import java.util.List;
import java.util.Map;

/**
 * Interface for table-valued functions
 */
public abstract class TableFunction {

    private final String name;

    protected TableFunction(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Execute the table function with named arguments
     */
    public abstract ResultSet execute(final Map<String, Object> namedArgs);

    /**
     * Execute the table function with positional arguments.
     * Override this in user-defined table functions.
     */
    public ResultSet execute(final List<Object> positionalArgs) {
        throw new UnsupportedOperationException("Positional argument execution not supported for " + name);
    }

    /**
     * Validate the arguments passed to the function
     */
    public abstract void validateArgs(final Map<String, Object> namedArgs);

    /**
     * The columns the function answers for these arguments, when it can tell them without running — what
     * lets a query over it be planned before any row is produced. Null when only running the function tells.
     *
     * @param namedArgs the call's named arguments
     * @return the columns, or null
     */
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return null;
    }
}
