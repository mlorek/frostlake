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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * NEXTVAL function - gets the next value from a sequence
 * Usage: NEXTVAL('sequence_name') or sequence_name.NEXTVAL
 */
public class NextVal extends BuiltInFunction {

    private final Catalog catalog;

    public NextVal(final Catalog catalog) {
        super("NEXTVAL", NumericType.BIGINT);
        this.catalog = catalog;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args == null || args.isEmpty()) {
            throw new RuntimeException("NEXTVAL requires a sequence name argument");
        }

        String sequenceName = args.get(0).toString();

        // Remove quotes if present
        if (sequenceName.startsWith("'") && sequenceName.endsWith("'")) {
            sequenceName = sequenceName.substring(1, sequenceName.length() - 1);
        }

        // Resolve sequence from current schema
        String dbName = catalog.getCurrentDatabase();
        String schemaName = catalog.getCurrentSchema();

        if (dbName == null || schemaName == null) {
            throw new RuntimeException("No database or schema selected");
        }

        try {
            Sequence sequence = catalog.getDatabase(dbName).getSchema(schemaName).getSequence(sequenceName);
            return sequence.nextVal();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to get next value from sequence '" + sequenceName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
