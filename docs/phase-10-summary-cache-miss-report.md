ROOT_CAUSE:

- Host probe for document 3719 returned `404 No static resource api/documents/3719/summary` and the same error for `/summary/generate`, without a session cookie. This is evidence of unavailable runtime mappings, not evidence of a missing cache row. The source has both mappings, verified through MockMvc.
- The frontend previously classified an unrecognized 404 as a missing API without checking the response. A cache miss now has an explicit, separate contract; ambiguous 404 responses never trigger generation or claim that the API is missing.
- `Document.activeIndexVersion` was excluded from JSON, and status polling omitted it. Both now provide read-only readiness metadata, so a new upload can transition from PROCESSING to READY and start Summary generation without reloading the page.

GET_ROUTE_EXISTS: YES in current source and compiled MVC tests.

Exact mappings in DocumentSummaryController:

```java
@RequestMapping("/api/documents/{docId}/summary")
@GetMapping
@PostMapping("/generate")
```

GET_NO_CACHE_STATUS_BEFORE:

204 in the source audited at the start of this request. The earlier cache contract used 404 with `error: "No current Summary V2 cache"`. The live host 404 above is a missing-route response, a different condition.

GET_NO_CACHE_STATUS_AFTER:

200 with `Cache-Control: no-store` and an explicit owned-document cache state:

```json
{"docId":3719,"cached":false,"summary":null}
```

Ownership and READY/active-index validation happen before this response is constructed. Missing or foreign documents remain 404 with `code: "DOCUMENT_NOT_FOUND"`.

VIEWER_CACHE_MISS_FLOW:

- A cached summary renders immediately without POST.
- The explicit 200 cache state triggers exactly one POST `/api/documents/{docId}/summary/generate`, shared by simultaneous chat and studio consumers.
- Compatibility remains for the previous 204 contract and explicitly identified legacy cache-miss 404 responses.
- All other 404 responses stop. Only an explicit missing-route code or Spring missing-resource evidence produces the technical API message.
- Viewer and reader use `DocumentSummaryLoader`. The legacy `/api/ai/summary/{docId}` endpoint delegates to the canonical controller instead of maintaining separate cache logic.

PROCESSING_HANDLING:

`READY` and a nonblank `activeIndexVersion` are required before generation. PROCESSING displays “Tài liệu đang được xử lý, vui lòng chờ trong giây lát.” FAILED displays a friendly processing failure. Readiness is checked again immediately before POST. Raw backend exceptions are not displayed.

FOREIGN_DOCUMENT_SECURITY:

Preserved: cache reads and generation use the session owner, queries restrict document ownership, persistence revalidates ownership, and foreign/missing-document 404 responses cannot trigger frontend generation.

NEW_UPLOAD_FLOW:

Audited DocumentService and DocumentIndexingService. Uploads without a notebook already enter PROCESSING and run parse → chunk → embed → vector upsert → atomic index activation → READY. No notebook condition gates indexing. Added tests assert PROCESSING and no active index during vector upsert, then READY with an active version afterward. The status endpoint now includes activeIndexVersion; viewer copies it before starting both Summary consumers.

FILES_CHANGED:

- `src/main/java/com/example/studyassistant/controller/DocumentSummaryController.java`
- `src/main/java/com/example/studyassistant/controller/AiController.java`
- `src/main/java/com/example/studyassistant/entity/Document.java`
- `src/main/java/com/example/studyassistant/service/DocumentService.java`
- `src/main/resources/static/js/document-summary.js`
- `src/main/resources/static/viewer.html`
- `src/main/resources/static/reader.html`
- `src/test/java/com/example/studyassistant/controller/DocumentSummaryControllerTest.java`
- `src/test/java/com/example/studyassistant/domain/summary/DocumentSummaryPersistenceServiceTest.java`
- `src/test/java/com/example/studyassistant/entity/DocumentIndexReadinessJsonTest.java`
- `src/test/java/com/example/studyassistant/service/DocumentIndexingIntegrationFlowTest.java`
- `src/test/js/document-summary.test.cjs`
- `src/test/js/viewer-generation.test.cjs`
- This report.

FOCUSED_TEST: PASS — 37 backend tests and 64 JavaScript tests. Includes MVC mappings/cache JSON, cache miss, cached reads, readiness gates, foreign ownership, persistence, Jackson 2/3 read-only metadata, notebook-free upload and polling-to-generation flow.

STANDARD_TEST: PASS — 848 tests, zero failures/errors/skips. Ran:

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon --max-workers=1
```

Used JDK 17 with reduced JVM memory, H2 test resources, fake AI credentials and mocks. Integration/E2E tags were excluded by the existing test task; no MySQL test classes executed.

MYSQL_TOUCHED: NO. No production database connection, SQL mutation or migration was performed. Runtime route probes supplied no cookie; both returned missing-route errors. The tested controller rejects unauthenticated requests before invoking Summary services.

READY_FOR_HOST_RETEST: YES for the compiled/tested source, after starting the updated backend and refreshing the browser. This is not a live-host E2E PASS. The currently probed process still returns missing-route 404 responses; changing the OpenAI key cannot register those routes. No production backend restart was performed as part of the isolated test work.

Host retest: start the updated backend, sign in, reload viewer for the owned document, verify GET 200 cache state followed by POST generation, then verify the next GET returns the cached summary without another POST. If either route still returns `No static resource`, resolve the runtime/classpath deployment before testing AI generation.
