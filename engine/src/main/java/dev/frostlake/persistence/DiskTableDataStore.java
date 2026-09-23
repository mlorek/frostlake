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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@link TableDataStore} over a checkpoint's tables directory — one serialized file per table. */
public final class DiskTableDataStore implements TableDataStore {

    /** The subdirectory holding hidden permanent tables' rows; every table file ends in ".dat", so no file clashes. */
    private static final String SHADOWED_DIR = "shadowed";

    private final Path tablesDir;

    public DiskTableDataStore(final Path tablesDir) {
        this.tablesDir = tablesDir;
    }

    @Override
    public void save(final String database, final String schema, final String table,
                     final TableDataSnapshot data) throws IOException {
        write(fileFor(tablesDir, database, schema, table), data);
    }

    @Override
    public TableDataSnapshot load(final String database, final String schema, final String table)
            throws IOException, ClassNotFoundException {
        return read(fileFor(tablesDir, database, schema, table));
    }

    @Override
    public void saveShadowed(final String database, final String schema, final String table,
                             final TableDataSnapshot data) throws IOException {
        final Path dir = tablesDir.resolve(SHADOWED_DIR);
        Files.createDirectories(dir);
        write(fileFor(dir, database, schema, table), data);
    }

    @Override
    public TableDataSnapshot loadShadowed(final String database, final String schema, final String table)
            throws IOException, ClassNotFoundException {
        return read(fileFor(tablesDir.resolve(SHADOWED_DIR), database, schema, table));
    }

    private static void write(final Path tableFile, final TableDataSnapshot data) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tableFile)))) {
            oos.writeObject(data);
        }
    }

    private static TableDataSnapshot read(final Path tableFile) throws IOException, ClassNotFoundException {
        if (!Files.exists(tableFile)) {
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(tableFile)))) {
            return (TableDataSnapshot) ois.readObject();
        }
    }

    private static Path fileFor(final Path dir, final String database, final String schema, final String table) {
        return dir.resolve(String.format("%s_%s_%s.dat", database, schema, table));
    }
}
