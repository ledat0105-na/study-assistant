package com.example.studyassistant.domain.presentation.media;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Set;

public interface MediaStorage {
    record StoredObject(String key, long sizeBytes) {}
    record StagedMedia(String key, String stagingToken, long sizeBytes) {}
    record QuarantinedMedia(String key, String quarantineToken) {}

    default StoredObject store(InputStream input, long maxBytes) throws IOException {
        StagedMedia staged = stage(input, maxBytes);
        try {
            promote(staged);
            return new StoredObject(staged.key(), staged.sizeBytes());
        } catch (IOException | RuntimeException e) {
            try {
                delete(staged.key());
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        } finally {
            discard(staged);
        }
    }

    StagedMedia stage(InputStream input, long maxBytes) throws IOException;
    InputStream openStaged(StagedMedia stagedMedia) throws IOException;
    void promote(StagedMedia stagedMedia) throws IOException;
    void discard(StagedMedia stagedMedia) throws IOException;
    InputStream open(String key) throws IOException;
    void delete(String key) throws IOException;
    QuarantinedMedia quarantine(String key) throws IOException;
    void restore(QuarantinedMedia quarantinedMedia) throws IOException;
    void purge(QuarantinedMedia quarantinedMedia) throws IOException;
    void reconcile(Set<String> databaseKeys, Duration gracePeriod) throws IOException;
}
