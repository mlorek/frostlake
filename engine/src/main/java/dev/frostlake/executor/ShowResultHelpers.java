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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import java.util.Arrays;
import java.util.List;

/**
 * Small stateless helpers shared by more than one SHOW/DESCRIBE sub-executor. Kept in one place so the
 * describe-result plumbing is not duplicated across the {@code Show*Executor} family.
 */
final class ShowResultHelpers {

    private ShowResultHelpers() {
    }

    /** Build a two-column (property, value) describe result. */
    static ResultSet propertyValueResult(final List<Row> rows) {
        return new ResultSet(Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)), rows);
    }

    /** Current database.schema for describe lookups. */
    static Schema resolveDescribeSchema(final Catalog catalog) {
        return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
    }
}
