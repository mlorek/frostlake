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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Snowpark Container Services objects a schema holds — image repositories, services and job services — and its
 * artifact repositories, each keyed by canonical name. They drop with their schema.
 */
public final class ContainerObjects {

    private final Map<String, ImageRepository> imageRepositories = new TreeMap<>();
    private final Map<String, ContainerService> services = new TreeMap<>();
    private final Map<String, ArtifactRepository> artifactRepositories = new TreeMap<>();

    /** The image repository of that name, or null. */
    public synchronized ImageRepository getImageRepository(final String name) {
        return imageRepositories.get(name);
    }

    /** Adds or replaces an image repository. */
    public synchronized void putImageRepository(final ImageRepository repository) {
        imageRepositories.put(repository.getName(), repository);
    }

    /** Removes an image repository; whether it existed. */
    public synchronized boolean removeImageRepository(final String name) {
        return imageRepositories.remove(name) != null;
    }

    /** Every image repository, by name. */
    public synchronized List<ImageRepository> getImageRepositories() {
        return new ArrayList<>(imageRepositories.values());
    }

    /** The service or job service of that name, or null. */
    public synchronized ContainerService getService(final String name) {
        return services.get(name);
    }

    /** Adds or replaces a service. */
    public synchronized void putService(final ContainerService service) {
        services.put(service.getName(), service);
    }

    /** Removes a service; whether it existed. */
    public synchronized boolean removeService(final String name) {
        return services.remove(name) != null;
    }

    /** Every service and job service, by name. */
    public synchronized List<ContainerService> getServices() {
        return new ArrayList<>(services.values());
    }

    /** The artifact repository of that name, or null. */
    public synchronized ArtifactRepository getArtifactRepository(final String name) {
        return artifactRepositories.get(name);
    }

    /** Adds or replaces an artifact repository. */
    public synchronized void putArtifactRepository(final ArtifactRepository repository) {
        artifactRepositories.put(repository.getName(), repository);
    }

    /** Removes an artifact repository; whether it existed. */
    public synchronized boolean removeArtifactRepository(final String name) {
        return artifactRepositories.remove(name) != null;
    }

    /** Every artifact repository, by name. */
    public synchronized List<ArtifactRepository> getArtifactRepositories() {
        return new ArrayList<>(artifactRepositories.values());
    }
}
