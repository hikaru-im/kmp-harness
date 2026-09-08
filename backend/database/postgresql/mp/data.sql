-- Optional historical/demo data for yudao-module-mp.

BEGIN;

SELECT setval('mp_account_seq', GREATEST(6, COALESCE((SELECT MAX(id) + 1 FROM mp_account), 6)), false);

SELECT setval('mp_auto_reply_seq', GREATEST(55, COALESCE((SELECT MAX(id) + 1 FROM mp_auto_reply), 55)), false);

SELECT setval('mp_material_seq', GREATEST(111, COALESCE((SELECT MAX(id) + 1 FROM mp_material), 111)), false);

SELECT setval('mp_menu_seq', GREATEST(170, COALESCE((SELECT MAX(id) + 1 FROM mp_menu), 170)), false);

SELECT setval('mp_message_seq', GREATEST(436, COALESCE((SELECT MAX(id) + 1 FROM mp_message), 436)), false);

INSERT INTO mp_message_template (id, account_id, app_id, template_id, title, content, example, primary_industry, deputy_industry, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
  (67, 5, E'wx5b23ba7a5589ecbb', E'QXxdl6_6sHXxsZKI2_Rmn40pB_pw9JJfT00262XFuyg', E'测试一下', E'{ {result.DATA} }\\n\\n领奖金额:{ {withdrawMoney.DATA} }\\n领奖  时间:    { {withdrawTime.DATA} }\\n银行信息:{ {cardInfo.DATA} }\\n到账时间:  { {arrivedTime.DATA} }}', E'', E'', E'', E'1', E'2025-11-26 17:12:44', E'1', E'2025-11-26 18:58:45', 0, 1);

SELECT setval('mp_message_template_seq', GREATEST(68, COALESCE((SELECT MAX(id) + 1 FROM mp_message_template), 68)), false);

SELECT setval('mp_tag_seq', GREATEST(18, COALESCE((SELECT MAX(id) + 1 FROM mp_tag), 18)), false);

SELECT setval('mp_user_seq', GREATEST(66, COALESCE((SELECT MAX(id) + 1 FROM mp_user), 66)), false);

COMMIT;
