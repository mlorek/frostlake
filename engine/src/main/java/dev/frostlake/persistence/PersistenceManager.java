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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.*;
import dev.frostlake.storage.StorageEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages persistence of catalog metadata and table data to disk
 */
public class PersistenceManager {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceManager.class);

    private final EngineConfig config;
    private final Path dataDirectory;
    private final Path catalogFile;
    private final Path tablesDirectory;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;

    public PersistenceManager(final EngineConfig config) {
        this.config = config;
        this.dataDirectory = Paths.get(config.getPersistenceDirectory());
        this.catalogFile = dataDirectory.resolve("catalog.dat");
        this.tablesDirectory = dataDirectory.resolve("tables");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = false;
    }

    /**
     * Initialize persistence directory structure
     */
    public void initialize() throws IOException {
        if (!config.isPersistenceEnabled()) {
            logger.info("Persistence is disabled");
            return;
        }

        // Create directories if they don't exist
        Files.createDirectories(dataDirectory);
        Files.createDirectories(tablesDirectory);
        logger.info("Persistence initialized at: {}", dataDirectory.toAbsolutePath());
    }

    /**
     * Start auto-save scheduler if enabled
     */
    public void startAutoSave(final Catalog catalog, final StorageEngine storageEngine) {
        if (!config.isPersistenceEnabled() || !config.isAutoSaveEnabled()) {
            return;
        }

        running = true;
        int interval = config.getSaveIntervalSeconds();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (running) {
                        saveCatalog(catalog, storageEngine);
                        logger.debug("Auto-save completed");
                    }
                } catch (final Exception e) {
                    logger.error("Auto-save failed", e);
                }
            }
        }, interval, interval, TimeUnit.SECONDS);

        logger.info("Auto-save enabled with interval: {} seconds", interval);
    }

    /**
     * Stop auto-save scheduler
     */
    public void stopAutoSave() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Write a full state snapshot (catalog + table data) into {@code dir}, regardless of the
     * {@code persistence.enabled} flag — the durable side of a WAL checkpoint. The WAL records this
     * snapshot's reference only after this returns, so a crash here just leaves an ignored orphan directory.
     */
    public void checkpointTo(final Path dir, final Catalog catalog, final StorageEngine storageEngine)
            throws IOException {
        final Path tablesDir = dir.resolve("tables");
        Files.createDirectories(tablesDir);
        CatalogSnapshotWriter.writeCatalogTo(dir.resolve("catalog.dat"), tablesDir, catalog, storageEngine);
    }

    /**
     * Restore a full state snapshot previously written by {@link #checkpointTo} into the live catalog +
     * storage, regardless of the {@code persistence.enabled} flag — used during WAL recovery.
     */
    public void restoreFrom(final Path dir, final Catalog catalog, final StorageEngine storageEngine)
            throws IOException, ClassNotFoundException {
        CatalogSnapshotReader.readCatalogFrom(dir.resolve("catalog.dat"), dir.resolve("tables"), catalog, storageEngine);
    }

    /**
     * Save catalog metadata to disk
     */
    public void saveCatalog(final Catalog catalog, final StorageEngine storageEngine) throws IOException {
        if (!config.isPersistenceEnabled()) {
            return;
        }
        CatalogSnapshotWriter.writeCatalogTo(catalogFile, tablesDirectory, catalog, storageEngine);
    }

    /**
     * Load catalog metadata from disk
     */
    public void loadCatalog(final Catalog catalog, final StorageEngine storageEngine) throws IOException, ClassNotFoundException {
        if (!config.isPersistenceEnabled()) {
            return;
        }
        CatalogSnapshotReader.readCatalogFrom(catalogFile, tablesDirectory, catalog, storageEngine);
    }

    /**
     * Clear all persisted data
     */
    public void clearAll() throws IOException {
        if (!config.isPersistenceEnabled()) {
            return;
        }

        // Delete catalog file
        Files.deleteIfExists(catalogFile);

        // Delete all table data files
        if (Files.exists(tablesDirectory)) {
            Files.walkFileTree(tablesDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) {
                    try {
                        Files.delete(path);
                    } catch (final IOException e) {
                        logger.warn("Could not delete file: {}", path, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        logger.info("All persisted data cleared");
    }
}
