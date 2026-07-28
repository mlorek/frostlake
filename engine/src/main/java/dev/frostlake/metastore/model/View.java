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

import java.util.ArrayList;
import java.util.List;

public class View extends SqlObject {

    private final String definition;
    private final List<String> columnNames;
    private boolean secure = false;
    /** Attached row access policy (ALTER VIEW ... ADD ROW ACCESS POLICY p ON (cols)), or null. */
    private String rowAccessPolicyName;
    private List<String> rowAccessPolicyColumns = new ArrayList<>();

    public View(final String name, final String definition) {
        super(name);
        this.definition = definition;
        this.columnNames = null;
    }

    public View(final String name, final List<String> columnNames, final String definition) {
        super(name);
        this.definition = definition;
        this.columnNames = columnNames;
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

    public boolean isSecure() { return secure; }
    public void setSecure(final boolean secure) { this.secure = secure; }

    public String getRowAccessPolicyName() { return rowAccessPolicyName; }
    public void setRowAccessPolicyName(final String name) { this.rowAccessPolicyName = name; }
    public List<String> getRowAccessPolicyColumns() { return rowAccessPolicyColumns; }
    public void setRowAccessPolicyColumns(final List<String> cols) {
        this.rowAccessPolicyColumns = new ArrayList<>(cols);
    }
    public boolean hasRowAccessPolicy() { return rowAccessPolicyName != null && !rowAccessPolicyName.isEmpty(); }

    /**
     * A full-fidelity duplicate for CLONE DATABASE/SCHEMA: keeps the explicit column list, the SECURE
     * flag, the comment and any attached row access policy (the name-and-definition constructor alone
     * silently dropped all of these from clones).
     */
    public View copy() {
        final View clone = columnNames != null
            ? new View(name, new ArrayList<>(columnNames), definition)
            : new View(name, definition);
        clone.setComment(comment);
        clone.secure = secure;
        clone.rowAccessPolicyName = rowAccessPolicyName;
        clone.rowAccessPolicyColumns = new ArrayList<>(rowAccessPolicyColumns);
        return clone;
    }

    /**
     * The view's full {@code CREATE OR REPLACE} statement rendered against {@code displayName}
     * (bare for GET_DDL's default output, schema-qualified for the metadata views). Snowflake
     * surfaces a view's executable DDL — not just its query — in
     * {@code INFORMATION_SCHEMA.VIEWS.VIEW_DEFINITION} and in SHOW VIEWS' {@code text} column;
     * deployment tooling relies on {@code EXECUTE IMMEDIATE} of that text to recreate views.
     */
    public String ddl(final String displayName) {
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace ");
        if (secure) {
            sb.append("secure ");
        }
        sb.append("view ").append(displayName);
        if (hasExplicitColumnNames()) {
            sb.append(" (").append(String.join(", ", columnNames)).append(")");
        }
        return sb.append(" as ").append(SqlObject.withoutTrailingSemicolon(definition)).append(";").toString();
    }

    @Override
    public String getObjectType() {
        return "VIEW";
    }
}
