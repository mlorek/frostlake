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

package dev.frostlake.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of view metadata
 */
public class ViewSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String query;
    public String comment;
    /** The user whose DDL created the relation (LAST_DDL_BY); null in a snapshot taken before it was kept. */
    public String lastDdlBy;
    public Instant createdAt;
    // SECURE VIEW flag. Primitive → old snapshots deserialize it as false (a plain view).
    public boolean secure;
    // RECURSIVE VIEW flag and the body as written, which its DDL shows; `query` then holds the recursive CTE the view
    // runs as. False and null on snapshots written before recursive views, which were never recursive.
    public boolean recursive;
    public String writtenBody;
    // Explicit column list (CREATE VIEW v (a, b) AS …), or null when the view has none. Null on old snapshots.
    public List<String> columnNames;
    // Attached row access policy (ALTER VIEW ... ADD ROW ACCESS POLICY p ON (cols)). Null on old snapshots.
    public String rowAccessPolicyName;
    public List<String> rowAccessPolicyColumns;
    // The view's resolved columns — name and declared type — as frozen when it was created, so
    // INFORMATION_SCHEMA.COLUMNS still reports them after a restore. Only the type-carrying fields are
    // written: a view's columns have no DEFAULT, IDENTITY or key of their own. Null on old snapshots
    // (and whenever the defining query could not be resolved), in which case the view reports no
    // columns exactly as it did before they were captured.
    public List<ColumnSnapshot> columns;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
