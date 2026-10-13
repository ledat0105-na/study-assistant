-- File only: apply explicitly to the integration database after review.
-- Existing notebook identities and provenance remain intact.
ALTER TABLE concepts
    MODIFY COLUMN notebook_id BIGINT NULL,
    ADD COLUMN document_id BIGINT NULL,
    ADD CONSTRAINT fk_concept_document FOREIGN KEY (document_id)
        REFERENCES documents(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_document_normalized_name UNIQUE (document_id, normalized_name),
    ADD CONSTRAINT ck_concept_exactly_one_scope CHECK (
        (notebook_id IS NOT NULL AND document_id IS NULL)
        OR (notebook_id IS NULL AND document_id IS NOT NULL)
    );
-- The document uniqueness index already indexes document_id as its leading column.
