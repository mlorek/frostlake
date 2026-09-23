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

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The one-row {@code status} answer of a statement that produces no rows of its own, with its sentence. */
final class StatusResults {

    /** The sentence of a statement with no kind-specific wording. */
    static final String EXECUTED = "Statement executed successfully.";

    private StatusResults() {
    }

    /** A {@code status} result holding the sentence. */
    static ResultSet of(final String sentence) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("status", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList((Object) sentence)));
        return new ResultSet(columns, rows);
    }

    /** {@code <Kind> <NAME> successfully created.} */
    static ResultSet created(final String kind, final String name) {
        return of(kind + " " + name + " successfully created.");
    }

    /** {@code <NAME> already exists, statement succeeded.} */
    static ResultSet alreadyExists(final String name) {
        return of(name + " already exists, statement succeeded.");
    }

    /** {@code <NAME> successfully dropped.} */
    static ResultSet dropped(final String name) {
        return of(name + " successfully dropped.");
    }

    /** {@code Drop statement executed successfully (<NAME> already dropped).} */
    static ResultSet alreadyDropped(final String name) {
        return of("Drop statement executed successfully (" + name + " already dropped).");
    }
}
