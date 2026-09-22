ALTER TABLE assessment_task
    ADD COLUMN IF NOT EXISTS final_conclusion VARCHAR(20),
    ADD COLUMN IF NOT EXISTS final_conclusion_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS final_conclusion_reason TEXT,
    ADD COLUMN IF NOT EXISTS abandonment_source VARCHAR(20);

UPDATE assessment_task
SET abandonment_source = 'TIMEOUT'
WHERE final_conclusion = 'ABANDONED'
  AND abandonment_source IS NULL;

ALTER TABLE assessment_task
    DROP CONSTRAINT IF EXISTS ck_task_abandonment_source;

ALTER TABLE assessment_task
    ADD CONSTRAINT ck_task_abandonment_source
        CHECK (abandonment_source IS NULL OR abandonment_source IN ('TIMEOUT', 'CANDIDATE'));
