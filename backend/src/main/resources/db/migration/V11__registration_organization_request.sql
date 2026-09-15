ALTER TABLE sys_user
    ADD COLUMN requested_department_name VARCHAR(100),
    ADD COLUMN requested_position_name VARCHAR(100),
    ADD COLUMN requested_dingtalk_department_id BIGINT;

ALTER TABLE sys_department
    ADD COLUMN dingtalk_department_id BIGINT;

CREATE UNIQUE INDEX uq_department_dingtalk_id
    ON sys_department(dingtalk_department_id)
    WHERE dingtalk_department_id IS NOT NULL;
