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

package dev.frostlake.functions;

/**
 * ServiceLoader SPI for optional function packs. The engine's {@link FunctionRegistry} discovers
 * every provider on the classpath after registering its own built-ins and asks each to contribute —
 * the same optional-module pattern as the {@code StageFileReader} COPY readers and the
 * {@code UdfLanguageRuntime} language runtimes. The {@code frostlake-geo} module contributes the
 * GEOGRAPHY / GEOMETRY ST_ functions this way; the engine itself keeps only the type surface.
 */
public interface FunctionProvider {

    /** Register this provider's functions (and aliases) into the given registry. */
    void contribute(final FunctionRegistry registry);
}
