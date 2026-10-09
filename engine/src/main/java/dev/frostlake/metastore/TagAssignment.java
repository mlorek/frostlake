/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.frostlake.metastore;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.Tag;

/** One tag set on an object: the tag, where it lives now, and the value it was given. */
public final class TagAssignment {

    private final String database;
    private final String schema;
    private final Tag tag;
    private final String value;

    /**
     * @param database the database the tag lives in
     * @param schema the schema the tag lives in
     * @param tag the tag
     * @param value the value the object gave it
     */
    public TagAssignment(final String database, final String schema, final Tag tag, final String value) {
        this.database = database;
        this.schema = schema;
        this.tag = tag;
        this.value = value;
    }

    /** The database the tag lives in. */
    public String getDatabase() {
        return database;
    }

    /** The schema the tag lives in. */
    public String getSchema() {
        return schema;
    }

    /** The tag. */
    public Tag getTag() {
        return tag;
    }

    /** The value the object gave the tag. */
    public String getValue() {
        return value;
    }

    /** The tag's full name as SQL text that reads back to it, each part quoted only where it has to be. */
    public String path() {
        return SqlIdentifiers.spellCanonicalEscaped(database) + "." + SqlIdentifiers.spellCanonicalEscaped(schema)
            + "." + SqlIdentifiers.spellCanonicalEscaped(tag.getName());
    }
}
