-- Add five reviewer accounts to each of the first two active positions in 技术部.
-- The migration is idempotent: existing usernames are left untouched.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    tech_department_id BIGINT;
    reviewer_role_id BIGINT;
    position_record RECORD;
    position_index INT := 0;
    reviewer_index INT;
    reviewer_username VARCHAR(80);
BEGIN
    SELECT id
      INTO tech_department_id
      FROM sys_department
     WHERE department_name = '技术部'
       AND status = 'ACTIVE'
     ORDER BY id
     LIMIT 1;

    IF tech_department_id IS NULL THEN
        RAISE NOTICE '技术部不存在，跳过评估人员初始化';
        RETURN;
    END IF;

    SELECT id
      INTO reviewer_role_id
      FROM sys_role
     WHERE role_code = 'REVIEWER'
     LIMIT 1;

    IF reviewer_role_id IS NULL THEN
        RAISE EXCEPTION 'REVIEWER 角色不存在';
    END IF;

    FOR position_record IN
        SELECT id
          FROM job_position
         WHERE department_id = tech_department_id
           AND status = 'ACTIVE'
         ORDER BY id
         LIMIT 2
    LOOP
        position_index := position_index + 1;

        FOR reviewer_index IN 1..5 LOOP
            reviewer_username := 'reviewer' || LPAD((2 + (position_index - 1) * 5 + reviewer_index)::TEXT, 3, '0');

            IF NOT EXISTS (SELECT 1 FROM sys_user WHERE username = reviewer_username) THEN
                INSERT INTO sys_user (
                    username,
                    password_hash,
                    real_name,
                    department_id,
                    position_id,
                    role_id,
                    status
                ) VALUES (
                    reviewer_username,
                    crypt('12345678', gen_salt('bf')),
                    '评估员' || LPAD((2 + (position_index - 1) * 5 + reviewer_index)::TEXT, 3, '0'),
                    tech_department_id,
                    position_record.id,
                    reviewer_role_id,
                    'ACTIVE'
                );
            END IF;
        END LOOP;
    END LOOP;

    IF position_index < 2 THEN
        RAISE NOTICE '技术部启用岗位少于两个，仅初始化 % 个岗位', position_index;
    END IF;
END $$;
