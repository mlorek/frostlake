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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;

/**
 * An aggregate function a user created with CREATE AGGREGATE FUNCTION, called as a built-in aggregate is: it
 * groups rows, reads every row of its group - NULL arguments too - and answers one value per group (live-verified).
 * A group's rows are handed to the function's handler once the group is complete, in the handler's own language
 * (see {@link JavaAggregateFunctions} and {@link UdfLanguageRuntime#executeAggregate}); an aggregation over no rows
 * at all is NULL, the handler never asked.
 *
 * <p>A refusal that echoes the plan names every user aggregate {@link #PLANNED_NAME}, whatever it is called:
 * {@code Invalid aggregate function in where clause [EXTENSION_FUNC_A(T.A)]} (live-verified).
 */
public final class UserAggregateFunction extends AggregateFunction {

    /** The name the plan gives every user aggregate, which refusals echoing the plan print (live-verified). */
    public static final String PLANNED_NAME = "EXTENSION_FUNC_A";

    private final List<Function> overloads;
    private final Catalog catalog;
    private final S3PathResolver s3Resolver;

    /**
     * @param overloads  the aggregate overloads the call's name finds, at least one
     * @param catalog    the catalog a handler's IMPORTS are resolved in, or null
     * @param s3Resolver resolves a handler's IMPORTS in external storage, or null
     */
    public UserAggregateFunction(final List<Function> overloads, final Catalog catalog,
                                 final S3PathResolver s3Resolver) {
        super(overloads.get(0).getName(), overloads.get(0).getReturnType());
        this.overloads = new ArrayList<>(overloads);
        this.catalog = catalog;
        this.s3Resolver = s3Resolver;
    }

    /**
     * The type a call's result is declared with: the function's RETURNS type, a VARCHAR written without a length
     * the unknown length's (live-verified).
     *
     * @return the type
     */
    public DataType resultType() {
        final Function function = overloads.get(0);
        final DataType declared = function.getReturnType();
        if (declared instanceof StringType && !Boolean.TRUE.equals(function.getReturnSized())) {
            return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
        }
        return declared;
    }

    /**
     * The overload a call with this many arguments runs.
     *
     * @param argumentCount how many arguments the call passes
     * @return the overload, or null when none takes that many
     */
    public Function overloadFor(final int argumentCount) {
        for (final Function overload : overloads) {
            if (overload.getParameters().size() == argumentCount) {
                return overload;
            }
        }
        return null;
    }

    @Override
    public Accumulator createAccumulator() {
        return new UserAggregateAccumulator(this);
    }

    /** Not the aggregation path: a group's value comes from its accumulator. */
    @Override
    public Object evaluate(final List<Object> args) {
        return null;
    }

    @Override
    public int getMinArgCount() {
        int fewest = Integer.MAX_VALUE;
        for (final Function overload : overloads) {
            fewest = Math.min(fewest, overload.getParameters().size());
        }
        return fewest;
    }

    @Override
    public int getMaxArgCount() {
        int most = 0;
        for (final Function overload : overloads) {
            most = Math.max(most, overload.getParameters().size());
        }
        return most;
    }

    /**
     * The value of one group: NULL for no rows, else what the handler answers over them.
     *
     * @param rows the group's rows, each the call's arguments
     * @return the group's value
     */
    Object aggregate(final List<List<Object>> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        final Function function = overloadFor(rows.get(0).size());
        if (function == null) {
            throw new RuntimeException("Function " + getName() + " takes no " + rows.get(0).size() + " arguments");
        }
        if (function.getUdfLanguage() == UdfLanguage.JAVA) {
            return JavaAggregateFunctions.run(function, rows, catalog, s3Resolver);
        }
        final Object answered = GuestResults.declared(function,
            UdfRuntimes.require(function.getUdfLanguage()).executeAggregate(function, rows));
        // A whole number a FLOAT function answers is a FLOAT's: 15.0, not 15.
        return answered instanceof Number && NumericType.isApproximate(function.getReturnType())
            ? Double.valueOf(((Number) answered).doubleValue()) : answered;
    }
}
