-- Compatibility repair only; file prepared for host review, NOT applied by this task.
-- V2 defines BIGINT with fk_concept_notebook (ON DELETE CASCADE), a unique scope
-- index and idx_concept_notebook_id. Keep those objects and the actual host type.
-- Fresh V12 installations are already nullable and take the no-op branch.
-- Host prerequisite: document_id and its document scope constraints already exist.
SET @kg_notebook_type = (
    SELECT COLUMN_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concepts' AND COLUMN_NAME = 'notebook_id'
);
SET @kg_notebook_nullable = (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concepts' AND COLUMN_NAME = 'notebook_id'
);
SET @kg_nullable_ddl = IF(@kg_notebook_nullable = 'NO',
    CONCAT('ALTER TABLE concepts MODIFY COLUMN notebook_id ', @kg_notebook_type, ' NULL'),
    'SELECT 1');
PREPARE kg_nullable_statement FROM @kg_nullable_ddl;
EXECUTE kg_nullable_statement;
DEALLOCATE PREPARE kg_nullable_statement;
