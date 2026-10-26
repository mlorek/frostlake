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
 * A handler class none of a routine's IMPORTS holds, as {@link JarHandlerLoader} finds it: a routine that calls it
 * answers as the account's class loader refuses the class (see {@link JavaHandlerFailure#missingClass}).
 */
public final class HandlerClassNotFound extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The class no import holds. */
    private final String className;

    HandlerClassNotFound(final String message, final String className, final Throwable cause) {
        super(message, cause);
        this.className = className;
    }

    /**
     * The class no import holds.
     *
     * @return its name
     */
    public String getClassName() {
        return className;
    }
}
