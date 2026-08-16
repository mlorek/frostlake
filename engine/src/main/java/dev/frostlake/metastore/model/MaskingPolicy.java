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
 * Snowflake MASKING POLICY — transforms a column value based on the querying user's role.
 * The policy body is a SQL expression that receives the column value and returns the masked value.
 * Example: CREATE MASKING POLICY email_mask AS (val STRING) RETURNS STRING ->
 *   CASE WHEN CURRENT_ROLE() IN ('ANALYST') THEN val ELSE '***MASKED***' END;
 */
public class MaskingPolicy extends SqlObject {

    private final List<Parameter> parameters;  // typically one param: the column value
    private final String returnType;
    private final String body;                 // SQL CASE expression

    public MaskingPolicy(final String name, final List<Parameter> parameters,
                         final String returnType, final String body) {
        super(name);
        this.parameters = new ArrayList<>(parameters);
        this.returnType = returnType;
        this.body = body;
    }

    public List<Parameter> getParameters() { return new ArrayList<>(parameters); }
    public String getReturnType() { return returnType; }
    public String getBody() { return body; }

    @Override
    public String getObjectType() { return "MASKING POLICY"; }
}
