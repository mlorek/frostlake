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

package dev.frostlake.http;

/**
 * The optional body of {@code POST /api/sessions}: the database and schema the new session starts in,
 * each an identifier reference as it would be written in SQL — a bare name folds to upper case, a
 * double-quoted one keeps its case.
 */
public class SessionRequest {
    private String database;
    private String schema;

    public String getDatabase() {
        return database;
    }

    public void setDatabase(final String database) {
        this.database = database;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(final String schema) {
        this.schema = schema;
    }
}
