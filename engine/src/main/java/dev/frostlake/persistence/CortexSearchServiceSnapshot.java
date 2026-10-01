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

/**
 * A Cortex search service as it survives a restart: the whole definition, since nothing about it is
 * derived from data the engine keeps elsewhere. Nothing indexed is saved — there is no index to save.
 */
public class CortexSearchServiceSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String searchColumn;
    public List<String> attributeColumns = new ArrayList<>();
    public List<String> columns = new ArrayList<>();
    public String warehouse;
    public String targetLag;
    public String embeddingModel;
    public String definition;
    public String comment;
    public String owner;
    public boolean indexingSuspended;
    public boolean servingSuspended;
}
