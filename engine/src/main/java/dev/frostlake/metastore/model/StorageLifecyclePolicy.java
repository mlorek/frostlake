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
 * Snowflake STORAGE LIFECYCLE POLICY: a policy of one argument or more whose BOOLEAN body says which of a table's rows
 * its lifecycle takes, attached to a table ON the columns its arguments read, with the archive tier and the days rows
 * are kept archived (live-verified).
 */
public class StorageLifecyclePolicy extends SqlObject {

    private final List<Parameter> parameters;
    private String body;
    private Integer archiveForDays;
    private String archiveTier;

    public StorageLifecyclePolicy(final String name, final List<Parameter> parameters, final String body) {
        super(name);
        this.parameters = new ArrayList<>(parameters);
        this.body = body;
    }

    /** @return the arguments, in the order the signature lists them */
    public List<Parameter> getParameters() { return new ArrayList<>(parameters); }
    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    /** @return the days rows are kept archived, or null where none is set */
    public Integer getArchiveForDays() { return archiveForDays; }
    public void setArchiveForDays(final Integer days) { this.archiveForDays = days; }

    /** @return the archive tier, COOL or COLD, or null where none is set */
    public String getArchiveTier() { return archiveTier; }
    public void setArchiveTier(final String tier) { this.archiveTier = tier; }

    @Override
    public String getObjectType() { return "STORAGE LIFECYCLE POLICY"; }
}
