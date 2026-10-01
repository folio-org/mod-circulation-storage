-- Derived anonymization due-date, kept off the audited loan record.
-- Row states: absent = not yet evaluated; due_at set = eligible at due_at;
-- due_at NULL = retain under the current policy.
-- Must always run (no fromModuleVersion) so RMB creates it and keeps it.
DO $do$
BEGIN
  -- No FK to loan: it would block TRUNCATE of the loan table. The stamp and
  -- finder queries JOIN loan, so a row for a missing loan is never returned.
  CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.loan_anonymization_due (
    loan_id uuid PRIMARY KEY,
    due_at  timestamptz
  );

  -- Drain index (partial: NULL rows excluded).
  CREATE INDEX IF NOT EXISTS loan_anonymization_due_due_at_idx
    ON ${myuniversity}_${mymodule}.loan_anonymization_due (due_at)
    WHERE due_at IS NOT NULL;

  -- Sweep index: closed loans with a userId, for the anti-join.
  PERFORM rmb_internal_index(
    'loan', 'loan_closed_with_user_idx', 'ADD',
    'CREATE INDEX IF NOT EXISTS loan_closed_with_user_idx ON ${myuniversity}_${mymodule}.loan '
    || $rmb$(id) WHERE (jsonb->'status'->>'name' = 'Closed' AND jsonb->>'userId' IS NOT NULL)$rmb$);
END $do$;
