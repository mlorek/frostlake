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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The properties of an EXECUTE TASK … USING, judged before the task is looked up (live-verified). A name given twice
 * is "duplicate property 'CONFIG';". Then the first name the account's own order of the names reaches that no run
 * takes - CONFIG is the one a run does - is "invalid property 'foo' for 'TASK'", named as written. Then CONFIG's
 * value: a text, a name, NULL or a list of properties is taken, a number or a switch value is an invalid value of the
 * parameter, and a list of constants is "Invalid value specified for property 'CONFIG'".
 */
public final class TaskRunProperties {

    private static final String CONFIG = "CONFIG";

    private TaskRunProperties() {
    }

    /**
     * The configuration a run is given.
     *
     * @param properties the properties as written
     * @return the configuration's text, or null for none
     */
    public static String config(final List<FrostlakeParser.TaskUsingPropertyContext> properties) {
        // The account holds the names in a hash map: its order is the one the name it refuses is found in.
        final Map<String, FrostlakeParser.TaskUsingPropertyContext> named = new HashMap<>();
        for (final FrostlakeParser.TaskUsingPropertyContext property : properties) {
            final String name = SqlIdentifiers.canonical(property.identifier());
            if (named.put(name, property) != null) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(name));
            }
        }
        for (final Map.Entry<String, FrostlakeParser.TaskUsingPropertyContext> entry : named.entrySet()) {
            if (!CONFIG.equals(entry.getKey())) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(
                    entry.getValue().identifier().getText(), "TASK"));
            }
        }
        final FrostlakeParser.TaskUsingPropertyContext config = named.get(CONFIG);
        if (config.NULL() != null) {
            return null;
        }
        final TaskPropertyValue value = TaskPropertyValue.of(config.taskPropertyValue());
        if (value.isNumeric() || value.isBoolean()) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.written(), CONFIG));
        }
        if (value.isList()) {
            throw new RuntimeException("Invalid value specified for property '" + CONFIG + "'");
        }
        return value.text();
    }
}
