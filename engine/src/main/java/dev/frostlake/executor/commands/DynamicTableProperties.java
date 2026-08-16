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
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The properties ALTER DYNAMIC TABLE sets or unsets. The account refuses any other by name, as
 * {@code invalid property 'X' for 'DYNAMIC_TABLE'}, before it looks the table up and before it notices a
 * session with no current database: the refresh mode and INITIALIZE are fixed at creation, and
 * CHANGE_TRACKING or a name it does not know is no dynamic table property (live-verified).
 */
public final class DynamicTableProperties {

    private static final Set<String> TAKEN = new HashSet<>(Arrays.asList(
        "TARGET_LAG", "WAREHOUSE", "COMMENT", "DATA_RETENTION_TIME_IN_DAYS", "MAX_DATA_EXTENSION_TIME_IN_DAYS"));

    private DynamicTableProperties() {
    }

    /**
     * Refuse the first property the action names that ALTER DYNAMIC TABLE cannot set or unset, in the order
     * written.
     *
     * @param action the ALTER's action
     */
    public static void rejectUntakeable(final FrostlakeParser.DynamicTableActionContext action) {
        final List<FrostlakeParser.DynamicTablePropertyContext> named = new ArrayList<>();
        for (final FrostlakeParser.DynamicTableSettingContext setting : action.dynamicTableSetting()) {
            named.add(setting.dynamicTableProperty());
        }
        named.addAll(action.dynamicTableProperty());
        for (final FrostlakeParser.DynamicTablePropertyContext property : named) {
            final String name = property.getText().toUpperCase();
            if (!TAKEN.contains(name)) {
                throw new RuntimeException(
                    SqlCompilationError.of("invalid property '" + name + "' for 'DYNAMIC_TABLE'"));
            }
        }
    }
}
