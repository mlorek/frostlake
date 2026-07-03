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

package dev.frostlake.persistence;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable snapshot of a named FILE FORMAT — its TYPE plus the format-specific options map
 * (insertion order preserved).
 */
public class FileFormatSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String type;
    public Map<String, String> options = new LinkedHashMap<>();
    public String comment;
}
