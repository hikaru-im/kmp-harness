-- PostgreSQL schema for yudao-module-mp.

BEGIN;

DROP TABLE IF EXISTS mp_user;
DROP TABLE IF EXISTS mp_tag;
DROP TABLE IF EXISTS mp_message_template;
DROP TABLE IF EXISTS mp_message;
DROP TABLE IF EXISTS mp_menu;
DROP TABLE IF EXISTS mp_material;
DROP TABLE IF EXISTS mp_auto_reply;
DROP TABLE IF EXISTS mp_account;
DROP SEQUENCE IF EXISTS mp_user_seq;
DROP SEQUENCE IF EXISTS mp_tag_seq;
DROP SEQUENCE IF EXISTS mp_message_template_seq;
DROP SEQUENCE IF EXISTS mp_message_seq;
DROP SEQUENCE IF EXISTS mp_menu_seq;
DROP SEQUENCE IF EXISTS mp_material_seq;
DROP SEQUENCE IF EXISTS mp_auto_reply_seq;
DROP SEQUENCE IF EXISTS mp_account_seq;

CREATE OR REPLACE FUNCTION mysql_on_update_current_timestamp()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
  IF (to_jsonb(NEW) - 'update_time') IS DISTINCT FROM (to_jsonb(OLD) - 'update_time') THEN
    NEW.update_time = statement_timestamp();
  END IF;
  RETURN NEW;
END;
$function$;

-- ----------------------------
-- Table structure for mp_account
-- ----------------------------
CREATE SEQUENCE mp_account_seq AS bigint START WITH 6;
CREATE TABLE mp_account (
  id bigint DEFAULT nextval('mp_account_seq'::regclass) NOT NULL,
  name varchar(100) DEFAULT NULL NULL,
  account varchar(100) DEFAULT NULL NULL,
  app_id varchar(100) DEFAULT NULL NULL,
  app_secret varchar(100) DEFAULT NULL NULL,
  url varchar(100) DEFAULT NULL NULL,
  token varchar(100) DEFAULT NULL NULL,
  aes_key varchar(300) DEFAULT NULL NULL,
  qr_code_url varchar(200) DEFAULT NULL NULL,
  remark varchar(255) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_account_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_account_seq OWNED BY mp_account.id;
-- MySQL index name: idx_app_id
CREATE INDEX mp_account_idx_app_id ON mp_account (app_id);
COMMENT ON TABLE mp_account IS E'公众号账号表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.id IS E'编号';
COMMENT ON COLUMN mp_account.name IS E'公众号名称';
-- MySQL column charset/collation: mp_account.name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.account IS E'公众号账号';
-- MySQL column charset/collation: mp_account.account = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.app_id IS E'公众号appid';
-- MySQL column charset/collation: mp_account.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.app_secret IS E'公众号密钥';
-- MySQL column charset/collation: mp_account.app_secret = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.url IS E'公众号url';
-- MySQL column charset/collation: mp_account.url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.token IS E'公众号token';
-- MySQL column charset/collation: mp_account.token = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.aes_key IS E'加密密钥';
-- MySQL column charset/collation: mp_account.aes_key = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.qr_code_url IS E'二维码图片URL';
-- MySQL column charset/collation: mp_account.qr_code_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.remark IS E'备注';
-- MySQL column charset/collation: mp_account.remark = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.creator IS E'创建者';
-- MySQL column charset/collation: mp_account.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.create_time IS E'创建时间';
COMMENT ON COLUMN mp_account.updater IS E'更新者';
-- MySQL column charset/collation: mp_account.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_account.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_account_update_time
BEFORE UPDATE ON mp_account
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_account.deleted IS E'是否删除';
COMMENT ON COLUMN mp_account.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_auto_reply
-- ----------------------------
CREATE SEQUENCE mp_auto_reply_seq AS bigint START WITH 55;
CREATE TABLE mp_auto_reply (
  id bigint DEFAULT nextval('mp_auto_reply_seq'::regclass) NOT NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  type smallint NOT NULL,
  request_keyword varchar(255) DEFAULT NULL NULL,
  request_match smallint DEFAULT NULL NULL,
  request_message_type varchar(32) DEFAULT NULL NULL,
  response_message_type varchar(32) NOT NULL,
  response_content varchar(1024) DEFAULT NULL NULL,
  response_media_id varchar(128) DEFAULT NULL NULL,
  response_media_url varchar(1024) DEFAULT NULL NULL,
  response_title varchar(128) DEFAULT NULL NULL,
  response_description varchar(256) DEFAULT NULL NULL,
  response_thumb_media_id varchar(128) DEFAULT NULL NULL,
  response_thumb_media_url varchar(1024) DEFAULT NULL NULL,
  response_articles varchar(1024) DEFAULT NULL NULL,
  response_music_url varchar(1024) DEFAULT NULL NULL,
  response_hq_music_url varchar(1024) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_auto_reply_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_auto_reply_seq OWNED BY mp_auto_reply.id;
-- MySQL index name: idx_app_id_type_request_message_type
CREATE INDEX mp_auto_reply_idx_app_id_type_request_message_type ON mp_auto_reply (app_id, type, request_message_type);
-- MySQL index name: idx_app_id_type_request_match_request_keyword
CREATE INDEX mp_auto_reply_idx_app_id_type_request_match_request_keyword ON mp_auto_reply (app_id, type, request_match, request_keyword);
-- MySQL index name: idx_account_id
CREATE INDEX mp_auto_reply_idx_account_id ON mp_auto_reply (account_id);
COMMENT ON TABLE mp_auto_reply IS E'公众号消息自动回复表';
-- MySQL table charset/collation: utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.id IS E'主键';
COMMENT ON COLUMN mp_auto_reply.account_id IS E'公众号账号的编号';
COMMENT ON COLUMN mp_auto_reply.app_id IS E'公众号 appId';
-- MySQL column charset/collation: mp_auto_reply.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_auto_reply.type IS E'回复类型';
COMMENT ON COLUMN mp_auto_reply.request_keyword IS E'请求的关键字';
-- MySQL column charset/collation: mp_auto_reply.request_keyword = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.request_match IS E'请求的关键字的匹配';
COMMENT ON COLUMN mp_auto_reply.request_message_type IS E'请求的消息类型';
-- MySQL column charset/collation: mp_auto_reply.request_message_type = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_message_type IS E'回复的消息类型';
-- MySQL column charset/collation: mp_auto_reply.response_message_type = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_content IS E'回复的消息内容';
-- MySQL column charset/collation: mp_auto_reply.response_content = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_media_id IS E'回复的媒体文件 id';
-- MySQL column charset/collation: mp_auto_reply.response_media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_media_url IS E'回复的媒体文件 URL';
-- MySQL column charset/collation: mp_auto_reply.response_media_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_title IS E'回复的标题';
-- MySQL column charset/collation: mp_auto_reply.response_title = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_description IS E'回复的描述';
-- MySQL column charset/collation: mp_auto_reply.response_description = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_thumb_media_id IS E'回复的缩略图的媒体 id';
-- MySQL column charset/collation: mp_auto_reply.response_thumb_media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_thumb_media_url IS E'回复的缩略图的媒体 URL';
-- MySQL column charset/collation: mp_auto_reply.response_thumb_media_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_articles IS E'回复的图文消息数组';
-- MySQL column charset/collation: mp_auto_reply.response_articles = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_music_url IS E'回复的音乐链接';
-- MySQL column charset/collation: mp_auto_reply.response_music_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.response_hq_music_url IS E'回复的高质量音乐链接';
-- MySQL column charset/collation: mp_auto_reply.response_hq_music_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_auto_reply.creator IS E'创建者';
-- MySQL column charset/collation: mp_auto_reply.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_auto_reply.create_time IS E'创建时间';
COMMENT ON COLUMN mp_auto_reply.updater IS E'更新者';
-- MySQL column charset/collation: mp_auto_reply.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_auto_reply.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_auto_reply_update_time
BEFORE UPDATE ON mp_auto_reply
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_auto_reply.deleted IS E'是否删除';
COMMENT ON COLUMN mp_auto_reply.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_material
-- ----------------------------
CREATE SEQUENCE mp_material_seq AS bigint START WITH 111;
CREATE TABLE mp_material (
  id bigint DEFAULT nextval('mp_material_seq'::regclass) NOT NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  media_id varchar(128) NOT NULL,
  type varchar(32) NOT NULL,
  permanent smallint DEFAULT 0 NOT NULL CHECK (permanent IN (0, 1)),
  url varchar(1024) DEFAULT NULL NULL,
  name varchar(255) DEFAULT NULL NULL,
  mp_url varchar(1024) DEFAULT NULL NULL,
  title varchar(255) DEFAULT NULL NULL,
  introduction varchar(255) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_material_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_material_seq OWNED BY mp_material.id;
-- MySQL index name: idx_account_id_media_id
CREATE INDEX mp_material_idx_account_id_media_id ON mp_material (account_id, media_id);
-- MySQL index name: idx_account_id
CREATE INDEX mp_material_idx_account_id ON mp_material (account_id);
-- MySQL index name: idx_media_id
CREATE INDEX mp_material_idx_media_id ON mp_material (media_id);
COMMENT ON TABLE mp_material IS E'公众号素材表';
-- MySQL table charset/collation: utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.id IS E'主键';
COMMENT ON COLUMN mp_material.account_id IS E'公众号账号的编号';
COMMENT ON COLUMN mp_material.app_id IS E'公众号 appId';
-- MySQL column charset/collation: mp_material.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_material.media_id IS E'公众号素材 id';
-- MySQL column charset/collation: mp_material.media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.type IS E'文件类型';
-- MySQL column charset/collation: mp_material.type = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.permanent IS E'是否永久';
COMMENT ON COLUMN mp_material.url IS E'文件服务器的 URL';
-- MySQL column charset/collation: mp_material.url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.name IS E'名字';
-- MySQL column charset/collation: mp_material.name = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.mp_url IS E'公众号文件 URL';
-- MySQL column charset/collation: mp_material.mp_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.title IS E'视频素材的标题';
-- MySQL column charset/collation: mp_material.title = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.introduction IS E'视频素材的描述';
-- MySQL column charset/collation: mp_material.introduction = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_material.creator IS E'创建者';
-- MySQL column charset/collation: mp_material.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_material.create_time IS E'创建时间';
COMMENT ON COLUMN mp_material.updater IS E'更新者';
-- MySQL column charset/collation: mp_material.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_material.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_material_update_time
BEFORE UPDATE ON mp_material
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_material.deleted IS E'是否删除';
COMMENT ON COLUMN mp_material.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_menu
-- ----------------------------
CREATE SEQUENCE mp_menu_seq AS bigint START WITH 170;
CREATE TABLE mp_menu (
  id bigint DEFAULT nextval('mp_menu_seq'::regclass) NOT NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  name varchar(255) DEFAULT NULL NULL,
  menu_key varchar(255) DEFAULT NULL NULL,
  parent_id varchar(32) DEFAULT NULL NULL,
  type varchar(32) DEFAULT E'' NOT NULL,
  url varchar(500) DEFAULT NULL NULL,
  mini_program_app_id varchar(32) DEFAULT NULL NULL,
  mini_program_page_path varchar(200) DEFAULT NULL NULL,
  article_id varchar(200) DEFAULT NULL NULL,
  reply_message_type varchar(32) DEFAULT NULL NULL,
  reply_content varchar(1024) DEFAULT NULL NULL,
  reply_media_id varchar(128) DEFAULT NULL NULL,
  reply_media_url varchar(1024) DEFAULT NULL NULL,
  reply_title varchar(128) DEFAULT NULL NULL,
  reply_description varchar(256) DEFAULT NULL NULL,
  reply_thumb_media_id varchar(128) DEFAULT NULL NULL,
  reply_thumb_media_url varchar(1024) DEFAULT NULL NULL,
  reply_articles varchar(1024) DEFAULT NULL NULL,
  reply_music_url varchar(1024) DEFAULT NULL NULL,
  reply_hq_music_url varchar(1024) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_menu_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_menu_seq OWNED BY mp_menu.id;
-- MySQL index name: idx_app_id
CREATE INDEX mp_menu_idx_app_id ON mp_menu (app_id);
-- MySQL index name: idx_account_id
CREATE INDEX mp_menu_idx_account_id ON mp_menu (account_id);
COMMENT ON TABLE mp_menu IS E'公众号菜单表';
-- MySQL table charset/collation: utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.id IS E'主键';
COMMENT ON COLUMN mp_menu.account_id IS E'微信公众号ID';
COMMENT ON COLUMN mp_menu.app_id IS E'微信公众号 appid';
-- MySQL column charset/collation: mp_menu.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_menu.name IS E'菜单名称';
-- MySQL column charset/collation: mp_menu.name = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.menu_key IS E'菜单标识';
-- MySQL column charset/collation: mp_menu.menu_key = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.parent_id IS E'父ID';
-- MySQL column charset/collation: mp_menu.parent_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.type IS E'按钮类型';
-- MySQL column charset/collation: mp_menu.type = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.url IS E'网页链接';
-- MySQL column charset/collation: mp_menu.url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.mini_program_app_id IS E'小程序appid';
-- MySQL column charset/collation: mp_menu.mini_program_app_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.mini_program_page_path IS E'小程序页面路径';
-- MySQL column charset/collation: mp_menu.mini_program_page_path = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.article_id IS E'跳转图文的媒体编号';
-- MySQL column charset/collation: mp_menu.article_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_message_type IS E'消息类型';
-- MySQL column charset/collation: mp_menu.reply_message_type = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_content IS E'回复的消息内容';
-- MySQL column charset/collation: mp_menu.reply_content = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_media_id IS E'回复的媒体文件 id';
-- MySQL column charset/collation: mp_menu.reply_media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_media_url IS E'回复的媒体文件 URL';
-- MySQL column charset/collation: mp_menu.reply_media_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_title IS E'回复的标题';
-- MySQL column charset/collation: mp_menu.reply_title = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_description IS E'回复的描述';
-- MySQL column charset/collation: mp_menu.reply_description = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_thumb_media_id IS E'回复的缩略图的媒体 id';
-- MySQL column charset/collation: mp_menu.reply_thumb_media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_thumb_media_url IS E'回复的缩略图的媒体 URL';
-- MySQL column charset/collation: mp_menu.reply_thumb_media_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_articles IS E'回复的图文消息数组';
-- MySQL column charset/collation: mp_menu.reply_articles = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_music_url IS E'回复的音乐链接';
-- MySQL column charset/collation: mp_menu.reply_music_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.reply_hq_music_url IS E'回复的高质量音乐链接';
-- MySQL column charset/collation: mp_menu.reply_hq_music_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_menu.creator IS E'创建者';
-- MySQL column charset/collation: mp_menu.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_menu.create_time IS E'创建时间';
COMMENT ON COLUMN mp_menu.updater IS E'更新者';
-- MySQL column charset/collation: mp_menu.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_menu.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_menu_update_time
BEFORE UPDATE ON mp_menu
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_menu.deleted IS E'是否删除';
COMMENT ON COLUMN mp_menu.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_message
-- ----------------------------
CREATE SEQUENCE mp_message_seq AS bigint START WITH 436;
CREATE TABLE mp_message (
  id bigint DEFAULT nextval('mp_message_seq'::regclass) NOT NULL,
  msg_id bigint DEFAULT NULL NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  user_id bigint NOT NULL,
  openid varchar(100) NOT NULL,
  type varchar(32) NOT NULL,
  send_from smallint NOT NULL,
  content varchar(1024) DEFAULT NULL NULL,
  media_id varchar(128) DEFAULT NULL NULL,
  media_url varchar(1024) DEFAULT NULL NULL,
  recognition varchar(1024) DEFAULT NULL NULL,
  format varchar(16) DEFAULT NULL NULL,
  title varchar(128) DEFAULT NULL NULL,
  description varchar(256) DEFAULT NULL NULL,
  thumb_media_id varchar(128) DEFAULT NULL NULL,
  thumb_media_url varchar(1024) DEFAULT NULL NULL,
  url varchar(500) DEFAULT NULL NULL,
  location_x double precision DEFAULT NULL NULL,
  location_y double precision DEFAULT NULL NULL,
  scale double precision DEFAULT NULL NULL,
  label varchar(128) DEFAULT NULL NULL,
  articles varchar(1024) DEFAULT NULL NULL,
  music_url varchar(1024) DEFAULT NULL NULL,
  hq_music_url varchar(1024) DEFAULT NULL NULL,
  event varchar(64) DEFAULT NULL NULL,
  event_key varchar(64) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_message_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_message_seq OWNED BY mp_message.id;
-- MySQL index name: idx_account_id
CREATE INDEX mp_message_idx_account_id ON mp_message (account_id);
COMMENT ON TABLE mp_message IS E'公众号消息表 ';
-- MySQL table charset/collation: utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.id IS E'主键';
COMMENT ON COLUMN mp_message.msg_id IS E'微信公众号的消息编号';
COMMENT ON COLUMN mp_message.account_id IS E'公众号账号的编号';
COMMENT ON COLUMN mp_message.app_id IS E'公众号 appId';
-- MySQL column charset/collation: mp_message.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message.user_id IS E'公众号粉丝的编号';
COMMENT ON COLUMN mp_message.openid IS E'公众号粉丝标志';
-- MySQL column charset/collation: mp_message.openid = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.type IS E'消息类型';
-- MySQL column charset/collation: mp_message.type = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.send_from IS E'消息来源';
COMMENT ON COLUMN mp_message.content IS E'消息内容';
-- MySQL column charset/collation: mp_message.content = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.media_id IS E'媒体文件 id';
-- MySQL column charset/collation: mp_message.media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.media_url IS E'媒体文件 URL';
-- MySQL column charset/collation: mp_message.media_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.recognition IS E'语音识别后文本';
-- MySQL column charset/collation: mp_message.recognition = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.format IS E'语音格式';
-- MySQL column charset/collation: mp_message.format = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.title IS E'标题';
-- MySQL column charset/collation: mp_message.title = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.description IS E'描述';
-- MySQL column charset/collation: mp_message.description = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.thumb_media_id IS E'缩略图的媒体 id';
-- MySQL column charset/collation: mp_message.thumb_media_id = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.thumb_media_url IS E'缩略图的媒体 URL';
-- MySQL column charset/collation: mp_message.thumb_media_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.url IS E'点击图文消息跳转链接';
-- MySQL column charset/collation: mp_message.url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.location_x IS E'地理位置维度';
COMMENT ON COLUMN mp_message.location_y IS E'地理位置经度';
COMMENT ON COLUMN mp_message.scale IS E'地图缩放大小';
COMMENT ON COLUMN mp_message.label IS E'详细地址';
-- MySQL column charset/collation: mp_message.label = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.articles IS E'图文消息数组';
-- MySQL column charset/collation: mp_message.articles = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.music_url IS E'音乐链接';
-- MySQL column charset/collation: mp_message.music_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.hq_music_url IS E'高质量音乐链接';
-- MySQL column charset/collation: mp_message.hq_music_url = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.event IS E'事件类型';
-- MySQL column charset/collation: mp_message.event = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.event_key IS E'事件 Key';
-- MySQL column charset/collation: mp_message.event_key = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_message.creator IS E'创建者';
-- MySQL column charset/collation: mp_message.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message.create_time IS E'创建时间';
COMMENT ON COLUMN mp_message.updater IS E'更新者';
-- MySQL column charset/collation: mp_message.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_message_update_time
BEFORE UPDATE ON mp_message
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_message.deleted IS E'是否删除';
COMMENT ON COLUMN mp_message.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_message_template
-- ----------------------------
CREATE SEQUENCE mp_message_template_seq AS bigint START WITH 68;
CREATE TABLE mp_message_template (
  id bigint DEFAULT nextval('mp_message_template_seq'::regclass) NOT NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  template_id varchar(100) NOT NULL,
  title varchar(20) DEFAULT NULL NULL,
  content varchar(1024) DEFAULT NULL NULL,
  example varchar(200) DEFAULT NULL NULL,
  primary_industry varchar(50) DEFAULT NULL NULL,
  deputy_industry varchar(50) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_message_template_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_message_template_seq OWNED BY mp_message_template.id;
-- MySQL index name: idx_account_id
CREATE INDEX mp_message_template_idx_account_id ON mp_message_template (account_id);
COMMENT ON TABLE mp_message_template IS E'公众号模板消息';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.id IS E'主键';
COMMENT ON COLUMN mp_message_template.account_id IS E'公众号账号的编号';
COMMENT ON COLUMN mp_message_template.app_id IS E'公众号 appId';
-- MySQL column charset/collation: mp_message_template.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.template_id IS E'公众号模板ID';
-- MySQL column charset/collation: mp_message_template.template_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.title IS E'标题';
-- MySQL column charset/collation: mp_message_template.title = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.content IS E'模板内容';
-- MySQL column charset/collation: mp_message_template.content = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.example IS E'模板示例';
-- MySQL column charset/collation: mp_message_template.example = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.primary_industry IS E'模板所属行业的一级行业';
-- MySQL column charset/collation: mp_message_template.primary_industry = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.deputy_industry IS E'模板所属行业的二级行业';
-- MySQL column charset/collation: mp_message_template.deputy_industry = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.creator IS E'创建者';
-- MySQL column charset/collation: mp_message_template.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.create_time IS E'创建时间';
COMMENT ON COLUMN mp_message_template.updater IS E'更新者';
-- MySQL column charset/collation: mp_message_template.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_message_template.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_message_template_update_time
BEFORE UPDATE ON mp_message_template
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_message_template.deleted IS E'是否删除';
COMMENT ON COLUMN mp_message_template.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_tag
-- ----------------------------
CREATE SEQUENCE mp_tag_seq AS bigint START WITH 18;
CREATE TABLE mp_tag (
  id bigint DEFAULT nextval('mp_tag_seq'::regclass) NOT NULL,
  tag_id bigint DEFAULT NULL NULL,
  name varchar(32) DEFAULT NULL NULL,
  count integer DEFAULT 0 NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_tag_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_tag_seq OWNED BY mp_tag.id;
-- MySQL index name: idx_account_id
CREATE INDEX mp_tag_idx_account_id ON mp_tag (account_id);
COMMENT ON TABLE mp_tag IS E'公众号标签表';
-- MySQL table charset/collation: utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_tag.id IS E'主键';
COMMENT ON COLUMN mp_tag.tag_id IS E'公众号标签 id';
COMMENT ON COLUMN mp_tag.name IS E'标签名称';
-- MySQL column charset/collation: mp_tag.name = utf8mb3 / utf8mb3_general_ci
COMMENT ON COLUMN mp_tag.count IS E'粉丝数量';
COMMENT ON COLUMN mp_tag.account_id IS E'公众号账号的编号';
COMMENT ON COLUMN mp_tag.app_id IS E'公众号 appId';
-- MySQL column charset/collation: mp_tag.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_tag.creator IS E'创建者';
-- MySQL column charset/collation: mp_tag.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_tag.create_time IS E'创建时间';
COMMENT ON COLUMN mp_tag.updater IS E'更新者';
-- MySQL column charset/collation: mp_tag.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_tag.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_tag_update_time
BEFORE UPDATE ON mp_tag
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_tag.deleted IS E'是否删除';
COMMENT ON COLUMN mp_tag.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for mp_user
-- ----------------------------
CREATE SEQUENCE mp_user_seq AS bigint START WITH 66;
CREATE TABLE mp_user (
  id bigint DEFAULT nextval('mp_user_seq'::regclass) NOT NULL,
  openid varchar(100) NOT NULL,
  union_id varchar(100) DEFAULT NULL NULL,
  subscribe_status smallint NOT NULL,
  subscribe_time timestamp without time zone NOT NULL,
  nickname varchar(64) DEFAULT NULL NULL,
  head_image_url varchar(1024) DEFAULT NULL NULL,
  unsubscribe_time timestamp without time zone DEFAULT NULL NULL,
  language varchar(30) DEFAULT NULL NULL,
  country varchar(30) DEFAULT NULL NULL,
  province varchar(30) DEFAULT NULL NULL,
  city varchar(30) DEFAULT NULL NULL,
  remark varchar(128) DEFAULT NULL NULL,
  tag_ids varchar(255) DEFAULT NULL NULL,
  account_id bigint NOT NULL,
  app_id varchar(128) NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT mp_user_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE mp_user_seq OWNED BY mp_user.id;
-- MySQL index name: idx_app_id_openid
CREATE INDEX mp_user_idx_app_id_openid ON mp_user (app_id, openid);
-- MySQL index name: idx_account_id
CREATE INDEX mp_user_idx_account_id ON mp_user (account_id);
COMMENT ON TABLE mp_user IS E'公众号粉丝表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.id IS E'编号';
COMMENT ON COLUMN mp_user.openid IS E'用户标识';
-- MySQL column charset/collation: mp_user.openid = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.union_id IS E'微信生态唯一标识';
-- MySQL column charset/collation: mp_user.union_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.subscribe_status IS E'关注状态';
COMMENT ON COLUMN mp_user.subscribe_time IS E'关注时间';
COMMENT ON COLUMN mp_user.nickname IS E'昵称';
-- MySQL column charset/collation: mp_user.nickname = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.head_image_url IS E'头像地址';
-- MySQL column charset/collation: mp_user.head_image_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.unsubscribe_time IS E'取消关注时间';
COMMENT ON COLUMN mp_user.language IS E'语言';
-- MySQL column charset/collation: mp_user.language = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.country IS E'国家';
-- MySQL column charset/collation: mp_user.country = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.province IS E'省份';
-- MySQL column charset/collation: mp_user.province = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.city IS E'城市';
-- MySQL column charset/collation: mp_user.city = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.remark IS E'备注';
-- MySQL column charset/collation: mp_user.remark = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.tag_ids IS E'标签编号数组';
-- MySQL column charset/collation: mp_user.tag_ids = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.account_id IS E'微信公众号ID';
COMMENT ON COLUMN mp_user.app_id IS E'微信公众号 appid';
-- MySQL column charset/collation: mp_user.app_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.creator IS E'创建者';
-- MySQL column charset/collation: mp_user.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.create_time IS E'创建时间';
COMMENT ON COLUMN mp_user.updater IS E'更新者';
-- MySQL column charset/collation: mp_user.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN mp_user.update_time IS E'更新时间';
CREATE TRIGGER trg_mp_user_update_time
BEFORE UPDATE ON mp_user
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN mp_user.deleted IS E'是否删除';
COMMENT ON COLUMN mp_user.tenant_id IS E'租户编号';

COMMIT;
