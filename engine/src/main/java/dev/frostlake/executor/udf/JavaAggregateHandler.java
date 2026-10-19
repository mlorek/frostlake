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

import java.lang.reflect.Method;

/** The members a Java aggregate function's handler class answers with (see {@link JavaAggregateFunctions}). */
public final class JavaAggregateHandler {

    private final Class<?> handled;
    private final Method initialize;
    private final Method accumulate;
    private final Method finish;

    /**
     * @param handled    the handler's class
     * @param initialize the method that makes a group's empty state
     * @param accumulate the method that folds a row's arguments into a state
     * @param finish     the method that turns a state into the group's result
     */
    public JavaAggregateHandler(final Class<?> handled, final Method initialize, final Method accumulate,
                                final Method finish) {
        this.handled = handled;
        this.initialize = initialize;
        this.accumulate = accumulate;
        this.finish = finish;
    }

    /** The handler's class. */
    public Class<?> handled() {
        return handled;
    }

    /** The method that makes a group's empty state. */
    public Method initialize() {
        return initialize;
    }

    /** The method that folds a row's arguments into a state. */
    public Method accumulate() {
        return accumulate;
    }

    /** The method that turns a state into the group's result. */
    public Method finish() {
        return finish;
    }
}
