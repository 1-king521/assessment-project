ALTER TABLE job_position
    ADD COLUMN position_type VARCHAR(20) NOT NULL DEFAULT 'RECRUITMENT';

-- Existing positions referenced only by registered users are organization positions.
UPDATE job_position p
SET position_type = 'ORG'
WHERE EXISTS (SELECT 1 FROM sys_user u WHERE u.position_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM assessment_template t WHERE t.position_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM assessment_task task WHERE task.position_id = p.id);

ALTER TABLE job_position
    ADD CONSTRAINT ck_position_type CHECK (position_type IN ('ORG', 'RECRUITMENT'));
