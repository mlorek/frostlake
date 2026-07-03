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

import dev.frostlake.metastore.SqlObject;

import java.time.LocalDateTime;
import java.util.List;

public class MaterializedView extends SqlObject {

    private final String definition;
    private final List<String> columnNames;
    private String warehouse;
    private boolean suspended;
    private boolean secure = false;
    private LocalDateTime lastRefreshedTime;

    public MaterializedView(final String name, final String definition) {
        super(name);
        this.definition = definition;
        this.columnNames = null;
        this.suspended = false;
    }

    public MaterializedView(final String name, final List<String> columnNames, final String definition) {
        super(name);
        this.definition = definition;
        this.columnNames = columnNames;
        this.suspended = false;
    }

    public String getDefinition() {
        return definition;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public boolean hasExplicitColumnNames() {
        return columnNames != null && !columnNames.isEmpty();
    }

    public String getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(final String warehouse) {
        this.warehouse = warehouse;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(final boolean suspended) {
        this.suspended = suspended;
    }

    public LocalDateTime getLastRefreshedTime() {
        return lastRefreshedTime;
    }

    public void setLastRefreshedTime(final LocalDateTime lastRefreshedTime) {
        this.lastRefreshedTime = lastRefreshedTime;
    }

    public boolean isSecure() { return secure; }
    public void setSecure(final boolean secure) { this.secure = secure; }

    @Override
    public String getObjectType() {
        return "MATERIALIZED VIEW";
    }
}
