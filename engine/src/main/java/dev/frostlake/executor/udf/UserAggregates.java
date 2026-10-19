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

package dev.frostlake.executor.udf;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RoutineOverloads;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.UserAggregateLookup;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds a user's aggregate function by the name a call writes, where a call finds any function of the user's
 * (live-verified): {@code db.schema.f} in that schema, {@code schema.f} in that schema of the current database, and
 * a bare name in the first schema of the session's SEARCH_PATH holding a function of that name, else in the current
 * schema. A name no aggregate function was ever created under is not looked for at all.
 */
public final class UserAggregates implements UserAggregateLookup {

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    /**
     * @param catalog       the catalog the functions live in
     * @param queryExecutor the executor whose session's search path finds a bare name
     */
    public UserAggregates(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public AggregateFunction find(final String name) {
        if (!catalog.holdsAggregateFunctionNames()) {
            return null;
        }
        try {
            final String[] parts = QualifiedName.parse(name).parts();
            final String simple = parts[parts.length - 1];
            if (!catalog.mayNameAggregateFunction(simple)) {
                return null;
            }
            final Schema schema = schemaOf(parts);
            if (schema == null) {
                return null;
            }
            final List<Function> aggregates = new ArrayList<>();
            for (final Function overload : schema.getFunctionOverloads(simple)) {
                if (overload.isAggregate()) {
                    aggregates.add(overload);
                }
            }
            return aggregates.isEmpty() ? null
                : new UserAggregateFunction(aggregates, catalog, queryExecutor.getS3PathResolver(),
                    queryExecutor.getProceduralExecutor());
        } catch (final RuntimeException unresolvable) {
            return null;
        }
    }

    @Override
    public boolean namesScalarBesideAggregate(final String name) {
        if (!catalog.holdsAggregateFunctionNames()) {
            return false;
        }
        try {
            final String[] parts = QualifiedName.parse(name).parts();
            final String simple = parts[parts.length - 1];
            if (!catalog.mayNameAggregateFunction(simple)) {
                return false;
            }
            final Schema schema = schemaOf(parts);
            if (schema == null) {
                return false;
            }
            boolean aggregate = false;
            boolean scalar = false;
            for (final Function overload : schema.getFunctionOverloads(simple)) {
                aggregate |= overload.isAggregate();
                scalar |= !overload.isAggregate() && !overload.isTableFunction();
            }
            return aggregate && scalar;
        } catch (final RuntimeException unresolvable) {
            return false;
        }
    }

    /** The schema a function name resolves against (see {@link RoutineOverloads#schemaOf}), or null. */
    private Schema schemaOf(final String[] parts) {
        return RoutineOverloads.schemaOf(queryExecutor, Arrays.asList(parts));
    }
}
