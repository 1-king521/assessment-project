-- Preserve old template/task links that were created before the two position tables were split.
INSERT INTO recruitment_position (id, department_name, position_name, status, created_by, created_at, updated_by, updated_at)
SELECT p.id,
       d.department_name,
       p.position_name,
       p.status,
       p.created_by,
       p.created_at,
       p.updated_by,
       p.updated_at
FROM job_position p
JOIN sys_department d ON d.id = p.department_id
WHERE (EXISTS (SELECT 1 FROM assessment_template t WHERE t.position_id = p.id)
    OR EXISTS (SELECT 1 FROM assessment_task task WHERE task.position_id = p.id))
  AND NOT EXISTS (SELECT 1 FROM recruitment_position rp WHERE rp.id = p.id);

SELECT setval(pg_get_serial_sequence('recruitment_position', 'id'), COALESCE((SELECT MAX(id) FROM recruitment_position), 1), true);
