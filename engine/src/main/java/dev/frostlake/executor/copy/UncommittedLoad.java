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

package dev.frostlake.executor.copy;

import java.util.Set;

/**
 * The files one COPY statement of a transaction still open loaded into a table, which count as loaded for that
 * transaction alone until it commits (see {@link CopyCommandExecutor}).
 */
final class UncommittedLoad {

    private final String history;
    private final Set<String> keys;

    /**
     * @param history the table's key: its qualified name, marked when the table is temporary
     * @param keys    the load keys of the files
     */
    UncommittedLoad(final String history, final Set<String> keys) {
        this.history = history;
        this.keys = keys;
    }

    String history() {
        return history;
    }

    Set<String> keys() {
        return keys;
    }
}
