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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * LAST_QUERY_ID() function - returns the query ID of the last executed query
 */
public class LastQueryId extends BuiltInFunction {

    private final QueryResultCache resultCache;

    public LastQueryId(final QueryResultCache resultCache) {
        super("LAST_QUERY_ID", StringType.VARCHAR);
        this.resultCache = resultCache;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        int index = -1; // default: most recent
        if (!args.isEmpty() && args.get(0) != null) {
            index = ((Number) args.get(0)).intValue();
        }
        return resultCache.getQueryId(index);
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 1; }
}
