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

/**
 * Snowflake ROW ACCESS POLICY — a SQL boolean predicate that filters rows.
 * The policy body must return a BOOLEAN. Rows where the predicate is FALSE are hidden.
 * Example: CREATE ROW ACCESS POLICY dept_policy AS (dept STRING) RETURNS BOOLEAN ->
 *   CURRENT_ROLE() = 'ADMIN' OR dept = CURRENT_USER();
 */
public class RowAccessPolicy extends SqlObject {

    private final List<Parameter> parameters;  // columns from the table passed as args
    private final String body;                 // SQL boolean expression

    public RowAccessPolicy(final String name, final List<Parameter> parameters, final String body) {
        super(name);
        this.parameters = new ArrayList<>(parameters);
        this.body = body;
    }

    public List<Parameter> getParameters() { return new ArrayList<>(parameters); }
    public String getBody() { return body; }

    @Override public String getObjectType() { return "ROW ACCESS POLICY"; }
}
