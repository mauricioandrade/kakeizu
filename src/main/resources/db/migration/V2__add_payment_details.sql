-- How and when the money moved, and whether the shop received it (sale) or paid it (expense).
-- Filled only on confirmation, like the other validated values.
ALTER TABLE receipts
    ADD COLUMN issued_time    TIME,
    ADD COLUMN payment_method VARCHAR(10),
    ADD COLUMN installments   SMALLINT,
    ADD COLUMN card_brand     VARCHAR(10),
    ADD COLUMN direction      VARCHAR(10);

ALTER TABLE receipts
    ADD CONSTRAINT ck_receipts_installments CHECK (installments BETWEEN 1 AND 24),
    ADD CONSTRAINT ck_receipts_credit_installments CHECK (installments = 1 OR payment_method = 'CREDIT'),
    ADD CONSTRAINT ck_receipts_direction CHECK (direction IN ('SALE', 'EXPENSE'));

-- Receipts confirmed before this migration have no direction: only new confirmations must have one
ALTER TABLE receipts
    ADD CONSTRAINT ck_receipts_confirmed_direction CHECK (status <> 'CONFIRMED' OR direction IS NOT NULL) NOT VALID;

CREATE INDEX ix_receipts_confirmed_sales ON receipts (issued_on) WHERE status = 'CONFIRMED' AND direction = 'SALE';
