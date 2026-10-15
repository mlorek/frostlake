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
    private String body;                       // SQL boolean expression
    private boolean exemptOtherPolicies;

    public RowAccessPolicy(final String name, final List<Parameter> parameters, final String body) {
        super(name);
        this.parameters = new ArrayList<>(parameters);
        this.body = body;
    }

    public List<Parameter> getParameters() { return new ArrayList<>(parameters); }
    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    /** Whether the CREATE set EXEMPT_OTHER_POLICIES TRUE, which a row access policy takes and its DDL writes. */
    public boolean isExemptOtherPolicies() { return exemptOtherPolicies; }

    /** Records EXEMPT_OTHER_POLICIES as the CREATE set it. */
    public void setExemptOtherPolicies(final boolean exemptOtherPolicies) {
        this.exemptOtherPolicies = exemptOtherPolicies;
    }

    @Override
    public String getObjectType() { return "ROW ACCESS POLICY"; }
}
