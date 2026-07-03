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

package dev.frostlake.metastore;

import java.util.Map;

/**
 * Implemented by catalog objects (and columns) that can carry Snowflake object tags applied via
 * {@code ALTER <object> SET TAG <tag> = '<value>'} / {@code ALTER <object> UNSET TAG <tag>}.
 */
public interface Taggable {

    /** Associate (or overwrite) a tag value on this object; {@code tagName} is the canonical upper-cased tag name. */
    void setTag(final String tagName, final String value);

    /** Remove a tag association from this object (no-op if absent). */
    void unsetTag(final String tagName);

    /** The value of the named tag on this object, or {@code null} if not set. */
    String getTagValue(final String tagName);

    /** All tag associations on this object (tag name -&gt; value). Named getTagValues to avoid clashing with Schema.getTags(). */
    Map<String, String> getTagValues();
}
