CREATE TABLE receipts (
    id                 UUID         PRIMARY KEY,
    status             VARCHAR(20)  NOT NULL,
    category           VARCHAR(20)  NOT NULL,
    -- Editable text fields as read by the model or corrected by a person; may be invalid while pending
    fields             JSONB        NOT NULL,
    image_sha256       VARCHAR(64)  NOT NULL,
    image_content_type VARCHAR(50)  NOT NULL,
    raw_model_output   JSONB,
    created_at         TIMESTAMPTZ  NOT NULL,
    -- Validated values, filled only on confirmation
    type               VARCHAR(10),
    amount             NUMERIC(14, 2),
    issued_on          DATE,
    counterparty       VARCHAR(255),
    tax_id             VARCHAR(14),
    unique_id          VARCHAR(44),

    CONSTRAINT uk_receipts_image_sha256 UNIQUE (image_sha256),
    CONSTRAINT ck_receipts_status CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_receipts_confirmed_complete CHECK (
        status <> 'CONFIRMED'
        OR (type IS NOT NULL AND amount IS NOT NULL AND issued_on IS NOT NULL AND counterparty IS NOT NULL)),
    CONSTRAINT ck_receipts_amount_not_negative CHECK (amount >= 0)
);

-- The same NF-e, NFC-e or Pix cannot be confirmed twice. Card authorization codes repeat across days and
-- machines, so card slips are deduplicated by image only.
CREATE UNIQUE INDEX uk_receipts_confirmed_unique_id ON receipts (type, unique_id)
    WHERE status = 'CONFIRMED' AND type <> 'CARD';

CREATE INDEX ix_receipts_created_at ON receipts (created_at);
CREATE INDEX ix_receipts_confirmed_issued_on ON receipts (issued_on) WHERE status = 'CONFIRMED';
