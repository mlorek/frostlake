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

package dev.frostlake.metastore.model;

/**
 * Represents a file in a stage
 */
public class StageFile {
    private final String name;
    private final long size;
    private final String lastModified;
    private final String md5;

    public StageFile(final String name, final long size, final String lastModified, final String md5) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.md5 = md5;
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public String getLastModified() { return lastModified; }
    public String getMd5() { return md5; }

    @Override
    public String toString() {
        return String.format("%-50s %10d %s %s", name, size, lastModified, md5);
    }
}
