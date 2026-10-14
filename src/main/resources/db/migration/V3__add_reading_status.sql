-- Photos are read in the background: a receipt exists from the upload on, as READING, until the model
-- finishes. Partial index so the app finds what to resume after a restart without scanning the table.
ALTER TABLE receipts DROP CONSTRAINT ck_receipts_status;
ALTER TABLE receipts
    ADD CONSTRAINT ck_receipts_status CHECK (status IN ('READING', 'PENDING_REVIEW', 'CONFIRMED', 'REJECTED'));

CREATE INDEX ix_receipts_reading ON receipts (created_at) WHERE status = 'READING';
