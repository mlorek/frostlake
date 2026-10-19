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
package dev.frostlake.functions.table;

import dev.frostlake.metastore.Taggable;

import java.util.Collections;
import java.util.Map;

/** A column that is known by its name alone and carries no tag of its own: a view's. */
final class NamedColumn implements Taggable {

    private final String name;

    NamedColumn(final String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    @Override
    public void setTag(final String tagName, final String value) {
        throw new UnsupportedOperationException("a view's column carries no tag of its own");
    }

    @Override
    public void unsetTag(final String tagName) {
        // nothing to take off
    }

    @Override
    public String getTagValue(final String tagName) {
        return null;
    }

    @Override
    public Map<String, String> getTagValues() {
        return Collections.<String, String>emptyMap();
    }
}
