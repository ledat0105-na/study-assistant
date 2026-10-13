package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizDocumentScopeValidatorTest {
    @Mock NotebookRepository notebooks;
    @Mock DocumentRepository documents;
    @Mock TopicRepository topics;
    @Mock ConceptRepository concepts;
    QuizScopeValidator validator;
    Document document;
    User owner;

    @BeforeEach void setup() {
        validator = new QuizScopeValidator(notebooks, documents, topics, concepts);
        owner = new User(); owner.setId(1L);
        document = new Document(); document.setId(10L); document.setUser(owner);
        document.setStatus("READY"); document.setActiveIndexVersion("v1");
    }
    GroundedQuizGenerationCommand command(Long notebook) {
        return new GroundedQuizGenerationCommand(1L, QuizScopeType.DOCUMENT, notebook, 10L,
                null, null, QuizMode.PRACTICE, 5, null, null, null, null);
    }
    void owned() {
        when(documents.findKnowledgeGraphDocumentByIdAndUserId(10L, 1L)).thenReturn(Optional.of(document));
    }
    @Test void standaloneOwnedDocumentAcceptedWithoutNotebook() {
        owned(); assertDoesNotThrow(() -> validator.validateScope(command(null)));
        verifyNoInteractions(notebooks);
    }
    @Test void notebookDocumentDerivesScopeButRejectsMismatch() {
        Notebook notebook = new Notebook(); notebook.setId(2L); notebook.setUser(owner);
        document.setNotebook(notebook); owned();
        assertDoesNotThrow(() -> validator.validateScope(command(null)));
        assertDoesNotThrow(() -> validator.validateScope(command(2L)));
        assertThrows(IllegalArgumentException.class, () -> validator.validateScope(command(3L)));
    }
    @Test void standaloneCannotBorrowNotebook() {
        owned(); assertThrows(IllegalArgumentException.class, () -> validator.validateScope(command(2L)));
    }
    @Test void foreignOrMissingDocumentRejected() {
        when(documents.findKnowledgeGraphDocumentByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> validator.validateScope(command(null)));
    }
    @Test void unreadyOrUnindexedDocumentRejected() {
        owned();
        for (String status : new String[]{"UPLOADING", "PROCESSING", "FAILED"}) {
            document.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> validator.validateScope(command(null)));
        }
        document.setStatus("READY"); document.setActiveIndexVersion(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateScope(command(null)));
    }
    @Test void foreignNotebookRejected() {
        User foreign = new User(); foreign.setId(9L);
        Notebook notebook = new Notebook(); notebook.setId(2L); notebook.setUser(foreign);
        document.setNotebook(notebook); owned();
        assertThrows(IllegalArgumentException.class, () -> validator.validateScope(command(null)));
    }
}
