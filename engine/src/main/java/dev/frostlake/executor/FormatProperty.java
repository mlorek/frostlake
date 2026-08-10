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


/** One property row: name, declared type, current-value default, DESC's default column. */
public class FormatProperty {
    public final String name;
    public final String type;
    public final String valueDefault;
    public final String shownDefault;

    FormatProperty(final String name, final String type, final String valueDefault,
                   final String shownDefault) {
        this.name = name;
        this.type = type;
        this.valueDefault = valueDefault;
        this.shownDefault = shownDefault;
    }
}
