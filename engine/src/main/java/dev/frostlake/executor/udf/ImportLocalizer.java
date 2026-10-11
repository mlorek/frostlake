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

import java.util.List;

/**
 * Gives a routine's imports as its handler loader reads them: a file an internal named stage keeps by its local
 * path, every other import as kept.
 */
public interface ImportLocalizer {

    /**
     * @param imports the imports a routine keeps
     * @return the imports, in the same order
     */
    List<String> localImports(List<String> imports);
}
