P1_SCHEMA_ROOT_CAUSE:
Concept hỗ trợ document scope với notebook null nhưng log host xác nhận concepts.notebook_id NOT NULL (MySQL 1048). V2 tạo BIGINT NOT NULL, FK notebook ON DELETE CASCADE, unique notebook/name và index notebook; V12 hỗ trợ document scope nhưng schema host đã nâng cấp một phần. max-output-tokens hiện có là 4000; không phải nguyên nhân của lỗi 1048 này.

MIGRATION_REQUIRED:
YES

MIGRATION_FILE:
src/main/resources/db/migration/V13__mind_map_v2_hardening.sql

MIGRATION_OPERATIONS:
Chỉ MODIFY concepts.notebook_id thành NULL, lấy COLUMN_TYPE từ metadata để giữ kiểu thực tế và unsigned nếu có; không drop/recreate FK, index hay document_id. Nếu nullable rồi thì no-op. V12 không sửa. Chưa chạy SQL này. Host cần document_id và các ràng buộc scope tương thích đã có; chi tiết host chưa được xác minh trực tiếp. Gradle hiện không có Flyway tự áp dụng migration.

MYSQL_MUTATED:
NO

CONCEPT_INTEGRITY_CLASSIFICATION_FIXED:
YES — chỉ MySQL 1062/SQLState 23000, SQLState 23505 hoặc DuplicateKeyException được phục hồi race. 1048/1452/1406 không fallback; giữ nguyên exception/cause cho server logs. Áp dụng cả race relation/source trong KG.

PARTIAL_GRAPH_POLICY:
Graph có node grounded active: FULL_SUCCESS nếu có quan hệ và không degradation; SUCCESS nếu không có quan hệ hợp lệ và không degradation; PARTIAL_SUCCESS/PARTIAL_GRAPH khi extractor/provider/schema/persistence/candidate-limit bị suy giảm. Node/provenance đã lưu giữ nguyên. Không có node grounded => failure với mã nguyên nhân. Không dựa riêng vào status SUCCESS của extractor. POST trả degradationCodes; GET vẫn là snapshot read-only, không lưu lịch sử outcome vào DB.

CONCEPT_SCHEMA_VALIDATION:
Bắt buộc root object chỉ có concepts là array; {} không hợp lệ, concepts:[] hợp lệ. Reject JSON lỗi, trailing tokens, duplicate fields, unknown fields, candidate không phải object và sai kiểu scalar; validation bằng chứng/tên/confidence hiện có vẫn giữ. Không persist malformed envelope.

RELATION_SCHEMA_VALIDATION:
Tương tự concepts với root relations array; relations:[] hợp lệ. ID phải là số nguyên long, nội dung text phải là chuỗi. Giữ kiểm tra endpoints, scope, self-edge, enum, confidence và exact evidence; candidate không hợp lệ bị loại theo quy ước cũ.

CANDIDATE_LIMIT_POLICY:
Tối đa 50 nguồn khái niệm mỗi active chunk, kiểm tra dưới khóa document trước khi thêm identity/source. Identity/nguồn cũ vẫn tái sử dụng và cập nhật mô tả. Dữ liệu cũ >50 relation candidates => không gọi AI, không xóa node, PARTIAL_GRAPH khi có node usable. Prompt quan hệ luôn bounded; không xóa dữ liệu cũ để ép giới hạn.

REGENERATION_IDEMPOTENCY:
Giữ unique scope/name, concept/chunk, endpoints/type và relation/chunk. H2 kiểm tra ba lần tạo cùng index giữ nguyên số dòng cả bốn loại. Tên mới khi chunk đã đạt 50 bị chặn trước insert, không để lại concept mồ côi. Index mới dùng provenance mới; stale sources không được hiển thị.

ERROR_CONTRACT:
GET/POST dùng chung response an toàn {code,error}, Cache-Control no-store. 401 UNAUTHENTICATED; 404 NOT_FOUND (ẩn owner); 409 DOCUMENT_NOT_READY/NO_ACTIVE_INDEX/NO_ACTIVE_EVIDENCE/STALE_INDEX; 413 GRAPH_TOO_LARGE; 422 AI_SCHEMA_ERROR/NO_CONCEPTS; 502 AI_PROVIDER_ERROR; 500 GRAPH_PERSISTENCE_ERROR. Partial trả HTTP 200 với graph. Không trả SQL/provider message hoặc stack trace cho frontend.

FRONTEND_ERROR_HANDLING:
Thông báo tiếng Việt phân biệt state/index/evidence, size, provider, schema, persistence, auth/not-found và empty graph. Loader giữ metadata partial của POST khi đọc lại GET. Viewer/map vẫn render node và thêm notice PARTIAL_GRAPH không chặn thao tác. Kiểm tra active version trước và sau network response.

ATTEMPT_KEY:
`${doc.id}:${doc.activeIndexVersion}` cho pending và attempted. Mỗi active version chỉ tự sinh một lần, manual retry được phép; V1 không chặn V2.

ACTIVE_INDEX_ENFORCEMENT:
PASS

OWNERSHIP:
PASS

LEGACY_AI_GENERATION_REINTRODUCED:
NO — TopicController document endpoint vẫn chỉ đọc repository. Phân loại từng hit production source và mapping M1–M20 ở docs/phase10-2-source-audit.md.

FILES_CHANGED:
Production:

- src/main/java/com/example/studyassistant/controller/DocumentMindMapController.java
- src/main/java/com/example/studyassistant/repository/ConceptSourceRepository.java
- src/main/java/com/example/studyassistant/service/AIConceptExtractor.java
- src/main/java/com/example/studyassistant/service/AIConceptRelationExtractor.java
- src/main/java/com/example/studyassistant/service/ConceptCandidateLimitException.java
- src/main/java/com/example/studyassistant/service/ConceptPersistenceHelper.java
- src/main/java/com/example/studyassistant/service/ConceptRelationService.java
- src/main/java/com/example/studyassistant/service/ConceptService.java
- src/main/java/com/example/studyassistant/service/DocumentKnowledgeGraphGenerationService.java
- src/main/java/com/example/studyassistant/service/GraphFailureCode.java
- src/main/java/com/example/studyassistant/service/KnowledgeGraphDuplicate.java
- src/main/java/com/example/studyassistant/service/KnowledgeGraphEvidenceValidator.java
- src/main/java/com/example/studyassistant/service/KnowledgeGraphGenerationException.java
- src/main/java/com/example/studyassistant/service/KnowledgeGraphJson.java
- src/main/java/com/example/studyassistant/service/KnowledgeGraphQueryService.java
- src/main/java/com/example/studyassistant/service/KnowledgeGraphStateException.java
- src/main/java/com/example/studyassistant/service/KnowledgeScope.java
- src/main/java/com/example/studyassistant/service/dto/KnowledgeGraphDto.java
- src/main/resources/db/migration/V13__mind_map_v2_hardening.sql
- src/main/resources/static/js/document-mind-map.js
- src/main/resources/static/map.html
- src/main/resources/static/viewer.html
Tests:

- src/test/java/com/example/studyassistant/controller/DocumentMindMapControllerTest.java
- src/test/java/com/example/studyassistant/service/AIConceptExtractorTest.java
- src/test/java/com/example/studyassistant/service/AIConceptRelationExtractorTest.java
- src/test/java/com/example/studyassistant/service/ConceptIntegrityClassificationTest.java
- src/test/java/com/example/studyassistant/service/DocumentKnowledgeGraphGenerationServiceTest.java
- src/test/java/com/example/studyassistant/service/DocumentKnowledgeGraphH2Test.java
- src/test/js/document-mind-map.test.cjs
- src/test/js/viewer-generation.test.cjs
Reports:

- docs/phase10-2-source-audit.md
- docs/phase10-2-mind-map-v2-report.md

UNRELATED_FILES_CHANGED:
NONE trong implementation lượt này. Workspace có nhiều thay đổi từ trước, được giữ nguyên. Build tạo log/report và JVM crash files khi lần regression đầu hết bộ nhớ; không commit/stage.

FOCUSED_TESTS:
PASS — 132 Java (H2/mock) + 75 JavaScript, không failure/skipped. M1–M20 mapped trong source audit.

STANDARD_TEST:
PASS — .\gradlew.bat test --rerun-tasks --no-daemon --max-workers=1: 896 tests, 0 failure, 0 error, 0 skipped. Lần đầu JVM bị thiếu native memory; lần chạy hoàn tất dùng Xmx256m, MaxMetaspaceSize192m, SerialGC, TieredStopAtLevel=1 và context cache=1. Các biến chỉ áp dụng tiến trình kiểm thử, không sửa application.properties/build.gradle.

MYSQL_TEST:
NOT_RUN

LIVE_OPENAI_CALLED:
NO

PRODUCTION_DB_TOUCHED:
NO

READY_FOR_SINGLE_HOST_E2E:
YES — sau khi host review/apply migration nullable. Không khẳng định host/MySQL/E2E đã PASS.

HOST_MIGRATION_COMMAND_REQUIRED:
YES
V13__mind_map_v2_hardening.sql
