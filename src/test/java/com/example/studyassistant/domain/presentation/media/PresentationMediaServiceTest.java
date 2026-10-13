package com.example.studyassistant.domain.presentation.media;

import com.example.studyassistant.domain.presentation.PresentationDeck;
import com.example.studyassistant.domain.presentation.PresentationDeckRepository;
import com.example.studyassistant.domain.presentation.PresentationDeckStatus;
import com.example.studyassistant.domain.presentation.PresentationMediaAsset;
import com.example.studyassistant.domain.presentation.PresentationMediaAssetRepository;
import com.example.studyassistant.domain.presentation.PresentationSlideElementRepository;
import com.example.studyassistant.domain.presentation.dto.PresentationMediaAssetDto;
import com.example.studyassistant.entity.PlanFeature;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresentationMediaServiceTest {
    private static final Long OWNER_ID = 71L;
    private static final Long DECK_ID = 91L;

    @TempDir
    Path tempDir;

    private User user;
    private PresentationDeck deck;
    private UserRepository userRepository;
    private PresentationDeckRepository deckRepository;
    private PresentationMediaAssetRepository mediaRepository;
    private PresentationSlideElementRepository elementRepository;
    private QuotaService quotaService;
    private LocalMediaStorage storage;
    private TransactionTemplate transactions;
    private PresentationMediaService service;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(OWNER_ID);
        user.setPlan("PREMIUM");
        user.setRole("TEACHER");
        deck = new PresentationDeck();
        deck.setStatus(PresentationDeckStatus.DRAFT);

        userRepository = mock(UserRepository.class);
        deckRepository = mock(PresentationDeckRepository.class);
        mediaRepository = mock(PresentationMediaAssetRepository.class);
        elementRepository = mock(PresentationSlideElementRepository.class);
        quotaService = mock(QuotaService.class);
        storage = new LocalMediaStorage(tempDir.resolve("media").toString());
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:media_service_tests;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new PresentationMediaService(userRepository, deckRepository, mediaRepository,
                elementRepository, quotaService, transactions, storage, new PresentationImageValidator(),
                Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC));

        when(userRepository.findByIdForUpdate(OWNER_ID)).thenReturn(java.util.Optional.of(user));
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user);
        when(deckRepository.findOwnedByIdForUpdate(DECK_ID, OWNER_ID)).thenReturn(java.util.Optional.of(deck));
        when(deckRepository.findByIdAndOwnerId(DECK_ID, OWNER_ID)).thenReturn(java.util.Optional.of(deck));
        when(mediaRepository.sumSizeBytesByOwnerId(OWNER_ID)).thenReturn(0L);
        when(mediaRepository.saveAndFlush(any(PresentationMediaAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PlanFeature plan = new PlanFeature();
        plan.setMaxPresentationMediaStorageBytes(32L * 1024 * 1024);
        when(quotaService.getPlanFeature("PREMIUM")).thenReturn(plan);
    }

    @Test
    void N_listIsScopedByBothOwnerAndDeck() {
        when(mediaRepository.findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(DECK_ID, OWNER_ID))
                .thenReturn(List.of());
        assertThat(service.list(user, DECK_ID)).isEmpty();
        verify(mediaRepository).findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(DECK_ID, OWNER_ID);
    }

    @Test
    void P_duplicateUploadsCreateSeparateLogicalAssets() throws Exception {
        byte[] png = pngBytes();
        service.upload(user, DECK_ID, multipart("duplicate.png", png));
        service.upload(user, DECK_ID, multipart("duplicate.png", png));
        org.mockito.ArgumentCaptor<PresentationMediaAsset> assets = org.mockito.ArgumentCaptor.forClass(PresentationMediaAsset.class);
        verify(mediaRepository, org.mockito.Mockito.times(2)).saveAndFlush(assets.capture());
        List<PresentationMediaAsset> created = assets.getAllValues();
        assertThat(created).extracting(PresentationMediaAsset::getSha256).containsOnly(created.get(0).getSha256());
        assertThat(created).extracting(PresentationMediaAsset::getStoredFileKey).doesNotHaveDuplicates();
        assertThat(created).allSatisfy(asset -> assertThat(Files.exists(tempDir.resolve("media/objects").resolve(asset.getStoredFileKey()))).isTrue());
    }

    @Test
    void R_databaseFailureCompensatesPromotedFile() throws Exception {
        when(mediaRepository.saveAndFlush(any(PresentationMediaAsset.class)))
                .thenThrow(new DataIntegrityViolationException("injected database failure"));
        assertThatThrownBy(() -> service.upload(user, DECK_ID, multipart("failure.png", pngBytes())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(Files.list(tempDir.resolve("media/objects")).toList()).isEmpty();
    }

    @Test
    void S_storageFailureLeavesNoAssetOrQuotaConsumption() throws Exception {
        LocalMediaStorage brokenStorage = org.mockito.Mockito.spy(storage);
        doThrow(new IOException("injected storage failure")).when(brokenStorage).promote(any());
        service = new PresentationMediaService(userRepository, deckRepository, mediaRepository,
                elementRepository, quotaService, transactions, brokenStorage, new PresentationImageValidator(),
                Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.upload(user, DECK_ID, multipart("storage-failure.png", pngBytes())))
                .isInstanceOf(PresentationMediaService.StorageOperationException.class);
        verify(mediaRepository, never()).saveAndFlush(any(PresentationMediaAsset.class));
        assertThat(Files.exists(tempDir.resolve("media/objects"))).isFalse();
    }

    @Test
    void F_actualStreamOverTenMiBIsRejectedAndStageIsRemoved() throws IOException {
        java.io.InputStream oversized = new java.io.InputStream() {
            private long remaining = PresentationImageValidator.MAX_FILE_BYTES + 1;

            @Override
            public int read() {
                if (remaining == 0) return -1;
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(length, remaining);
                java.util.Arrays.fill(buffer, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        };
        assertThatThrownBy(() -> storage.stage(oversized, PresentationImageValidator.MAX_FILE_BYTES))
                .isInstanceOf(LocalMediaStorage.MediaFileTooLargeException.class);
        try (var stagedFiles = Files.list(tempDir.resolve("media/staging"))) {
            assertThat(stagedFiles.toList()).isEmpty();
        }
    }

    @Test
    void T_referencedAssetDeletionReturnsConflict() {
        PresentationMediaAsset asset = mock(PresentationMediaAsset.class);
        when(asset.getId()).thenReturn(12L);
        when(asset.getStoredFileKey()).thenReturn("referenced-key");
        when(mediaRepository.findByIdAndDeckIdAndOwnerId(12L, DECK_ID, OWNER_ID)).thenReturn(java.util.Optional.of(asset));
        when(elementRepository.existsByMediaAssetId(12L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(user, DECK_ID, 12L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(mediaRepository, never()).delete(any(PresentationMediaAsset.class));
    }

    @Test
    void U_unreferencedDeleteRemovesDatabaseRowAndPhysicalFile() throws Exception {
        MediaStorage.StoredObject stored;
        try (var input = new java.io.ByteArrayInputStream(pngBytes())) {
            stored = storage.store(input, PresentationImageValidator.MAX_FILE_BYTES);
        }
        PresentationMediaAsset asset = asset(stored.key());
        when(mediaRepository.findByIdAndDeckIdAndOwnerId(14L, DECK_ID, OWNER_ID)).thenReturn(java.util.Optional.of(asset));
        when(elementRepository.existsByMediaAssetId(14L)).thenReturn(false);
        service.delete(user, DECK_ID, 14L);
        verify(mediaRepository).delete(asset);
        assertThat(Files.exists(tempDir.resolve("media/objects").resolve(stored.key()))).isFalse();
    }

    @Test
    void V_serverStorageKeyIsIndependentOfTraversalFilename() throws Exception {
        PresentationMediaAssetDto uploaded = service.upload(user, DECK_ID, multipart("../../escape.png", pngBytes()));
        org.mockito.ArgumentCaptor<PresentationMediaAsset> asset = org.mockito.ArgumentCaptor.forClass(PresentationMediaAsset.class);
        verify(mediaRepository).saveAndFlush(asset.capture());
        assertThat(uploaded.originalFilename()).isEqualTo("escape.png");
        assertThat(asset.getValue().getStoredFileKey()).matches("[0-9a-f-]{36}");
        assertThat(asset.getValue().getStoredFileKey()).doesNotContain("escape").doesNotContain("/").doesNotContain("\\");
    }

    @Test
    void X_archivedDeckRejectsMutationBeforeStaging() throws Exception {
        deck.setStatus(PresentationDeckStatus.ARCHIVED);
        assertThatThrownBy(() -> service.upload(user, DECK_ID, multipart("archived.png", pngBytes())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(mediaRepository, never()).sumSizeBytesByOwnerId(anyLong());
        assertThat(Files.exists(tempDir.resolve("media/staging"))).isFalse();
    }

    @Test
    void Y_aggregateQuotaAcceptsExactBoundaryAndRejectsNextByte() throws Exception {
        byte[] png = pngBytes();
        PlanFeature plan = new PlanFeature();
        plan.setMaxPresentationMediaStorageBytes((long) png.length);
        when(quotaService.getPlanFeature("PREMIUM")).thenReturn(plan);
        service.upload(user, DECK_ID, multipart("boundary.png", png));
        when(mediaRepository.sumSizeBytesByOwnerId(OWNER_ID)).thenReturn((long) png.length);
        assertThatThrownBy(() -> service.upload(user, DECK_ID, multipart("over.png", png)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void Z_reconciliationRemovesOnlyOrphansAndStaleStagingFiles() throws Exception {
        MediaStorage.StoredObject referenced;
        MediaStorage.StoredObject orphan;
        try (var input = new java.io.ByteArrayInputStream(pngBytes())) {
            referenced = storage.store(input, PresentationImageValidator.MAX_FILE_BYTES);
        }
        try (var input = new java.io.ByteArrayInputStream(pngBytes())) {
            orphan = storage.store(input, PresentationImageValidator.MAX_FILE_BYTES);
        }
        MediaStorage.StagedMedia abandoned = storage.stage(new java.io.ByteArrayInputStream(pngBytes()), PresentationImageValidator.MAX_FILE_BYTES);
        java.nio.file.attribute.FileTime oldTime = java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(java.time.Duration.ofDays(2)));
        Files.setLastModifiedTime(tempDir.resolve("media/objects").resolve(orphan.key()), oldTime);
        Files.setLastModifiedTime(tempDir.resolve("media/staging").resolve(abandoned.stagingToken() + ".stage"), oldTime);

        storage.reconcile(java.util.Set.of(referenced.key()), java.time.Duration.ofHours(24));

        assertThat(Files.exists(tempDir.resolve("media/objects").resolve(referenced.key()))).isTrue();
        assertThat(Files.exists(tempDir.resolve("media/objects").resolve(orphan.key()))).isFalse();
        assertThat(Files.exists(tempDir.resolve("media/staging").resolve(abandoned.stagingToken() + ".stage"))).isFalse();
    }

    @Test
    void quotaAndMutationsAcquireUserBeforeDeck() throws Exception {
        service.upload(user, DECK_ID, multipart("locks.png", pngBytes()));
        org.mockito.InOrder order = inOrder(userRepository, deckRepository);
        order.verify(userRepository).findByIdForUpdate(OWNER_ID);
        order.verify(deckRepository).findOwnedByIdForUpdate(DECK_ID, OWNER_ID);
    }

    private PresentationMediaAsset asset(String key) {
        PresentationMediaAsset asset = new PresentationMediaAsset();
        asset.setOwner(user);
        asset.setDeckId(DECK_ID);
        asset.setStoredFileKey(key);
        asset.setOriginalFileName("asset.png");
        asset.setMimeType("image/png");
        asset.setSizeBytes(1L);
        asset.setSha256("0".repeat(64));
        asset.setWidth(1);
        asset.setHeight(1);
        asset.setSourceType(com.example.studyassistant.domain.presentation.PresentationMediaSourceType.TEACHER_UPLOAD);
        asset.setLicenseStatus(com.example.studyassistant.domain.presentation.PresentationMediaLicenseStatus.USER_PROVIDED);
        asset.setCreatedAt(java.time.LocalDateTime.now());
        return asset;
    }

    private MockMultipartFile multipart(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "image/png", bytes);
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x42aabb);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }
}
