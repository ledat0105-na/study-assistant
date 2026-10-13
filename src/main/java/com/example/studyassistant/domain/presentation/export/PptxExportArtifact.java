package com.example.studyassistant.domain.presentation.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public record PptxExportArtifact(Path spoolPath, long contentLength, String filename) implements AutoCloseable {
    public InputStream openStream() throws IOException {
        return Files.newInputStream(spoolPath);
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(spoolPath);
    }
}
