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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Serializable snapshot of a stored PROCEDURE (any language). */
public class ProcedureSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public List<ParameterSnapshot> parameters = new ArrayList<>();
    public String returnType;
    public String body;
    public String language;
    public String handler;
    public String runtimeVersion;
    public List<String> packages = new ArrayList<>();
    public String executeAs;
    public List<String> imports = new ArrayList<>();
    public String comment;
    public String owner;
    // RETURNS TABLE and its declared columns; false and null on old snapshots, which kept neither.
    public boolean returnsTable;
    public List<ParameterSnapshot> returnColumns;
    // The declared null handling and volatility; null on old snapshots, which read back as the defaults.
    public String nullHandling;
    public String volatility;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
