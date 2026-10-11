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
 * The instance of a handler's class kept for one call site of one statement (see {@link JavaHandlerInstances}
 * and {@link StatementHandlerStates}). Nothing is owed to it when the statement ends.
 */
public final class JavaHandlerInstance implements AutoCloseable {

    private final Object instance;

    public JavaHandlerInstance(final Object instance) {
        this.instance = instance;
    }

    public Object instance() {
        return instance;
    }

    @Override
    public void close() {
        // The instance is the collector's once its statement has ended.
    }
}
