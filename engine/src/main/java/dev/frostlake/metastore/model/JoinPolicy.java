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
 * Snowflake JOIN POLICY — attached to a TABLE, it refuses to let that table be read on its own: the
 * query must INNER join it to a DIFFERENT table on an equality between their columns. A query with no
 * join, an outer join, a cross join, a constant join condition or a self join is all refused, each
 * with its own sentence (live-verified).
 *
 * <p>The policy takes no arguments and its body answers {@code JOIN_CONSTRAINT(JOIN_REQUIRED => …)},
 * so a body answering FALSE imposes nothing.
 */
public class JoinPolicy extends SqlObject {

    private String body;

    public JoinPolicy(final String name, final String body) {
        super(name);
        this.body = body;
    }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    @Override
    public String getObjectType() { return "JOIN POLICY"; }
}
