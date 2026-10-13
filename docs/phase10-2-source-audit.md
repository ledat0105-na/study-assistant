# Phase 10.2 — kiểm tra nguồn

Đối chiếu trực tiếp production source ngày 2026-10-12. Không truy cập MySQL hoặc OpenAI thật. V12 không sửa; V13 chỉ được tạo thành tệp.

## Phân loại các kết quả tìm kiếm

### [src/main/java/com/example/studyassistant/controller/DocumentController.java](D:/study-assistant/src/main/java/com/example/studyassistant/controller/DocumentController.java)

Đọc văn bản cho API tài liệu đã kiểm tra quyền; không sinh mind map và không gọi AI ở hit này.

- Dòng 184: `"text", doc.getExtractedText() != null ? doc.getExtractedText() : ""`

### [src/main/java/com/example/studyassistant/domain/flashcard/FlashcardReviewService.java](D:/study-assistant/src/main/java/com/example/studyassistant/domain/flashcard/FlashcardReviewService.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 12: `import org.springframework.dao.DataIntegrityViolationException;`
- Dòng 45: `} catch (DataIntegrityViolationException e) {`

### [src/main/java/com/example/studyassistant/domain/flashcard/generation/BoundedActiveChunkSelector.java](D:/study-assistant/src/main/java/com/example/studyassistant/domain/flashcard/generation/BoundedActiveChunkSelector.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 18: `* retrieval. Using a bounded selection of all active chunks (matching activeIndexVersion),`
- Dòng 23: `* activeIndexVersion are included — stale or re-indexed chunks are excluded.`

### [src/main/java/com/example/studyassistant/domain/flashcard/generation/GenerationIndexSnapshot.java](D:/study-assistant/src/main/java/com/example/studyassistant/domain/flashcard/generation/GenerationIndexSnapshot.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 13: `String activeIndexVersion,`
- Dòng 25: `return doc.getActiveIndexVersion().equals(activeIndexVersion)`

### [src/main/java/com/example/studyassistant/domain/flashcard/generation/GroundedFlashcardGenerationService.java](D:/study-assistant/src/main/java/com/example/studyassistant/domain/flashcard/generation/GroundedFlashcardGenerationService.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 95: `doc.getId(), snapshot.activeIndexVersion());`
- Dòng 100: `" indexVersion=" + snapshot.activeIndexVersion());`
- Dòng 442: `if (!chunk.getIndexVersion().equals(snapshot.activeIndexVersion())) {`

### [src/main/java/com/example/studyassistant/domain/presentation/generation/PresentationOutlineGenerationWorker.java](D:/study-assistant/src/main/java/com/example/studyassistant/domain/presentation/generation/PresentationOutlineGenerationWorker.java)

Chỉ là comment nói không dùng extractedText; luồng presentation ngoài phạm vi.

- Dòng 50: `* No doc.getExtractedText() usage.`

### [src/main/java/com/example/studyassistant/domain/quiz/submission/QuizAttemptSubmissionService.java](D:/study-assistant/src/main/java/com/example/studyassistant/domain/quiz/submission/QuizAttemptSubmissionService.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 9: `import org.springframework.dao.DataIntegrityViolationException;`
- Dòng 205: `} catch (DataIntegrityViolationException e) {`

### [src/main/java/com/example/studyassistant/entity/Document.java](D:/study-assistant/src/main/java/com/example/studyassistant/entity/Document.java)

Metadata active index và quy trình xóa mềm đã có; ngoài sửa KG lượt này.

- Dòng 73: `private String activeIndexVersion;`

### [src/main/java/com/example/studyassistant/repository/ConceptRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/ConceptRepository.java)

Lọc provenance active index ở query; giữ các điều kiện READY, document/index và notebook scope hiện có.

- Dòng 28: `AND d.activeIndexVersion IS NOT NULL`
- Dòng 29: `AND dc.indexVersion = d.activeIndexVersion`

### [src/main/java/com/example/studyassistant/repository/ConceptSourceRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/ConceptSourceRepository.java)

Lọc provenance active index ở query; giữ các điều kiện READY, document/index và notebook scope hiện có.

- Dòng 34: `"AND d.activeIndexVersion IS NOT NULL " +`
- Dòng 35: `"AND dc.indexVersion = d.activeIndexVersion")`

### [src/main/java/com/example/studyassistant/repository/KnowledgeGraphQueryRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/KnowledgeGraphQueryRepository.java)

Lọc provenance active index ở query; giữ các điều kiện READY, document/index và notebook scope hiện có.

- Dòng 33: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 58: `"AND dc.indexVersion = d.activeIndexVersion")`
- Dòng 74: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 91: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 109: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 128: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 144: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 160: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 175: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 194: `"AND dc.indexVersion = d.activeIndexVersion " +`

### [src/main/java/com/example/studyassistant/repository/StudentMasteryReadRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/StudentMasteryReadRepository.java)

Lọc provenance active index ở query; giữ các điều kiện READY, document/index và notebook scope hiện có.

- Dòng 31: `"AND dc.indexVersion = d.activeIndexVersion " +`
- Dòng 56: `"AND dc.indexVersion = d.activeIndexVersion " +`

### [src/main/java/com/example/studyassistant/repository/adaptive/AdaptiveCandidateRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/adaptive/AdaptiveCandidateRepository.java)

Lọc provenance active index ở query; giữ các điều kiện READY, document/index và notebook scope hiện có.

- Dòng 33: `AND d.activeIndexVersion IS NOT NULL`
- Dòng 34: `AND dc.indexVersion = d.activeIndexVersion`
- Dòng 60: `AND d.activeIndexVersion IS NOT NULL`
- Dòng 61: `AND dc.indexVersion = d.activeIndexVersion`
- Dòng 65: `WHERE csA.concept.id = r.sourceConcept.id AND dA.notebook.id = :notebookId AND dA.status = 'READY' AND dA.activeIndexVersion IS NOT NULL AND dcA.indexVersion = dA.activeIndexVersion`
- Dòng 69: `WHERE csB.concept.id = r.targetConcept.id AND dB.notebook.id = :notebookId AND dB.status = 'READY' AND dB.activeIndexVersion IS NOT NULL AND dcB.indexVersion = dB.activeIndexVersion`
- Dòng 86: `AND d.activeIndexVersion IS NOT NULL`
- Dòng 87: `AND dc.indexVersion = d.activeIndexVersion`

### [src/main/java/com/example/studyassistant/service/AIConceptExtractor.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/AIConceptExtractor.java)

Giới hạn candidate KG: tối đa 50 nguồn khái niệm/chunk; >50 candidate quan hệ có sẵn không gọi AI, trả degradation; node có bằng chứng vẫn dùng được.

- Dòng 153: `if (candidateLimit) finalStatus = "CANDIDATE_LIMIT_EXCEEDED";`

### [src/main/java/com/example/studyassistant/service/AIConceptRelationExtractor.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/AIConceptRelationExtractor.java)

Giới hạn candidate KG: tối đa 50 nguồn khái niệm/chunk; >50 candidate quan hệ có sẵn không gọi AI, trả degradation; node có bằng chứng vẫn dùng được.

- Dòng 98: `.status("CANDIDATE_LIMIT_EXCEEDED")`

### [src/main/java/com/example/studyassistant/service/ConceptRelationPersistenceHelper.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptRelationPersistenceHelper.java)

Race recovery KG: ConceptService/ConceptRelationService chỉ phục hồi khi classifier xác nhận lỗi unique. Comment helper mô tả ràng buộc unique hiện có; không thay đổi schema ở runtime.

- Dòng 38: `// Actually, let's just let it throw DataIntegrityViolationException if it exists, or check manually.`
- Dòng 40: `// Let's just create it directly, and rely on uk_source_target_type to throw DataIntegrityViolationException.`

### [src/main/java/com/example/studyassistant/service/ConceptRelationService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptRelationService.java)

Race recovery KG: ConceptService/ConceptRelationService chỉ phục hồi khi classifier xác nhận lỗi unique. Comment helper mô tả ràng buộc unique hiện có; không thay đổi schema ở runtime.

- Dòng 8: `import org.springframework.dao.DataIntegrityViolationException;`
- Dòng 56: `} catch (DataIntegrityViolationException e) {`
- Dòng 67: `} catch (DataIntegrityViolationException e) {`
- Dòng 76: `} catch (DataIntegrityViolationException e2) {`

### [src/main/java/com/example/studyassistant/service/ConceptService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptService.java)

Race recovery KG: ConceptService/ConceptRelationService chỉ phục hồi khi classifier xác nhận lỗi unique. Comment helper mô tả ràng buộc unique hiện có; không thay đổi schema ở runtime.

- Dòng 9: `import org.springframework.dao.DataIntegrityViolationException;`
- Dòng 50: `} catch (DataIntegrityViolationException original) {`
- Dòng 88: `} catch (DataIntegrityViolationException original) {`
- Dòng 106: `} catch (DataIntegrityViolationException original) {`
- Dòng 115: `} catch (DataIntegrityViolationException srcEx) {`

### [src/main/java/com/example/studyassistant/service/DocumentKnowledgeGraphGenerationService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/DocumentKnowledgeGraphGenerationService.java)

Giới hạn candidate KG: tối đa 50 nguồn khái niệm/chunk; >50 candidate quan hệ có sẵn không gọi AI, trả degradation; node có bằng chứng vẫn dùng được.

- Dòng 72: `case "CANDIDATE_LIMIT_EXCEEDED" -> GraphFailureCode.GRAPH_TOO_LARGE;`

### [src/main/java/com/example/studyassistant/service/DocumentService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/DocumentService.java)

Metadata active index và quy trình xóa mềm đã có; ngoài sửa KG lượt này.

- Dòng 219: `response.put("activeIndexVersion", doc.getActiveIndexVersion());`
- Dòng 269: `String activeIndexVersion = doc.getActiveIndexVersion();`
- Dòng 276: `Runnable cleanup = () -> cleanupDeletedDocument(documentId, activeIndexVersion, filePath);`
- Dòng 287: `private void cleanupDeletedDocument(Long documentId, String activeIndexVersion, String filePath) {`
- Dòng 288: `if (activeIndexVersion != null && !activeIndexVersion.isBlank()) {`
- Dòng 290: `documentIndexingService.cleanupVersion(documentId, activeIndexVersion);`

### [src/main/java/com/example/studyassistant/service/KnowledgeGraphQueryService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/KnowledgeGraphQueryService.java)

DTO document graph trả phiên bản hoạt động; query canonical kiểm tra KnowledgeScope trước khi đọc graph.

- Dòng 125: `.activeIndexVersion(document.getActiveIndexVersion())`

### [src/main/java/com/example/studyassistant/service/QuizSubmissionService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/QuizSubmissionService.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 16: `import org.springframework.dao.DataIntegrityViolationException;`
- Dòng 170: `} catch (DataIntegrityViolationException e) {`

### [src/main/java/com/example/studyassistant/service/TutorSessionService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/TutorSessionService.java)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 10: `import org.springframework.dao.DataIntegrityViolationException;`
- Dòng 62: `} catch (DataIntegrityViolationException e) {`

### [src/main/java/com/example/studyassistant/service/dto/KnowledgeGraphDto.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/dto/KnowledgeGraphDto.java)

DTO document graph trả phiên bản hoạt động; query canonical kiểm tra KnowledgeScope trước khi đọc graph.

- Dòng 18: `private String activeIndexVersion;`

### [src/main/resources/static/detail.html](D:/study-assistant/src/main/resources/static/detail.html)

GET topic cũ để đọc cây đã có. TopicController.getTopicsByDocument chỉ kiểm tra quyền và đọc repository, không sinh Topic/AI.

- Dòng 101: `const resTopics = await fetch('/api/topics/document/${docId}');`

### [src/main/resources/static/js/document-mind-map.js](D:/study-assistant/src/main/resources/static/js/document-mind-map.js)

Loader canonical; key docId:activeIndexVersion, kiểm tra phiên bản khi đọc/POST; không dùng topic legacy.

- Dòng 33: `const version = doc.activeIndexVersion;`
- Dòng 41: `if (doc.activeIndexVersion !== version || doc.status !== 'READY'`
- Dòng 42: `|| (graph && graph.activeIndexVersion && graph.activeIndexVersion !== version)) {`

### [src/main/resources/static/js/document-summary.js](D:/study-assistant/src/main/resources/static/js/document-summary.js)

Luồng quiz/flashcard/tutor/presentation đã có, ngoài phạm vi; không thay đổi.

- Dòng 32: `|| typeof document.activeIndexVersion !== 'string' || !document.activeIndexVersion.trim()) {`

### [src/main/resources/static/map.html](D:/study-assistant/src/main/resources/static/map.html)

Nạp loader canonical; viewer sao chép activeIndexVersion từ polling, gate READY và active index.

- Dòng 90: `<script src="js/document-mind-map.js"></script>`

### [src/main/resources/static/viewer.html](D:/study-assistant/src/main/resources/static/viewer.html)

Nạp loader canonical; viewer sao chép activeIndexVersion từ polling, gate READY và active index.

- Dòng 273: `<script src="js/document-mind-map.js"></script>`
- Dòng 413: `doc.activeIndexVersion = update.activeIndexVersion || null;`
- Dòng 438: `&& typeof doc.activeIndexVersion === "string" && !!doc.activeIndexVersion.trim();`

`aiService.callOpenAI` và `buildDocumentContext`: 0 hit trong production source.

Không bổ sung extractedText AI generation. Ownership/provenance được kiểm thử trên H2; tính tương thích migration MySQL chưa chạy trên host.

## Bao phủ yêu cầu M1–M20

- M1: DocumentKnowledgeGraphH2Test.standaloneGeneratesGroundedConceptsAndRelations (notebook null), bothScopesNullRejected / bothScopesPresentRejected.
- M2: ConceptIntegrityClassificationTest.migrationOnlyRepairsNullabilityAndPreservesHostColumnType (kiểm tra tệp SQL; chưa thực thi MySQL).
- M3–M4: ConceptIntegrityClassificationTest duplicate race và mã MySQL 1048/1452/1406 giữ nguyên exception, không fallback.
- M5–M8: DocumentKnowledgeGraphGenerationServiceTest full/empty relation/partial provider/schema/persistence/candidate limit; H2 giữ node sau lỗi provider.
- M9: allFailedChunksReturnFailureAndLeaveReadyIndex / emptyGroundedGraphFailsWithCorrectCause.
- M10–M13: hai extractor invalidEnvelopeFailsClosed / explicitlyEmptyArrayIsValid; thêm duplicate field, trailing JSON, unknown shape và kiểu sai.
- M14–M16: H2 stale chunk, reindex, foreign document, PROCESSING và thiếu active index; GET cũng gate READY.
- M17: JS new active version gets a new single automatic attempt / stale response test.
- M18: H2 retry ba lần giữ nguyên số concepts/sources/relations/relation sources; giới hạn tên mới ở 50 nguồn/chunk, không tạo identity mồ côi.
- M19: controller GET/POST cùng GRAPH_TOO_LARGE/413; các mã lỗi 500/422/502 được kiểm tra riêng.
- M20: shared-loader partial graph notice giữ node; actual viewer gọi renderer rồi notice trên cùng container.

## Giới hạn xác nhận

- Main Gradle không có Flyway; ddl-auto=update không đảm bảo sửa NOT NULL tồn tại trên host. Migration chỉ là tệp cho host review/apply sau lượt này.
- V13 chỉ sửa nullable, giữ COLUMN_TYPE thực tế (kể cả unsigned) và không drop FK/index. Sau V12 trên schema mới, V13 no-op. Host nâng cấp cần document_id và các ràng buộc document scope tương thích đã có; chưa xác minh trực tiếp các ràng buộc host.
- Mọi ca persistence dùng H2. Không khẳng định MySQL hoặc E2E thật đã PASS.
- Quan hệ >50 candidate có sẵn: không gọi AI và không xóa node. Nguồn mới bị chặn tại 50/chunk dưới khóa document; retry vẫn cập nhật mô tả của identity/nguồn cũ.
- PARTIAL_SUCCESS được trả cho lần POST; GET là snapshot đọc, không lưu lịch sử trạng thái generation mới vào DB.
