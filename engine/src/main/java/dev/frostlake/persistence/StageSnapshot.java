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
import java.time.Instant;

/**
 * Serializable snapshot of a stage definition. The DEFINITION is what round-trips (name, type, url,
 * file format) — the local backing directory is recomputed on restore from the url through the
 * engine's S3 path resolver, exactly as CREATE STAGE does, so a restored engine resolves
 * {@code @stage/…} references (COPY, UDF IMPORTS) identically to the one that was checkpointed.
 */
public class StageSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String type;
    public String url;
    public String fileFormat;
    public boolean encryption;
    public String comment;
    public Instant createdAt;
}
