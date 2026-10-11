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

/**
 * A handler's class as one statement holds it: loaded for the statement and the place that calls the function,
 * so what its static fields hold is there for the statement's later rows and for nothing else.
 */
public final class JavaHandlerClass implements AutoCloseable {

    private final Class<?> handled;

    /**
     * @param handled the handler's class
     */
    public JavaHandlerClass(final Class<?> handled) {
        this.handled = handled;
    }

    /**
     * @return the handler's class
     */
    public Class<?> handled() {
        return handled;
    }

    @Override
    public void close() {
        // The class goes with its loader once nothing holds it.
    }
}
