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

import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.storage.ResultSet;

/**
 * A result read once, on first request, and answered from then on: a table function's result, which the plan
 * may read for its shape while planning and the pipeline reads for its rows.
 */
final class MemoizedResultSet implements ResultSetProvider {

    private final ResultSetProvider source;
    private ResultSet result;

    /**
     * @param source the provider run on the first request
     */
    MemoizedResultSet(final ResultSetProvider source) {
        this.source = source;
    }

    @Override
    public ResultSet getResultSet() {
        if (result == null) {
            result = source.getResultSet();
        }
        return result;
    }
}
