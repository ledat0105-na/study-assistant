package com.example.studyassistant.domain.presentation.media;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationDeckStatus;
import com.example.studyassistant.domain.presentation.PresentationMediaAsset;
import com.example.studyassistant.domain.presentation.PresentationMediaAssetRepository;
import com.example.studyassistant.domain.presentation.PresentationMediaLicenseStatus;
import com.example.studyassistant.domain.presentation.PresentationMediaSourceType;
import com.example.studyassistant.domain.presentation.PresentationSlideElementRepository;
import com.example.studyassistant.domain.presentation.dto.PresentationMediaAssetDto;
import com.example.studyassistant.entity.PlanFeature;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PresentationMediaService {
    private static final Duration ORPHAN_GRACE_PERIOD = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final PresentationDeckRepository deckRepository;
    private final PresentationMediaAssetRepository mediaRepository;
    private final PresentationSlideElementRepository elementRepository;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final MediaStorage mediaStorage;
    private final PresentationImageValidator imageValidator;
    private final Clock clock;

    public PresentationMediaAssetDto upload(User authenticatedUser, Long deckId, MultipartFile file) {
        assertOwnedWritableDeck(authenticatedUser.getId(), deckId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty");
        }
        if (file.getSize() > PresentationImageValidator.MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image file exceeds 10 MiB");
        }

        String originalFilename = safeDisplayFilename(file.getOriginalFilename());
        MediaStorage.StagedMedia staged;
        try (InputStream input = file.getInputStream()) {
            staged = mediaStorage.stage(input, PresentationImageValidator.MAX_FILE_BYTES);
        } catch (LocalMediaStorage.MediaFileTooLargeException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded image");
        }

        try {
            PresentationImageValidator.ValidatedImage image;
            try (InputStream stagedInput = mediaStorage.openStaged(staged)) {
                image = imageValidator.validate(originalFilename, file.getContentType(), staged.sizeBytes(), stagedInput);
            } catch (PresentationImageValidator.InvalidPresentationImageException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded image could not be decoded");
            }
            String contentHash = sha256(staged);

            PresentationMediaAssetDto result = transactionTemplate.execute(status -> {
                userRepository.findByIdForUpdate(authenticatedUser.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
                PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, authenticatedUser.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
                rejectArchived(deck);
                enforceAggregateQuota(authenticatedUser, staged.sizeBytes());

                try {
                    mediaStorage.promote(staged);
                } catch (IOException e) {
                    throw new StorageOperationException("Could not commit uploaded image to media storage", e);
                }

                PresentationMediaAsset asset = new PresentationMediaAsset();
                asset.setOwner(userRepository.getReferenceById(authenticatedUser.getId()));
                asset.setDeckId(deck.getId());
                asset.setSourceType(PresentationMediaSourceType.TEACHER_UPLOAD);
                asset.setOriginalFileName(originalFilename);
                asset.setStoredFileKey(staged.key());
                asset.setMimeType(image.mimeType());
                asset.setSizeBytes(staged.sizeBytes());
                asset.setSha256(contentHash);
                asset.setWidth(image.width());
                asset.setHeight(image.height());
                asset.setLicenseStatus(PresentationMediaLicenseStatus.USER_PROVIDED);
                asset.setCreatedAt(LocalDateTime.now(clock));
                asset = mediaRepository.saveAndFlush(asset);
                return toDto(asset);
            });
            return result;
        } catch (RuntimeException e) {
            compensateUpload(staged.key(), e);
            throw e;
        } finally {
            try {
                mediaStorage.discard(staged);
            } catch (IOException ignored) {
            }
        }
    }

    public List<PresentationMediaAssetDto> list(User user, Long deckId) {
        requireOwnedDeck(user.getId(), deckId);
        return mediaRepository.findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(deckId, user.getId()).stream()
            .map(this::toDto)
                .toList();
    }

    public PresentationMediaAssetDto get(User user, Long deckId, Long mediaId) {
        requireOwnedDeck(user.getId(), deckId);
        PresentationMediaAsset asset = findOwnedAsset(user.getId(), deckId, mediaId);
        return toDto(asset);
    }

    public PresentationMediaContent openContent(User user, Long deckId, Long mediaId) {
        requireOwnedDeck(user.getId(), deckId);
        PresentationMediaAsset asset = findOwnedAsset(user.getId(), deckId, mediaId);
        try {
            return new PresentationMediaContent(asset.getMimeType(), asset.getSizeBytes(),
                    safeDispositionFilename(asset.getOriginalFileName()), mediaStorage.open(asset.getStoredFileKey()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media content not found");
        }
    }

    public void delete(User user, Long deckId, Long mediaId) {
        QuarantineState quarantineState = new QuarantineState();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                userRepository.findByIdForUpdate(user.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
                PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, user.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
                rejectArchived(deck);
                PresentationMediaAsset asset = findOwnedAsset(user.getId(), deckId, mediaId);
                if (elementRepository.existsByMediaAssetId(asset.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Media is referenced by a slide element");
                }
                try {
                    quarantineState.value = mediaStorage.quarantine(asset.getStoredFileKey());
                } catch (IOException e) {
                    throw new StorageOperationException("Could not quarantine media for deletion", e);
                }
                mediaRepository.delete(asset);
                mediaRepository.flush();
            });
        } catch (RuntimeException e) {
            restoreAfterRollback(quarantineState.value, e);
            throw e;
        }

        if (quarantineState.value != null) {
            try {
                mediaStorage.purge(quarantineState.value);
            } catch (IOException ignored) {
                // Startup reconciliation purges quarantined keys with no database row.
            }
        }
    }

    public void reconcileStorage() throws IOException {
        mediaStorage.reconcile(java.util.Set.copyOf(mediaRepository.findAllStoredFileKeys()), ORPHAN_GRACE_PERIOD);
    }

    private String sha256(MediaStorage.StagedMedia staged) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = mediaStorage.openStaged(staged);
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                digestInput.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new StorageOperationException("Could not hash staged media", e);
        }
    }

    private void enforceAggregateQuota(User user, long uploadSize) {
        PlanFeature feature = quotaService.getPlanFeature(user.getPlan());
        Long configuredLimit = feature.getMaxPresentationMediaStorageBytes();
        if (configuredLimit == null || configuredLimit < 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Presentation media quota is not configured");
        }
        long currentSize = mediaRepository.sumSizeBytesByOwnerId(user.getId());
        if (currentSize > configuredLimit || uploadSize > configuredLimit - currentSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Presentation media storage quota exceeded");
        }
    }

    private void assertOwnedWritableDeck(Long ownerId, Long deckId) {
        transactionTemplate.execute(status -> {
            userRepository.findByIdForUpdate(ownerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
            PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, ownerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
            rejectArchived(deck);
            return null;
        });
    }

    private PresentationDeck requireOwnedDeck(Long ownerId, Long deckId) {
        return deckRepository.findByIdAndOwnerId(deckId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
    }

    private PresentationMediaAsset findOwnedAsset(Long ownerId, Long deckId, Long mediaId) {
        return mediaRepository.findByIdAndDeckIdAndOwnerId(mediaId, deckId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
    }

    private void rejectArchived(PresentationDeck deck) {
        if (deck.getStatus() == PresentationDeckStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck is archived");
        }
    }

    private PresentationMediaAssetDto toDto(PresentationMediaAsset asset) {
        return new PresentationMediaAssetDto(asset.getId(), asset.getDeckId(), asset.getSourceType().name(),
                asset.getOriginalFileName(), asset.getMimeType(), asset.getSizeBytes(), asset.getWidth(),
                asset.getHeight(), asset.getSha256(), asset.getCreatedAt(),
                "/api/teacher/presentations/" + asset.getDeckId() + "/media/" + asset.getId() + "/content");
    }

    private String safeDisplayFilename(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image filename is required");
        }
        String basename = supplied.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        basename = basename.replaceAll("[\\p{Cntrl}]", "").trim();
        if (basename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image filename is invalid");
        }
        if (basename.length() > 255) {
            int dot = basename.lastIndexOf('.');
            String extension = dot > 0 ? basename.substring(dot) : "";
            int allowedBase = 255 - extension.length();
            basename = basename.substring(0, Math.max(1, allowedBase)) + extension;
        }
        return basename;
    }

    private String safeDispositionFilename(String original) {
        return original.replaceAll("[\\p{Cntrl}\"\\\\/;]", "_");
    }

    private void compensateUpload(String key, RuntimeException failure) {
        try {
            boolean exists = Boolean.TRUE.equals(transactionTemplate.execute(status -> mediaRepository.existsByStoredFileKey(key)));
            if (!exists) mediaStorage.delete(key);
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void restoreAfterRollback(MediaStorage.QuarantinedMedia quarantined, RuntimeException failure) {
        if (quarantined == null) return;
        try {
            mediaStorage.restore(quarantined);
        } catch (IOException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private static final class QuarantineState {
        private MediaStorage.QuarantinedMedia value;
    }

    public static final class StorageOperationException extends RuntimeException {
        public StorageOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
