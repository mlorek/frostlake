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

/** Where a session variable stands as a property's value: the property's name and how it judges the value. */
final class VariablePropertyPlace {

    private final String name;
    private final VariablePropertyKind kind;

    /**
     * @param name the property's name, upper-cased
     * @param kind how it judges a variable's value
     */
    VariablePropertyPlace(final String name, final VariablePropertyKind kind) {
        this.name = name;
        this.kind = kind;
    }

    String name() {
        return name;
    }

    VariablePropertyKind kind() {
        return kind;
    }
}
