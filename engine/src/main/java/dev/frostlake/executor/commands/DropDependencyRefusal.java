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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;

/**
 * A DROP … RESTRICT refused because another table's foreign key references what it would drop. IF EXISTS forgives a
 * missing object and never this: {@code DROP TABLE IF EXISTS p RESTRICT} is refused all the same (live-verified).
 */
final class DropDependencyRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param kind what the statement drops, as the sentence names it: {@code table}, {@code schema}
     */
    DropDependencyRefusal(final String kind) {
        super(SqlCompilationError.of("Cannot drop the " + kind + " because of dependencies"));
    }
}
