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

import java.util.Locale;

/**
 * One property a CREATE TASK or ALTER TASK … SET names, with the value it gives: the property's name as the
 * statement spells it, which a repeat is recognised by, and its upper-case form, which it is read by.
 */
final class TaskPropertySetting {

    private final String spelling;
    private final TaskPropertyValue value;

    /**
     * @param spelling the property's name as written
     * @param value    the value it is given
     */
    TaskPropertySetting(final String spelling, final TaskPropertyValue value) {
        this.spelling = spelling;
        this.value = value;
    }

    /** The property's name as the statement spells it. */
    String spelling() {
        return spelling;
    }

    /** The property's name in upper case. */
    String key() {
        return spelling.toUpperCase(Locale.ROOT);
    }

    /** The value the property is given. */
    TaskPropertyValue value() {
        return value;
    }
}
