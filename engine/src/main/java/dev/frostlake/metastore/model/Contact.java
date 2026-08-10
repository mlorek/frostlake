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
 * Snowflake CONTACT — a schema-level object naming who to reach about an object, attached to tables
 * (and other objects) for a PURPOSE with {@code ALTER TABLE … SET CONTACT <purpose> = <contact>}.
 *
 * <p>It carries no behaviour: nothing about a contact changes how a query runs. What it does carry is
 * a small property surface — a single email address (the property is named for a list, but live takes
 * exactly one), a URL, and a comment.
 */
public class Contact extends SqlObject {

    private String url;
    private String emailDistributionList;

    public Contact(final String name) {
        super(name);
    }

    public String getUrl() { return url; }
    public void setUrl(final String url) { this.url = url; }

    public String getEmailDistributionList() { return emailDistributionList; }
    public void setEmailDistributionList(final String emailDistributionList) {
        this.emailDistributionList = emailDistributionList;
    }

    @Override
    public String getObjectType() { return "CONTACT"; }
}
