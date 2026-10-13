-- REVIEW ONLY: this file reads schema metadata and prints a proposed ALTER.
-- It does not execute ALTER, UPDATE, DELETE, INSERT, or a migration.
-- The repository is configured for study_assistant. Confirm that the backend
-- running on port 8081 uses this same database before approving any repair.

SELECT DATABASE() AS connected_database;

SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE,
       IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'study_assistant'
  AND TABLE_NAME = 'concepts'
  AND COLUMN_NAME IN ('notebook_id', 'document_id')
ORDER BY ORDINAL_POSITION;

SHOW CREATE TABLE study_assistant.concepts;

-- Preserve the exact current integer type (including UNSIGNED) and default.
-- Stop instead of guessing if unusual column attributes need review.
SELECT CASE
    WHEN NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS d
        WHERE d.TABLE_SCHEMA = c.TABLE_SCHEMA
          AND d.TABLE_NAME = c.TABLE_NAME AND d.COLUMN_NAME = 'document_id'
    ) THEN 'STOP: document_id is missing; review the document-scope migration.'
    WHEN c.IS_NULLABLE = 'YES' THEN 'NO_CHANGE_REQUIRED: notebook_id is already nullable.'
    WHEN c.DATA_TYPE <> 'bigint' OR c.EXTRA <> '' OR c.COLUMN_COMMENT <> ''
        THEN 'STOP: review the full column definition before changing nullability.'
    ELSE CONCAT(
        'ALTER TABLE `study_assistant`.`concepts` MODIFY COLUMN `notebook_id` ',
        c.COLUMN_TYPE, ' NULL',
        CASE WHEN c.COLUMN_DEFAULT IS NULL THEN ''
             ELSE CONCAT(' DEFAULT ', c.COLUMN_DEFAULT) END,
        ';'
    )
END AS proposed_repair_sql
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA = 'study_assistant'
  AND c.TABLE_NAME = 'concepts' AND c.COLUMN_NAME = 'notebook_id';
