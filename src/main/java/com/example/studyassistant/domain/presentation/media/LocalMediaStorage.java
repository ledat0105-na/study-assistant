package com.example.studyassistant.domain.presentation.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class LocalMediaStorage implements MediaStorage {
    private final Path root;
    private final Path stagingDirectory;
    private final Path objectsDirectory;
    private final Path quarantineDirectory;

    public LocalMediaStorage(@Value("${presentation.media.storage-root:storage_private/presentation-media}") String storageRoot) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
        this.stagingDirectory = root.resolve("staging");
        this.objectsDirectory = root.resolve("objects");
        this.quarantineDirectory = root.resolve("quarantine");
    }

    @Override
    public StagedMedia stage(InputStream input, long maxBytes) throws IOException {
        Files.createDirectories(stagingDirectory);
        String key = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        Path stagedPath = stagingPath(token);
        long copied = 0;
        byte[] buffer = new byte[64 * 1024];
        try (OutputStream output = Files.newOutputStream(stagedPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > maxBytes - copied) {
                    throw new MediaFileTooLargeException();
                }
                output.write(buffer, 0, read);
                copied += read;
            }
            return new StagedMedia(key, token, copied);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(stagedPath);
            throw e;
        }
    }

    @Override
    public InputStream openStaged(StagedMedia stagedMedia) throws IOException {
        return Files.newInputStream(stagingPath(stagedMedia.stagingToken()));
    }

    @Override
    public void promote(StagedMedia stagedMedia) throws IOException {
        Files.createDirectories(objectsDirectory);
        atomicMove(stagingPath(stagedMedia.stagingToken()), objectPath(stagedMedia.key()));
    }

    @Override
    public void discard(StagedMedia stagedMedia) throws IOException {
        Files.deleteIfExists(stagingPath(stagedMedia.stagingToken()));
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(objectPath(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(objectPath(key));
    }

    @Override
    public QuarantinedMedia quarantine(String key) throws IOException {
        Files.createDirectories(quarantineDirectory);
        String token = UUID.randomUUID().toString();
        atomicMove(objectPath(key), quarantinePath(key, token));
        return new QuarantinedMedia(key, token);
    }

    @Override
    public void restore(QuarantinedMedia quarantinedMedia) throws IOException {
        Files.createDirectories(objectsDirectory);
        atomicMove(quarantinePath(quarantinedMedia.key(), quarantinedMedia.quarantineToken()), objectPath(quarantinedMedia.key()));
    }

    @Override
    public void purge(QuarantinedMedia quarantinedMedia) throws IOException {
        Files.deleteIfExists(quarantinePath(quarantinedMedia.key(), quarantinedMedia.quarantineToken()));
    }

    @Override
    public void reconcile(Set<String> databaseKeys, Duration gracePeriod) throws IOException {
        Instant cutoff = Instant.now().minus(gracePeriod);
        Files.createDirectories(stagingDirectory);
        Files.createDirectories(objectsDirectory);
        Files.createDirectories(quarantineDirectory);
        removeStaleStaging(cutoff);
        reconcileObjects(databaseKeys, cutoff);
        reconcileQuarantine(databaseKeys);
    }

    private void removeStaleStaging(Instant cutoff) throws IOException {
        try (Stream<Path> paths = Files.list(stagingDirectory)) {
            for (Path path : paths.toList()) {
                if (isOlderThan(path, cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void reconcileObjects(Set<String> databaseKeys, Instant cutoff) throws IOException {
        try (Stream<Path> paths = Files.list(objectsDirectory)) {
            for (Path path : paths.toList()) {
                String key = path.getFileName().toString();
                if (isValidKey(key) && !databaseKeys.contains(key) && isOlderThan(path, cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void reconcileQuarantine(Set<String> databaseKeys) throws IOException {
        try (Stream<Path> paths = Files.list(quarantineDirectory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                int separator = name.indexOf('.');
                if (separator != 36 || !isValidKey(name.substring(0, separator))) {
                    continue;
                }
                String key = name.substring(0, separator);
                if (databaseKeys.contains(key) && !Files.exists(objectPath(key))) {
                    Files.createDirectories(objectsDirectory);
                    atomicMove(path, objectPath(key));
                } else {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private boolean isOlderThan(Path path, Instant cutoff) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        return attributes.lastModifiedTime().toInstant().isBefore(cutoff);
    }

    private Path stagingPath(String token) {
        return stagingDirectory.resolve(requireKey(token) + ".stage");
    }

    private Path objectPath(String key) {
        return objectsDirectory.resolve(requireKey(key));
    }

    private Path quarantinePath(String key, String token) {
        return quarantineDirectory.resolve(requireKey(key) + "." + requireKey(token) + ".trash");
    }

    private String requireKey(String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Invalid server media key");
        }
        return key;
    }

    private boolean isValidKey(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void atomicMove(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Storage filesystem does not support atomic media moves", e);
        }
    }

    public static final class MediaFileTooLargeException extends IOException {
        public MediaFileTooLargeException() {
            super("Media file exceeds the configured byte limit");
        }
    }
}
