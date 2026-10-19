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
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.commands.DeferredHandlerCheck;
import dev.frostlake.executor.expressions.ValueOverloads;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Collections;
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
    private final ProceduralExecutor procedural;

    /**
     * @param overloads  the aggregate overloads the call's name finds, at least one
     * @param catalog    the catalog a handler's IMPORTS are resolved in, or null
     * @param s3Resolver resolves a handler's IMPORTS in external storage, or null
     * @param procedural converts a guest-language handler's arguments to their parameters' types, or null
     */
    public UserAggregateFunction(final List<Function> overloads, final Catalog catalog,
                                 final S3PathResolver s3Resolver, final ProceduralExecutor procedural) {
        super(overloads.get(0).getName(), overloads.get(0).getReturnType());
        this.overloads = new ArrayList<>(overloads);
        this.catalog = catalog;
        this.s3Resolver = s3Resolver;
        this.procedural = procedural;
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

    /**
     * This aggregate held to one of its overloads: the one a call's arguments' static types chose, which every group
     * of the call runs - an all-NULL group too (live-verified).
     *
     * @param overload the overload, one of this aggregate's
     * @return the aggregate running that overload alone
     */
    public UserAggregateFunction pinnedTo(final Function overload) {
        return new UserAggregateFunction(Collections.singletonList(overload), catalog, s3Resolver, procedural);
    }

    /**
     * Whether a function is one of this aggregate's overloads.
     *
     * @param function the function
     * @return whether a call of this aggregate may run it
     */
    public boolean holds(final Function function) {
        for (final Function overload : overloads) {
            if (overload == function) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every overload of another aggregate is one of this one's: an aggregate pinned earlier still names
     * functions the catalog holds.
     *
     * @param other the other aggregate
     * @return whether each of its overloads is one of this aggregate's
     */
    public boolean holdsEvery(final UserAggregateFunction other) {
        for (final Function overload : other.overloads) {
            if (!holds(overload)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The values that choose a group's overload where the call's arguments could not be typed: in each argument's
     * place, the first value a row gives that is not NULL, or NULL where every row gives NULL.
     */
    private static List<Object> decidingValues(final List<List<Object>> rows) {
        final List<Object> values = new ArrayList<>(rows.get(0));
        for (int i = 0; i < values.size(); i++) {
            for (int r = 1; values.get(i) == null && r < rows.size(); r++) {
                values.set(i, i < rows.get(r).size() ? rows.get(r).get(i) : null);
            }
        }
        return values;
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
     * A guest-language handler's rows, each argument converted to its parameter's SQL type as a scalar handler's is
     * (live-verified): an INT parameter rounds 1.5 to 2, a NUMBER(10,2) one holds 1.255 as 1.26, a VARCHAR one
     * reads a number as its text and a BOOLEAN one reads 'off' as FALSE.
     */
    private List<List<Object>> declaredArguments(final Function function, final List<List<Object>> rows) {
        if (procedural == null) {
            return rows;
        }
        final List<Parameter> parameters = function.getParameters();
        final List<List<Object>> declared = new ArrayList<>(rows.size());
        for (final List<Object> row : rows) {
            final List<Object> values = new ArrayList<>(row.size());
            for (int i = 0; i < row.size(); i++) {
                values.add(i < parameters.size()
                    ? procedural.coerceArgument(row.get(i), parameters.get(i).getDataType(), false) : row.get(i));
            }
            declared.add(values);
        }
        return declared;
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
        final Function function = ValueOverloads.choose(overloads, decidingValues(rows));
        if (function == null) {
            throw new RuntimeException("Function " + getName() + " takes no " + rows.get(0).size() + " arguments");
        }
        // A temporary aggregate's handler is judged as rows first reach it.
        DeferredHandlerCheck.require(function);
        if (function.getUdfLanguage() == UdfLanguage.JAVA) {
            return JavaAggregateFunctions.run(function, rows, catalog, s3Resolver);
        }
        final Object answered = GuestResults.declared(function, UdfRuntimes.require(function.getUdfLanguage())
            .executeAggregate(function, declaredArguments(function, rows)));
        // A whole number a FLOAT function answers is a FLOAT's: 15.0, not 15.
        return answered instanceof Number && NumericType.isApproximate(function.getReturnType())
            ? Double.valueOf(((Number) answered).doubleValue()) : answered;
    }
}
