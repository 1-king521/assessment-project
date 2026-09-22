ALTER TABLE assessment_assignment
    ADD COLUMN review_due_at TIMESTAMPTZ,
    ADD COLUMN overdue_reminder_sent_at TIMESTAMPTZ;

CREATE INDEX idx_assignment_overdue_reminder
    ON assessment_assignment(review_due_at, status)
    WHERE overdue_reminder_sent_at IS NULL;
