-- Optional historical/demo data for yudao-module-sync.
-- Generated from backend/database/sync-2026-07-26.sql; source SHA-256: 415234a67de5d5c18b1d1b2baf43c6ebfdaedb120bcdce49f57cfb4b07d06a66.
-- Load the matching module schema first. This file may contain credentials, keys, or certificates from the source export.
-- Run tools/module-sql-sync.mjs --check to verify this file.

BEGIN;

SELECT setval('app_sync_change_seq', GREATEST(1, COALESCE((SELECT MAX(cursor) + 1 FROM app_sync_change), 1)), false);

COMMIT;
