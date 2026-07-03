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

package dev.frostlake.metastore.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named file format object (CREATE FILE FORMAT) — a reusable set of parsing/formatting options (TYPE plus
 * format-specific options like FIELD_DELIMITER, SKIP_HEADER) that COPY statements reference via FORMAT_NAME.
 */
public class FileFormat {

    private String name;
    private String type;
    private final Map<String, String> options;
    private String comment;

    public FileFormat(final String name, final String type) {
        this.name = name;
        this.type = type != null ? type.toUpperCase() : "CSV";
        this.options = new LinkedHashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type != null ? type.toUpperCase() : null;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOption(final String key, final String value) {
        if (key != null) {
            options.put(key.toUpperCase(), value);
        }
    }

    public String getOption(final String key) {
        return key == null ? null : options.get(key.toUpperCase());
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }
}
