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

/**
 * Finds the aggregate function a user created under the name a call writes, for the registry's aggregate lookups
 * (see {@link FunctionRegistry#getAggregateFunction}): a call of a user's aggregate is an aggregate wherever one of
 * a built-in is.
 */
public interface UserAggregateLookup {

    /**
     * The user's aggregate function a call of this name means, or null when the name names none.
     *
     * @param name the name as the call writes it, upper-cased, with or without its schema and database
     * @return the aggregate, or null
     */
    AggregateFunction find(final String name);

    /**
     * Whether the name finds scalar overloads beside its aggregate ones, so a call's arguments decide which kind it
     * calls (see {@link FunctionRegistry#namesScalarBesideAggregate}).
     *
     * @param name the name as the call writes it, upper-cased, with or without its schema and database
     * @return whether it finds both kinds
     */
    boolean namesScalarBesideAggregate(final String name);
}
