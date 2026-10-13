package com.example.studyassistant.domain.presentation.media;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Iterator;
import java.util.Locale;

@Component
public class PresentationImageValidator {
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_WIDTH = 8192;
    public static final int MAX_HEIGHT = 8192;
    public static final long MAX_PIXELS = 40_000_000L;
    private static final int SIGNATURE_LENGTH = 12;

    public record ValidatedImage(String mimeType, String extension, int width, int height) {}

    public ValidatedImage validate(String filename, String suppliedContentType, long sizeBytes, InputStream input) throws IOException {
        if (sizeBytes <= 0) {
            throw new InvalidPresentationImageException("Image file is empty");
        }
        if (sizeBytes > MAX_FILE_BYTES) {
            throw new InvalidPresentationImageException("Image file exceeds 10 MiB");
        }
        String extension = extension(filename);

        try (PushbackInputStream pushback = new PushbackInputStream(input, SIGNATURE_LENGTH)) {
            byte[] signature = pushback.readNBytes(SIGNATURE_LENGTH);
            pushback.unread(signature);
            String mimeType = mimeFromSignature(signature);
            if (!extensionMatches(extension, mimeType)) {
                throw new InvalidPresentationImageException("Filename extension does not match image content");
            }
            validateSuppliedMime(suppliedContentType, mimeType);

            try (ImageInputStream imageInput = ImageIO.createImageInputStream(pushback)) {
                if (imageInput == null) {
                    throw new InvalidPresentationImageException("Image reader could not open upload");
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) {
                    throw new InvalidPresentationImageException("Unsupported or corrupt image data");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(imageInput, true, true);
                    String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                    if (!formatMatches(format, mimeType)) {
                        throw new InvalidPresentationImageException("Image decoder format does not match signature");
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    validateDimensions(width, height);
                    var decoded = reader.read(0);
                    if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                        throw new InvalidPresentationImageException("Image could not be fully decoded");
                    }
                    decoded.flush();
                    return new ValidatedImage(mimeType, extensionFor(mimeType), width, height);
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_WIDTH || height > MAX_HEIGHT ||
                (long) width * height > MAX_PIXELS) {
            throw new InvalidPresentationImageException("Image dimensions exceed the supported limits");
        }
    }

    private String mimeFromSignature(byte[] signature) {
        if (signature.length >= 8 &&
                (signature[0] & 0xff) == 0x89 && signature[1] == 0x50 && signature[2] == 0x4e &&
                signature[3] == 0x47 && signature[4] == 0x0d && signature[5] == 0x0a &&
                signature[6] == 0x1a && signature[7] == 0x0a) {
            return "image/png";
        }
        if (signature.length >= 3 && (signature[0] & 0xff) == 0xff &&
                (signature[1] & 0xff) == 0xd8 && (signature[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (signature.length >= SIGNATURE_LENGTH && ascii(signature, 0, 4).equals("RIFF") &&
                ascii(signature, 8, 4).equals("WEBP")) {
            return "image/webp";
        }
        throw new InvalidPresentationImageException("Image signature is not PNG, JPEG, or WebP");
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255 ||
                filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new InvalidPresentationImageException("Invalid original filename");
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw new InvalidPresentationImageException("Image filename extension is required");
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("webp")) {
            throw new InvalidPresentationImageException("Only PNG, JPEG, and WebP images are supported");
        }
        return extension;
    }

    private boolean extensionMatches(String extension, String mimeType) {
        return switch (mimeType) {
            case "image/png" -> extension.equals("png");
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
    }

    private boolean formatMatches(String format, String mimeType) {
        return switch (mimeType) {
            case "image/png" -> format.equals("PNG");
            case "image/jpeg" -> format.equals("JPEG") || format.equals("JPG");
            case "image/webp" -> format.equals("WEBP");
            default -> false;
        };
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new InvalidPresentationImageException("Unsupported image MIME type");
        };
    }

    private void validateSuppliedMime(String supplied, String actual) {
        if (supplied == null || supplied.isBlank() || supplied.equalsIgnoreCase("application/octet-stream")) {
            return;
        }
        String normalized = supplied.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(actual)) {
            throw new InvalidPresentationImageException("Supplied content type does not match image content");
        }
    }

    public static final class InvalidPresentationImageException extends RuntimeException {
        public InvalidPresentationImageException(String message) {
            super(message);
        }
    }
}
