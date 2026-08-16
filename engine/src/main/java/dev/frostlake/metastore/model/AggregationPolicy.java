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

/**
 * Snowflake AGGREGATION POLICY — attached to a TABLE, it forbids reading individual rows: a query must
 * aggregate, may not use the aggregates that would leak a single value (MIN, MAX, MEDIAN, MODE,
 * ANY_VALUE, VARIANCE, LISTAGG, ARRAY_AGG, PERCENTILE_*), and sees groups smaller than the policy's
 * minimum folded into one remainder group. All live-verified.
 *
 * <p>The policy takes no arguments and its body answers either {@code AGGREGATION_CONSTRAINT(
 * MIN_GROUP_SIZE => n)} or {@code NO_AGGREGATION_CONSTRAINT()}, so a conditional body is an expression
 * choosing between them.
 */
public class AggregationPolicy extends SqlObject {

    private String body;

    public AggregationPolicy(final String name, final String body) {
        super(name);
        this.body = body;
    }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    @Override
    public String getObjectType() { return "AGGREGATION POLICY"; }
}
