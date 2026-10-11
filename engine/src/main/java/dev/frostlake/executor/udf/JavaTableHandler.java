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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A Java table function's handler as its class declares it: the {@code process} method that reads a row, the
 * {@code endPartition} method when the class has one, and the public field of the output class behind each
 * declared column.
 */
public final class JavaTableHandler {

    private final Class<?> handled;
    private final Method process;
    private final Method endPartition;
    private final Field[] columns;

    /**
     * @param handled      the handler's class
     * @param process      the method that reads a row
     * @param endPartition the method called once a partition is through, or null
     * @param columns      the output class's field for each declared column, in the declaration's order
     */
    public JavaTableHandler(final Class<?> handled, final Method process, final Method endPartition,
                            final Field[] columns) {
        this.handled = handled;
        this.process = process;
        this.endPartition = endPartition;
        this.columns = columns;
    }

    public Class<?> handled() {
        return handled;
    }

    public Method process() {
        return process;
    }

    public Method endPartition() {
        return endPartition;
    }

    public Field[] columns() {
        return columns;
    }
}
