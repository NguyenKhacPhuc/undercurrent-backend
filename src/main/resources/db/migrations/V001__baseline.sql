-- V001 baseline — the schema_migrations mechanism is initialized.
-- No real schema yet; tables land in V002+ as stories 03+ ship
-- (accounts in story 03, sessions in story 04, ...).
--
-- This SELECT 1 makes the file a valid no-op DDL/DML body the
-- runner can execute end-to-end, observably recording V001 in
-- schema_migrations on first boot.
SELECT 1;
