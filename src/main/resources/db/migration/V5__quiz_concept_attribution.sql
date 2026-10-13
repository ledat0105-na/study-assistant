-- SKM1.5.2: Add concept_id attribution to quiz_questions
ALTER TABLE quiz_questions
ADD COLUMN concept_id BIGINT DEFAULT NULL,
ADD CONSTRAINT fk_quiz_questions_concept
    FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE SET NULL;

CREATE INDEX idx_quiz_questions_concept_id ON quiz_questions(concept_id);
