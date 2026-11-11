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
 * Snowflake PRIVACY POLICY: a policy of no arguments whose body answers a PRIVACY_BUDGET -
 * {@code NO_PRIVACY_POLICY()} or {@code PRIVACY_BUDGET(BUDGET_NAME => …)} - attached to a table with the entity key
 * its rows are counted by (live-verified).
 */
public class PrivacyPolicy extends SqlObject {

    private String body;

    public PrivacyPolicy(final String name, final String body) {
        super(name);
        this.body = body;
    }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    @Override
    public String getObjectType() { return "PRIVACY POLICY"; }
}
