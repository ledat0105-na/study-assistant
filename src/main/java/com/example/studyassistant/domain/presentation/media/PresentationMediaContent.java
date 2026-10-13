package com.example.studyassistant.domain.presentation.media;

import java.io.InputStream;

public record PresentationMediaContent(String mimeType, long contentLength, String filename, InputStream inputStream) {}