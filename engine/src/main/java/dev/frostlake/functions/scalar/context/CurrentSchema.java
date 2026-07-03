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
import dev.frostlake.types.StringType;

import java.util.List;

public class CurrentSchema extends BuiltInFunction {
    private final Catalog catalog;

    public CurrentSchema(final Catalog catalog) {
        super("CURRENT_SCHEMA", StringType.VARCHAR);
        this.catalog = catalog;
    }

    @Override
    public Object evaluate(final List<Object> args) { return catalog.getCurrentSchema(); }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return 0; }
}
