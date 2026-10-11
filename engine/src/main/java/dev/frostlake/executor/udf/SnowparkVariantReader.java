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

package dev.frostlake.executor.udf;

import tools.jackson.databind.JsonNode;

/**
 * Reads a handler's {@code Variant} on the engine's side: the kind it was made as and the value it holds, which
 * the class shows a handler through its readers only.
 */
public interface SnowparkVariantReader {

    /**
     * @param kind  what the value was made as
     * @param value the value's tree, or null for a value made from no text
     * @return what the reader makes of them
     */
    Object read(SnowparkVariantKind kind, JsonNode value);
}
