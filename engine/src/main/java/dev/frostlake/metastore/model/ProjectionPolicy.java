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
 * Snowflake PROJECTION POLICY — attached to a COLUMN, it decides whether that column may appear in a
 * query's select list at all. Unlike a masking policy, which rewrites the value, this one refuses the
 * projection outright; the column stays usable everywhere it is not projected (a WHERE, an ORDER BY,
 * an aggregate over it), all live-verified.
 *
 * <p>The policy takes no arguments — live refuses a signature with any — and its body evaluates to a
 * {@code PROJECTION_CONSTRAINT(ALLOW => …)}, so a conditional body is just an expression choosing
 * between two of them.
 */
public class ProjectionPolicy extends SqlObject {

    private String body;

    public ProjectionPolicy(final String name, final String body) {
        super(name);
        this.body = body;
    }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    @Override
    public String getObjectType() { return "PROJECTION POLICY"; }
}
