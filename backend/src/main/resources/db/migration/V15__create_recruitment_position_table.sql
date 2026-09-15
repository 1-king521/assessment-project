CREATE TABLE recruitment_position (
    id BIGINT PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL,
    position_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL REFERENCES sys_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recruitment_position_name UNIQUE (department_name, position_name),
    CONSTRAINT ck_recruitment_position_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

INSERT INTO recruitment_position (id, department_name, position_name, status, created_by, created_at, updated_by, updated_at)
SELECT p.id, d.department_name, p.position_name, p.status, p.created_by, p.created_at, p.updated_by, p.updated_at
FROM job_position p JOIN sys_department d ON d.id = p.department_id
WHERE p.position_type = 'RECRUITMENT';

ALTER TABLE assessment_template DROP CONSTRAINT IF EXISTS assessment_template_position_id_fkey;
ALTER TABLE assessment_task DROP CONSTRAINT IF EXISTS assessment_task_position_id_fkey;
ALTER TABLE assessment_task DROP CONSTRAINT IF EXISTS fk_task_position;
ALTER TABLE assessment_template ADD CONSTRAINT fk_template_recruitment_position FOREIGN KEY (position_id) REFERENCES recruitment_position(id);
ALTER TABLE assessment_task ADD CONSTRAINT fk_task_recruitment_position FOREIGN KEY (position_id) REFERENCES recruitment_position(id);
