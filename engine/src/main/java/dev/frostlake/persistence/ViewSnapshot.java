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

/**
 * Serializable snapshot of view metadata
 */
public class ViewSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String query;
    public String comment;
    public Instant createdAt;
    // SECURE VIEW flag. Primitive → old snapshots deserialize it as false (a plain view).
    public boolean secure;
    // Explicit column list (CREATE VIEW v (a, b) AS …), or null when the view has none. Null on old snapshots.
    public List<String> columnNames;
    // Attached row access policy (ALTER VIEW ... ADD ROW ACCESS POLICY p ON (cols)). Null on old snapshots.
    public String rowAccessPolicyName;
    public List<String> rowAccessPolicyColumns;
}
