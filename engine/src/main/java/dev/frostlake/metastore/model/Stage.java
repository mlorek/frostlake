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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.SqlObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Represents a Snowflake STAGE - a named location for staging data files.
 * Supports local filesystem staging for data loading and unloading operations.
 * S3 stages are simulated using local filesystem paths.
 */
public class Stage extends SqlObject {

    private final StageType type;
    private String url;                 // file:///path/to/dir or s3://bucket/path
    private String fileFormat;          // CSV, JSON, PARQUET, etc.
    private final boolean encryption;
    private String encryptionType;
    private S3PathResolver s3Resolver;  // resolver for recomputing localPath when ALTER STAGE SET URL changes it

    // Base directory for simulating S3 stages
    private static final String S3_SIMULATION_BASE = System.getProperty("user.home") + "/.frostlake_stages/s3";

    // For local filesystem stages (also used for S3 simulation)
    private Path localPath;

    // The FILE_FORMAT sub-options the stage was declared or altered with (SKIP_HEADER=1, …) beyond
    // the TYPE kept in fileFormat — DESC STAGE overlays them on the per-type defaults.
    private final Map<String, String> fileFormatOptions = new LinkedHashMap<>();
    // COPY_OPTIONS = (ON_ERROR='SKIP_FILE', …), likewise overlaid by DESC STAGE.
    private final Map<String, String> copyOptions = new LinkedHashMap<>();
    // DIRECTORY = (ENABLE = TRUE) — surfaced by DESC STAGE and SHOW STAGES' directory_enabled.
    private boolean directoryEnabled;

    public Map<String, String> getFileFormatOptions() {
        return fileFormatOptions;
    }

    public Map<String, String> getCopyOptions() {
        return copyOptions;
    }

    // The files the directory table has registered, by stage-relative path, as of the last REFRESH: each
    // file's directory row (RELATIVE_PATH, SIZE, LAST_MODIFIED, MD5, ETAG, FILE_URL).
    private final Map<String, List<Object>> directoryRegistry = new LinkedHashMap<>();

    /** The directory table's registered files by stage-relative path, as of the last REFRESH. */
    public Map<String, List<Object>> getDirectoryRegistry() {
        return directoryRegistry;
    }

    public boolean isDirectoryEnabled() {
        return directoryEnabled;
    }

    public void setDirectoryEnabled(final boolean directoryEnabled) {
        this.directoryEnabled = directoryEnabled;
    }

    public Stage(final String name, final StageType type, final String url) {
        this(name, type, url, "CSV", false, null, null);
    }

    public Stage(final String name, final StageType type, final String url, final String fileFormat,
                 final boolean encryption, final String comment) {
        this(name, type, url, fileFormat, encryption, comment, null);
    }

    public Stage(final String name, final StageType type, final String url, final String fileFormat,
                 final boolean encryption, final String comment, final S3PathResolver s3Resolver) {
        super(name);
        this.type = type;
        this.url = url;
        this.fileFormat = fileFormat;
        this.encryption = encryption;
        this.comment = comment;
        this.s3Resolver = s3Resolver;
        resolveLocalPath();
    }

    /**
     * (Re)compute the local directory backing this stage from its current url: {@code file://} maps directly,
     * {@code s3://} maps through the configured resolver (stage.s3.localMappings / .localRoot), else the
     * {@code ~/.frostlake_stages/s3} default. Creates the directory. Re-run when the url changes.
     */
    private void resolveLocalPath() {
        if (url == null) {
            this.localPath = null;
            return;
        }
        if (url.startsWith("file://")) {
            this.localPath = Paths.get(url.substring(7));
        } else if (url.startsWith("s3://")) {
            this.localPath = s3Resolver != null
                ? s3Resolver.toLocalPath(url)
                : Paths.get(S3_SIMULATION_BASE, url.substring(5));
        } else {
            this.localPath = null;
        }
        if (this.localPath != null) {
            try {
                Files.createDirectories(this.localPath);
            } catch (final IOException e) {
                throw new RuntimeException("Failed to create stage directory: " + this.localPath, e);
            }
        }
    }

    /** ALTER STAGE … SET URL — repoint the stage and recompute its local directory. */
    public void setUrl(final String url) {
        this.url = url;
        resolveLocalPath();
    }

    /** ALTER STAGE … SET FILE_FORMAT. */
    public void setFileFormat(final String fileFormat) {
        this.fileFormat = fileFormat;
    }

    S3PathResolver getS3Resolver() {
        return s3Resolver;
    }

    public StageType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public boolean isEncryption() {
        return encryption;
    }

    /** The declared ENCRYPTION TYPE (e.g. SNOWFLAKE_SSE), or null for the account default. */
    public String getEncryptionType() {
        return encryptionType;
    }

    public void setEncryptionType(final String encryptionType) {
        this.encryptionType = encryptionType;
    }

    public Instant getCreatedAt() {
        return createdTime;
    }

    public Path getLocalPath() {
        return localPath;
    }


    @Override
    public String getObjectType() {
        return "STAGE";
    }

    /**
     * List files in the stage
     */
    public List<StageFile> listFiles() throws IOException {
        return listFiles(null);
    }

    /**
     * List files in the stage with optional pattern
     */
    public List<StageFile> listFiles(final String pattern) throws IOException {
        if (localPath == null) {
            throw new UnsupportedOperationException("LIST only supported for file:// and s3:// stages");
        }

        if (!Files.exists(localPath)) {
            return new ArrayList<>();
        }

        final List<StageFile> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(localPath, 1)) {
            // Collect paths into list manually
            final List<Path> fileList = new ArrayList<>();
            final Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                final Path p = iterator.next();
                if (Files.isRegularFile(p)) {
                    if (pattern == null || matchPattern(p.getFileName().toString(), pattern)) {
                        fileList.add(p);
                    }
                }
            }

            for (final Path file : fileList) {
                final String fileName = file.getFileName().toString();
                final long size = Files.size(file);
                final String lastModified = Files.getLastModifiedTime(file).toInstant().toString();
                final String md5 = ""; // Could compute MD5 if needed

                files.add(new StageFile(fileName, size, lastModified, md5));
            }
        }

        return files;
    }

    /**
     * Simple pattern matching (supports * wildcard)
     */
    private boolean matchPattern(final String fileName, final String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }

        // Convert wildcard pattern to regex
        final String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");

        return fileName.matches(regex);
    }

    /**
     * Check if a file exists in the stage
     */
    public boolean fileExists(final String fileName) {
        if (localPath == null) {
            return false;
        }

        final Path filePath = localPath.resolve(fileName);
        return Files.exists(filePath);
    }

    /**
     * Get full path to a file in the stage
     */
    public Path getFilePath(final String fileName) {
        if (localPath == null) {
            throw new UnsupportedOperationException("getFilePath only supported for file:// and s3:// stages");
        }

        return localPath.resolve(fileName);
    }

    /**
     * Put a file into the stage
     */
    public void putFile(final Path sourceFile) throws IOException {
        if (localPath == null) {
            throw new UnsupportedOperationException("PUT only supported for file:// and s3:// stages");
        }

        final Path destFile = localPath.resolve(sourceFile.getFileName());
        Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Get a file from the stage
     */
    public void getFile(final String fileName, final Path destPath) throws IOException {
        if (localPath == null) {
            throw new UnsupportedOperationException("GET only supported for file:// and s3:// stages");
        }

        final Path sourceFile = localPath.resolve(fileName);
        if (!Files.exists(sourceFile)) {
            throw new IOException("File not found in stage: " + fileName);
        }

        Files.copy(sourceFile, destPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Remove a file from the stage
     */
    public boolean removeFile(final String fileName) throws IOException {
        if (localPath == null) {
            throw new UnsupportedOperationException("REMOVE only supported for file:// and s3:// stages");
        }

        final Path file = localPath.resolve(fileName);
        return Files.deleteIfExists(file);
    }

    /**
     * Check if this stage is using S3 simulation
     */
    public boolean isS3Simulated() {
        return url != null && url.startsWith("s3://");
    }

    /**
     * Get the S3 bucket name (if this is an S3 stage)
     */
    public String getS3Bucket() {
        if (!isS3Simulated()) {
            return null;
        }

        final String path = url.substring(5); // Remove "s3://"
        final int slashIndex = path.indexOf('/');
        if (slashIndex > 0) {
            return path.substring(0, slashIndex);
        }
        return path;
    }

    /**
     * Get the S3 prefix/path (if this is an S3 stage)
     */
    public String getS3Prefix() {
        if (!isS3Simulated()) {
            return null;
        }

        final String path = url.substring(5); // Remove "s3://"
        final int slashIndex = path.indexOf('/');
        if (slashIndex > 0 && slashIndex < path.length() - 1) {
            return path.substring(slashIndex + 1);
        }
        return "";
    }

    @Override
    public String toString() {
        return String.format("Stage{name='%s', type=%s, url='%s', format='%s'}",
            name, type, url, fileFormat);
    }
}
