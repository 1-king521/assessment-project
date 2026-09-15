-- 撤销 V12__seed_tech_department_reviewers.sql 播下的 reviewer003 ~ reviewer012。
--
-- V12 已在现有库执行过，Flyway 不会重跑，因此无法通过修改 V12 生效（改动已执行过的
-- 迁移会导致 Flyway 校验和失败）。用本迁移抵消它的效果。
--
-- 逐个删除并单独捕获外键冲突：这些账号可能已产生评估分配、评审或通知记录，
-- 硬删会让应用启动失败。有依赖的账号跳过并输出提示，交由 scripts/reset-data.sql 统一清理。
DO $$
DECLARE
    reviewer_index INT;
    reviewer_username VARCHAR(80);
    skipped_usernames TEXT[] := '{}';
BEGIN
    FOR reviewer_index IN 3..12 LOOP
        reviewer_username := 'reviewer' || LPAD(reviewer_index::TEXT, 3, '0');

        BEGIN
            DELETE FROM sys_user WHERE username = reviewer_username;
        EXCEPTION
            WHEN foreign_key_violation THEN
                skipped_usernames := skipped_usernames || reviewer_username;
        END;
    END LOOP;

    IF array_length(skipped_usernames, 1) > 0 THEN
        RAISE NOTICE '以下账号存在关联业务数据，未删除：%', array_to_string(skipped_usernames, ', ');
    END IF;
END $$;
