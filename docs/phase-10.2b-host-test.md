# Targeted Mind Map test for document 3710

Implementation checks use H2 and mocked providers only. The commands below have
not been executed. Run them only in a separately verified integration environment.

## Prerequisites

- The server datasource must point exclusively to `studyassistant_rag_test`.
- Review and apply `V12__document_scoped_knowledge_graph.sql` to that integration
  database in a separate authorized step. It requires MySQL 8.0.16+ for enforced
  CHECK constraints. Do not run the new server against the old schema.
- Keep Hibernate `ddl-auto=validate` so startup does not mutate schema implicitly.
- Log in as the owner of document 3710 on that integration server.
- Confirm provider configuration before authorizing a live generation test.

In a SQL session connected to `studyassistant_rag_test` only:

```sql
SELECT DATABASE();
SELECT id, user_id, notebook_id, status, active_index_version
FROM studyassistant_rag_test.documents WHERE id = 3710;
```

Stop if the row is missing, belongs to a different user, is not READY, or lacks
an active index. Do not fabricate fixture identity or repair other documents.

On an authenticated page of the verified integration server, use browser Console:

```javascript
const generated = await fetch('/api/documents/3710/mind-map/generate', {
  method: 'POST', credentials: 'same-origin'
});
console.log(generated.status, await generated.json());
const result = await fetch('/api/documents/3710/mind-map', {
  credentials: 'same-origin'
});
console.log(result.status, await result.json());
```

Expected: scopeType DOCUMENT, documentId 3710, nodes for extracted grounded
concepts, and edges if valid relationships were extracted. Empty extraction is
allowed; the service never fabricates nodes to make the test pass.

Fixture-specific provenance checks:

```sql
SELECT c.id, c.notebook_id, c.document_id, c.canonical_name,
       cs.document_chunk_id, dc.index_version
FROM studyassistant_rag_test.concepts c
JOIN studyassistant_rag_test.concept_sources cs ON cs.concept_id = c.id
JOIN studyassistant_rag_test.document_chunks dc ON dc.id = cs.document_chunk_id
JOIN studyassistant_rag_test.documents d ON d.id = dc.document_id
WHERE d.id = 3710 AND d.status = 'READY'
  AND dc.index_version = d.active_index_version;

SELECT cr.id, cr.source_concept_id, cr.target_concept_id,
       cr.relation_type, crs.document_chunk_id, dc.index_version
FROM studyassistant_rag_test.concept_relations cr
JOIN studyassistant_rag_test.concept_relation_sources crs ON crs.concept_relation_id = cr.id
JOIN studyassistant_rag_test.document_chunks dc ON dc.id = crs.document_chunk_id
JOIN studyassistant_rag_test.documents d ON d.id = dc.document_id
WHERE d.id = 3710 AND d.status = 'READY'
  AND dc.index_version = d.active_index_version;
```

The new document FK uses ON DELETE RESTRICT. Documents with document-scoped
concepts cannot be deleted through the current delete flow; rejection happens
before vector cleanup. Notebook deletion semantics are unchanged. A later
explicit provenance-aware deletion feature can address this separately.

## Implementation files and checks

Files changed by this phase (existing unrelated workspace edits preserved):

- [Concept.java](D:/study-assistant/src/main/java/com/example/studyassistant/entity/Concept.java)
- [ConceptRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/ConceptRepository.java)
- [DocumentRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/DocumentRepository.java)
- [KnowledgeGraphQueryRepository.java](D:/study-assistant/src/main/java/com/example/studyassistant/repository/KnowledgeGraphQueryRepository.java)
- [KnowledgeScope.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/KnowledgeScope.java)
- [KnowledgeGraphEvidenceValidator.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/KnowledgeGraphEvidenceValidator.java)
- [DocumentKnowledgeGraphGenerationService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/DocumentKnowledgeGraphGenerationService.java)
- [KnowledgeGraphGenerationException.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/KnowledgeGraphGenerationException.java)
- [AIConceptExtractor.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/AIConceptExtractor.java)
- [AIConceptRelationExtractor.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/AIConceptRelationExtractor.java)
- [ConceptService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptService.java)
- [ConceptPersistenceHelper.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptPersistenceHelper.java)
- [ConceptRelationService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptRelationService.java)
- [ConceptRelationPersistenceHelper.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/ConceptRelationPersistenceHelper.java)
- [KnowledgeGraphQueryService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/KnowledgeGraphQueryService.java)
- [DocumentService.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/DocumentService.java)
- [DocumentIndexingService.java](D:/study-assistant/src/main/java/com/example/studyassistant/retrieval/service/DocumentIndexingService.java)
- [DocumentMindMapController.java](D:/study-assistant/src/main/java/com/example/studyassistant/controller/DocumentMindMapController.java)
- [KnowledgeGraphDto.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/dto/KnowledgeGraphDto.java)
- [GraphConceptDto.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/dto/GraphConceptDto.java)
- [GraphRelationDto.java](D:/study-assistant/src/main/java/com/example/studyassistant/service/dto/GraphRelationDto.java)
- [V12__document_scoped_knowledge_graph.sql](D:/study-assistant/src/main/resources/db/migration/V12__document_scoped_knowledge_graph.sql)
- [document-mind-map.js](D:/study-assistant/src/main/resources/static/js/document-mind-map.js)
- [viewer.html](D:/study-assistant/src/main/resources/static/viewer.html)
- [map.html](D:/study-assistant/src/main/resources/static/map.html)
- [DocumentKnowledgeGraphH2Test.java](D:/study-assistant/src/test/java/com/example/studyassistant/service/DocumentKnowledgeGraphH2Test.java)
- [DocumentMindMapControllerTest.java](D:/study-assistant/src/test/java/com/example/studyassistant/controller/DocumentMindMapControllerTest.java)
- [AIConceptExtractorTest.java](D:/study-assistant/src/test/java/com/example/studyassistant/service/AIConceptExtractorTest.java)
- [AIConceptRelationExtractorTest.java](D:/study-assistant/src/test/java/com/example/studyassistant/service/AIConceptRelationExtractorTest.java)
- [DocumentIndexingServiceTest.java](D:/study-assistant/src/test/java/com/example/studyassistant/retrieval/service/DocumentIndexingServiceTest.java)
- [DocumentIndexingIntegrationFlowTest.java](D:/study-assistant/src/test/java/com/example/studyassistant/service/DocumentIndexingIntegrationFlowTest.java)
- [DocumentDeleteAndQuotaTest.java](D:/study-assistant/src/test/java/com/example/studyassistant/service/DocumentDeleteAndQuotaTest.java)
- [document-mind-map.test.cjs](D:/study-assistant/src/test/js/document-mind-map.test.cjs)
- [phase-10.2b-host-test.md](D:/study-assistant/docs/phase-10.2b-host-test.md)

Legacy scan classification:

- DocumentController.getExtractedText: read-only document content endpoint.
- detail.html /api/topics/document: compatibility read through ownership-checked TopicController; no generation.
- PresentationOutlineGenerationWorker: comment stating extractedText is not used.
- AIConceptExtractor / AIConceptRelationExtractor: existing implementations now invoked by canonical KG orchestration.
- No generateKnowledgeMapTopics or aiService.callOpenAI matches in production source.

Commands used for focused verification:

```powershell
node --test src/test/js/viewer-generation.test.cjs src/test/js/document-mind-map.test.cjs
.\gradlew.bat test --tests '*DocumentKnowledgeGraphH2Test' --tests '*AIConceptExtractorTest' --tests '*AIConceptRelationExtractorTest' --tests '*KnowledgeGraphQueryServiceTest' --tests '*DocumentIndexingServiceTest' --tests '*DocumentMindMapControllerTest' --tests '*DocumentIndexingIntegrationFlowTest' --tests '*DocumentDeleteAndQuotaTest' --no-daemon --max-workers=1
```

Standard command and working memory settings (process-local only):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:JAVA_TOOL_OPTIONS='-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=64m -XX:CICompilerCount=2 -XX:ActiveProcessorCount=2'
$env:_JAVA_OPTIONS='-Xmx256m -XX:+UseSerialGC -Dspring.test.context.cache.maxSize=1'
.\gradlew.bat test --rerun-tasks --no-daemon --max-workers=1
```

The normal 512 MB JVM runs hit Windows native-memory/pagefile exhaustion.
The final run retains all standard tests with a smaller effective heap.
