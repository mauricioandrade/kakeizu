-- Fields that failed their check digits on reading (CNPJ, NF-e access key): what the model read, what a
-- focused re-read and the misread-digit search found, and the re-read output next to raw_model_output.
-- Suggestions only, never applied to the fields; kept after review to measure how often they helped.
ALTER TABLE receipts ADD COLUMN field_recoveries JSONB NOT NULL DEFAULT '[]';
