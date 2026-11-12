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

package dev.frostlake.executor;

import dev.frostlake.functions.scalar.context.DefinitionOwnership;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.StreamSources;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;

/**
 * Whether the session sees a stream, which the functions that read one by name ask before they answer: a stream the
 * session does not see is refused as one that is not there.
 */
public final class StreamSight {

    private StreamSight() {
    }

    /**
     * Whether the session sees a stream: one it owns or holds a privilege on, in a database and a schema it uses.
     *
     * @param catalog the catalog
     * @param stream  the stream
     * @param sight   which objects the session sees, or null when every one is seen
     * @return true when the session sees it
     */
    public static boolean sees(final Catalog catalog, final Stream stream, final DefinitionOwnership sight) {
        final Schema home = StreamSources.homeOf(catalog, stream);
        return sight == null || home == null || sight.sees("STREAM", home.qualifiedName(stream.getName()),
            stream.getOwner());
    }
}
