-- PostgreSQL schema for yudao-module-member.
-- Generated from backend/database/member-2026-07-26.sql; source SHA-256: ba58737b971a260b6f1424d5819641e48c9ab0fd671c27404248a96294d69019.
-- Destructive: drops and recreates only this module's tables and sequences.
-- Run tools/module-sql-sync.mjs --check to verify this file.

BEGIN;

DROP TABLE IF EXISTS member_user;
DROP TABLE IF EXISTS member_tag;
DROP TABLE IF EXISTS member_sign_in_record;
DROP TABLE IF EXISTS member_sign_in_config;
DROP TABLE IF EXISTS member_point_record;
DROP TABLE IF EXISTS member_level_record;
DROP TABLE IF EXISTS member_level;
DROP TABLE IF EXISTS member_group;
DROP TABLE IF EXISTS member_experience_record;
DROP TABLE IF EXISTS member_config;
DROP TABLE IF EXISTS member_address;
DROP SEQUENCE IF EXISTS member_user_seq;
DROP SEQUENCE IF EXISTS member_tag_seq;
DROP SEQUENCE IF EXISTS member_sign_in_record_seq;
DROP SEQUENCE IF EXISTS member_sign_in_config_seq;
DROP SEQUENCE IF EXISTS member_point_record_seq;
DROP SEQUENCE IF EXISTS member_level_record_seq;
DROP SEQUENCE IF EXISTS member_level_seq;
DROP SEQUENCE IF EXISTS member_group_seq;
DROP SEQUENCE IF EXISTS member_experience_record_seq;
DROP SEQUENCE IF EXISTS member_config_seq;
DROP SEQUENCE IF EXISTS member_address_seq;

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
-- Table structure for member_address
-- ----------------------------
CREATE SEQUENCE member_address_seq AS bigint START WITH 37;
CREATE TABLE member_address (
  id bigint DEFAULT nextval('member_address_seq'::regclass) NOT NULL,
  user_id bigint NOT NULL,
  name varchar(10) NOT NULL,
  mobile varchar(20) NOT NULL,
  area_id bigint NOT NULL,
  detail_address varchar(250) NOT NULL,
  default_status smallint NOT NULL CHECK (default_status IN (0, 1)),
  version bigint DEFAULT 1 NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_address_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_address_seq OWNED BY member_address.id;
-- MySQL index name: idx_userId
CREATE INDEX member_address_idx_userid ON member_address (user_id);
COMMENT ON TABLE member_address IS E'用户收件地址';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN member_address.id IS E'收件地址编号';
COMMENT ON COLUMN member_address.user_id IS E'用户编号';
COMMENT ON COLUMN member_address.name IS E'收件人名称';
-- MySQL column charset/collation: member_address.name = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN member_address.mobile IS E'手机号';
-- MySQL column charset/collation: member_address.mobile = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN member_address.area_id IS E'地区编码';
COMMENT ON COLUMN member_address.detail_address IS E'收件详细地址';
-- MySQL column charset/collation: member_address.detail_address = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN member_address.default_status IS E'是否默认';
COMMENT ON COLUMN member_address.version IS E'业务版本号';
COMMENT ON COLUMN member_address.creator IS E'创建者';
-- MySQL column charset/collation: member_address.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_address.create_time IS E'创建时间';
COMMENT ON COLUMN member_address.updater IS E'更新者';
-- MySQL column charset/collation: member_address.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_address.update_time IS E'更新时间';
CREATE TRIGGER trg_member_address_update_time
BEFORE UPDATE ON member_address
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_address.deleted IS E'是否删除';
COMMENT ON COLUMN member_address.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_config
-- ----------------------------
CREATE SEQUENCE member_config_seq AS bigint START WITH 6;
CREATE TABLE member_config (
  id bigint DEFAULT nextval('member_config_seq'::regclass) NOT NULL,
  point_trade_deduct_enable smallint NOT NULL CHECK (point_trade_deduct_enable IN (0, 1)),
  point_trade_deduct_unit_price integer NOT NULL,
  point_trade_deduct_max_price integer DEFAULT NULL NULL,
  point_trade_give_point bigint DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_config_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_config_seq OWNED BY member_config.id;
COMMENT ON TABLE member_config IS E'会员配置表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_config.id IS E'自增主键';
COMMENT ON COLUMN member_config.point_trade_deduct_enable IS E'是否开启积分抵扣';
COMMENT ON COLUMN member_config.point_trade_deduct_unit_price IS E'积分抵扣(单位：分)';
COMMENT ON COLUMN member_config.point_trade_deduct_max_price IS E'积分抵扣最大值';
COMMENT ON COLUMN member_config.point_trade_give_point IS E'1 元赠送多少分';
COMMENT ON COLUMN member_config.creator IS E'创建者';
-- MySQL column charset/collation: member_config.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_config.create_time IS E'创建时间';
COMMENT ON COLUMN member_config.updater IS E'更新者';
-- MySQL column charset/collation: member_config.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_config.update_time IS E'更新时间';
CREATE TRIGGER trg_member_config_update_time
BEFORE UPDATE ON member_config
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_config.deleted IS E'是否删除';
COMMENT ON COLUMN member_config.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_experience_record
-- ----------------------------
CREATE SEQUENCE member_experience_record_seq AS bigint START WITH 125;
CREATE TABLE member_experience_record (
  id bigint DEFAULT nextval('member_experience_record_seq'::regclass) NOT NULL,
  user_id bigint DEFAULT 0 NOT NULL,
  biz_id varchar(64) DEFAULT E'' NOT NULL,
  biz_type smallint DEFAULT 0 NOT NULL,
  title varchar(30) DEFAULT E'' NOT NULL,
  description varchar(512) DEFAULT E'' NOT NULL,
  experience integer DEFAULT 0 NOT NULL,
  total_experience integer DEFAULT 0 NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_experience_record_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_experience_record_seq OWNED BY member_experience_record.id;
-- MySQL index name: idx_user_id
CREATE INDEX member_experience_record_idx_user_id ON member_experience_record (user_id);
COMMENT ON INDEX member_experience_record_idx_user_id IS E'会员经验记录-用户编号';
-- MySQL index name: idx_user_biz_type
CREATE INDEX member_experience_record_idx_user_biz_type ON member_experience_record (user_id, biz_type);
COMMENT ON INDEX member_experience_record_idx_user_biz_type IS E'会员经验记录-用户业务类型';
COMMENT ON TABLE member_experience_record IS E'会员经验记录';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_experience_record.id IS E'编号';
COMMENT ON COLUMN member_experience_record.user_id IS E'用户编号';
COMMENT ON COLUMN member_experience_record.biz_id IS E'业务编号';
-- MySQL column charset/collation: member_experience_record.biz_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_experience_record.biz_type IS E'业务类型';
COMMENT ON COLUMN member_experience_record.title IS E'标题';
-- MySQL column charset/collation: member_experience_record.title = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_experience_record.description IS E'描述';
-- MySQL column charset/collation: member_experience_record.description = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_experience_record.experience IS E'经验';
COMMENT ON COLUMN member_experience_record.total_experience IS E'变更后的经验';
COMMENT ON COLUMN member_experience_record.creator IS E'创建者';
-- MySQL column charset/collation: member_experience_record.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_experience_record.create_time IS E'创建时间';
COMMENT ON COLUMN member_experience_record.updater IS E'更新者';
-- MySQL column charset/collation: member_experience_record.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_experience_record.update_time IS E'更新时间';
CREATE TRIGGER trg_member_experience_record_update_time
BEFORE UPDATE ON member_experience_record
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_experience_record.deleted IS E'是否删除';
COMMENT ON COLUMN member_experience_record.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_group
-- ----------------------------
CREATE SEQUENCE member_group_seq AS bigint START WITH 2;
CREATE TABLE member_group (
  id bigint DEFAULT nextval('member_group_seq'::regclass) NOT NULL,
  name varchar(30) DEFAULT E'' NOT NULL,
  status smallint DEFAULT 0 NOT NULL,
  remark varchar(255) DEFAULT E'' NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_group_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_group_seq OWNED BY member_group.id;
COMMENT ON TABLE member_group IS E'用户分组';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_group.id IS E'编号';
COMMENT ON COLUMN member_group.name IS E'名称';
-- MySQL column charset/collation: member_group.name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_group.status IS E'状态';
COMMENT ON COLUMN member_group.remark IS E'备注';
-- MySQL column charset/collation: member_group.remark = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_group.creator IS E'创建者';
-- MySQL column charset/collation: member_group.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_group.create_time IS E'创建时间';
COMMENT ON COLUMN member_group.updater IS E'更新者';
-- MySQL column charset/collation: member_group.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_group.update_time IS E'更新时间';
CREATE TRIGGER trg_member_group_update_time
BEFORE UPDATE ON member_group
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_group.deleted IS E'是否删除';
COMMENT ON COLUMN member_group.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_level
-- ----------------------------
CREATE SEQUENCE member_level_seq AS bigint START WITH 4;
CREATE TABLE member_level (
  id bigint DEFAULT nextval('member_level_seq'::regclass) NOT NULL,
  name varchar(30) DEFAULT E'' NOT NULL,
  level integer DEFAULT 0 NOT NULL,
  experience integer DEFAULT 0 NOT NULL,
  discount_percent smallint DEFAULT 100 NOT NULL,
  icon varchar(255) DEFAULT E'' NOT NULL,
  background_url varchar(255) DEFAULT E'' NOT NULL,
  status smallint DEFAULT 0 NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_level_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_level_seq OWNED BY member_level.id;
COMMENT ON TABLE member_level IS E'会员等级';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level.id IS E'编号';
COMMENT ON COLUMN member_level.name IS E'等级名称';
-- MySQL column charset/collation: member_level.name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level.level IS E'等级';
COMMENT ON COLUMN member_level.experience IS E'升级经验';
COMMENT ON COLUMN member_level.discount_percent IS E'享受折扣';
COMMENT ON COLUMN member_level.icon IS E'等级图标';
-- MySQL column charset/collation: member_level.icon = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level.background_url IS E'等级背景图';
-- MySQL column charset/collation: member_level.background_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level.status IS E'状态';
COMMENT ON COLUMN member_level.creator IS E'创建者';
-- MySQL column charset/collation: member_level.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level.create_time IS E'创建时间';
COMMENT ON COLUMN member_level.updater IS E'更新者';
-- MySQL column charset/collation: member_level.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level.update_time IS E'更新时间';
CREATE TRIGGER trg_member_level_update_time
BEFORE UPDATE ON member_level
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_level.deleted IS E'是否删除';
COMMENT ON COLUMN member_level.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_level_record
-- ----------------------------
CREATE SEQUENCE member_level_record_seq AS bigint START WITH 45;
CREATE TABLE member_level_record (
  id bigint DEFAULT nextval('member_level_record_seq'::regclass) NOT NULL,
  user_id bigint DEFAULT 0 NOT NULL,
  level_id bigint DEFAULT 0 NOT NULL,
  level integer DEFAULT 0 NOT NULL,
  discount_percent smallint DEFAULT 100 NOT NULL,
  experience integer DEFAULT 0 NOT NULL,
  user_experience integer DEFAULT 0 NOT NULL,
  remark varchar(255) DEFAULT E'' NOT NULL,
  description varchar(255) DEFAULT E'' NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_level_record_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_level_record_seq OWNED BY member_level_record.id;
-- MySQL index name: idx_user_id
CREATE INDEX member_level_record_idx_user_id ON member_level_record (user_id);
COMMENT ON INDEX member_level_record_idx_user_id IS E'会员等级记录-用户编号';
COMMENT ON TABLE member_level_record IS E'会员等级记录';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level_record.id IS E'编号';
COMMENT ON COLUMN member_level_record.user_id IS E'用户编号';
COMMENT ON COLUMN member_level_record.level_id IS E'等级编号';
COMMENT ON COLUMN member_level_record.level IS E'会员等级';
COMMENT ON COLUMN member_level_record.discount_percent IS E'享受折扣';
COMMENT ON COLUMN member_level_record.experience IS E'升级经验';
COMMENT ON COLUMN member_level_record.user_experience IS E'会员此时的经验';
COMMENT ON COLUMN member_level_record.remark IS E'备注';
-- MySQL column charset/collation: member_level_record.remark = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level_record.description IS E'描述';
-- MySQL column charset/collation: member_level_record.description = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level_record.creator IS E'创建者';
-- MySQL column charset/collation: member_level_record.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level_record.create_time IS E'创建时间';
COMMENT ON COLUMN member_level_record.updater IS E'更新者';
-- MySQL column charset/collation: member_level_record.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_level_record.update_time IS E'更新时间';
CREATE TRIGGER trg_member_level_record_update_time
BEFORE UPDATE ON member_level_record
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_level_record.deleted IS E'是否删除';
COMMENT ON COLUMN member_level_record.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_point_record
-- ----------------------------
CREATE SEQUENCE member_point_record_seq AS bigint START WITH 167;
CREATE TABLE member_point_record (
  id bigint DEFAULT nextval('member_point_record_seq'::regclass) NOT NULL,
  user_id bigint NOT NULL,
  biz_id varchar(255) NOT NULL,
  biz_type smallint NOT NULL,
  title varchar(255) NOT NULL,
  description varchar(5000) DEFAULT NULL NULL,
  point integer NOT NULL,
  total_point integer NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_point_record_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_point_record_seq OWNED BY member_point_record.id;
-- MySQL index name: index_userId
CREATE INDEX member_point_record_index_userid ON member_point_record (user_id);
COMMENT ON TABLE member_point_record IS E'用户积分记录';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_point_record.id IS E'自增主键';
COMMENT ON COLUMN member_point_record.user_id IS E'用户编号';
COMMENT ON COLUMN member_point_record.biz_id IS E'业务编码';
-- MySQL column charset/collation: member_point_record.biz_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_point_record.biz_type IS E'业务类型';
COMMENT ON COLUMN member_point_record.title IS E'积分标题';
-- MySQL column charset/collation: member_point_record.title = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_point_record.description IS E'积分描述';
-- MySQL column charset/collation: member_point_record.description = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_point_record.point IS E'积分';
COMMENT ON COLUMN member_point_record.total_point IS E'变动后的积分';
COMMENT ON COLUMN member_point_record.creator IS E'创建者';
-- MySQL column charset/collation: member_point_record.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_point_record.create_time IS E'创建时间';
COMMENT ON COLUMN member_point_record.updater IS E'更新者';
-- MySQL column charset/collation: member_point_record.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_point_record.update_time IS E'更新时间';
CREATE TRIGGER trg_member_point_record_update_time
BEFORE UPDATE ON member_point_record
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_point_record.deleted IS E'是否删除';
COMMENT ON COLUMN member_point_record.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_sign_in_config
-- ----------------------------
CREATE SEQUENCE member_sign_in_config_seq AS bigint START WITH 13;
CREATE TABLE member_sign_in_config (
  id integer DEFAULT nextval('member_sign_in_config_seq'::regclass) NOT NULL,
  day integer NOT NULL,
  point integer NOT NULL,
  experience integer DEFAULT 0 NOT NULL,
  status smallint NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_sign_in_config_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_sign_in_config_seq OWNED BY member_sign_in_config.id;
COMMENT ON TABLE member_sign_in_config IS E'签到规则';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_sign_in_config.id IS E'编号';
COMMENT ON COLUMN member_sign_in_config.day IS E'第几天';
COMMENT ON COLUMN member_sign_in_config.point IS E'奖励积分';
COMMENT ON COLUMN member_sign_in_config.experience IS E'奖励经验';
COMMENT ON COLUMN member_sign_in_config.status IS E'状态';
COMMENT ON COLUMN member_sign_in_config.creator IS E'创建者';
-- MySQL column charset/collation: member_sign_in_config.creator = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_sign_in_config.create_time IS E'创建时间';
COMMENT ON COLUMN member_sign_in_config.updater IS E'更新者';
-- MySQL column charset/collation: member_sign_in_config.updater = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_sign_in_config.update_time IS E'更新时间';
CREATE TRIGGER trg_member_sign_in_config_update_time
BEFORE UPDATE ON member_sign_in_config
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_sign_in_config.deleted IS E'是否删除';
COMMENT ON COLUMN member_sign_in_config.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_sign_in_record
-- ----------------------------
CREATE SEQUENCE member_sign_in_record_seq AS bigint START WITH 15;
CREATE TABLE member_sign_in_record (
  id bigint DEFAULT nextval('member_sign_in_record_seq'::regclass) NOT NULL,
  user_id integer DEFAULT NULL NULL,
  day integer DEFAULT NULL NULL,
  point integer DEFAULT 0 NOT NULL,
  experience integer DEFAULT 0 NOT NULL,
  sign_date date DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_sign_in_record_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_sign_in_record_seq OWNED BY member_sign_in_record.id;
-- MySQL index name: idx_user_id_create_time
CREATE INDEX member_sign_in_record_idx_user_id_create_time ON member_sign_in_record (user_id, create_time);
-- MySQL index name: uk_tenant_user_sign_date
CREATE UNIQUE INDEX member_sign_in_record_uk_tenant_user_sign_date ON member_sign_in_record (tenant_id, user_id, sign_date);
COMMENT ON TABLE member_sign_in_record IS E'签到记录';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_sign_in_record.id IS E'签到自增id';
COMMENT ON COLUMN member_sign_in_record.user_id IS E'签到用户';
COMMENT ON COLUMN member_sign_in_record.day IS E'第几天签到';
COMMENT ON COLUMN member_sign_in_record.point IS E'签到的分数';
COMMENT ON COLUMN member_sign_in_record.experience IS E'奖励经验';
COMMENT ON COLUMN member_sign_in_record.sign_date IS E'业务签到日期（历史记录可为空）';
COMMENT ON COLUMN member_sign_in_record.creator IS E'创建者';
-- MySQL column charset/collation: member_sign_in_record.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_sign_in_record.create_time IS E'创建时间';
COMMENT ON COLUMN member_sign_in_record.updater IS E'更新者';
-- MySQL column charset/collation: member_sign_in_record.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_sign_in_record.update_time IS E'更新时间';
CREATE TRIGGER trg_member_sign_in_record_update_time
BEFORE UPDATE ON member_sign_in_record
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_sign_in_record.deleted IS E'是否删除';
COMMENT ON COLUMN member_sign_in_record.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_tag
-- ----------------------------
CREATE SEQUENCE member_tag_seq AS bigint START WITH 3;
CREATE TABLE member_tag (
  id bigint DEFAULT nextval('member_tag_seq'::regclass) NOT NULL,
  name varchar(30) DEFAULT E'' NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_tag_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_tag_seq OWNED BY member_tag.id;
COMMENT ON TABLE member_tag IS E'会员标签';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_tag.id IS E'编号';
COMMENT ON COLUMN member_tag.name IS E'标签名称';
-- MySQL column charset/collation: member_tag.name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_tag.creator IS E'创建者';
-- MySQL column charset/collation: member_tag.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_tag.create_time IS E'创建时间';
COMMENT ON COLUMN member_tag.updater IS E'更新者';
-- MySQL column charset/collation: member_tag.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_tag.update_time IS E'更新时间';
CREATE TRIGGER trg_member_tag_update_time
BEFORE UPDATE ON member_tag
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_tag.deleted IS E'是否删除';
COMMENT ON COLUMN member_tag.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for member_user
-- ----------------------------
CREATE SEQUENCE member_user_seq AS bigint START WITH 298;
CREATE TABLE member_user (
  id bigint DEFAULT nextval('member_user_seq'::regclass) NOT NULL,
  mobile varchar(11) DEFAULT NULL NULL,
  email varchar(50) DEFAULT NULL NULL,
  password varchar(100) DEFAULT E'' NOT NULL,
  status smallint NOT NULL,
  register_ip varchar(32) NOT NULL,
  register_terminal smallint DEFAULT NULL NULL,
  login_ip varchar(50) DEFAULT E'' NULL,
  login_date timestamp without time zone DEFAULT NULL NULL,
  nickname varchar(30) DEFAULT E'' NOT NULL,
  avatar varchar(512) DEFAULT E'' NOT NULL,
  profile_version bigint DEFAULT 1 NOT NULL,
  name varchar(30) DEFAULT E'' NULL,
  sex smallint DEFAULT 0 NULL,
  area_id bigint DEFAULT NULL NULL,
  birthday timestamp without time zone DEFAULT NULL NULL,
  mark varchar(255) DEFAULT NULL NULL,
  point integer DEFAULT 0 NOT NULL,
  tag_ids varchar(255) DEFAULT NULL NULL,
  level_id bigint DEFAULT NULL NULL,
  experience integer DEFAULT 0 NOT NULL,
  group_id bigint DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT member_user_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE member_user_seq OWNED BY member_user.id;
-- MySQL index name: idx_mobile
CREATE UNIQUE INDEX member_user_idx_mobile ON member_user (mobile, tenant_id);
COMMENT ON TABLE member_user IS E'会员用户';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.id IS E'编号';
COMMENT ON COLUMN member_user.mobile IS E'手机号';
-- MySQL column charset/collation: member_user.mobile = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.email IS E'邮箱';
-- MySQL column charset/collation: member_user.email = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.password IS E'密码';
-- MySQL column charset/collation: member_user.password = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.status IS E'状态';
COMMENT ON COLUMN member_user.register_ip IS E'注册 IP';
-- MySQL column charset/collation: member_user.register_ip = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.register_terminal IS E'注册终端';
COMMENT ON COLUMN member_user.login_ip IS E'最后登录IP';
-- MySQL column charset/collation: member_user.login_ip = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN member_user.login_date IS E'最后登录时间';
COMMENT ON COLUMN member_user.nickname IS E'用户昵称';
-- MySQL column charset/collation: member_user.nickname = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.avatar IS E'头像';
-- MySQL column charset/collation: member_user.avatar = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.profile_version IS E'个人资料业务版本号';
COMMENT ON COLUMN member_user.name IS E'真实名字';
-- MySQL column charset/collation: member_user.name = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.sex IS E'用户性别';
COMMENT ON COLUMN member_user.area_id IS E'所在地';
COMMENT ON COLUMN member_user.birthday IS E'出生日期';
COMMENT ON COLUMN member_user.mark IS E'会员备注';
-- MySQL column charset/collation: member_user.mark = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.point IS E'积分';
COMMENT ON COLUMN member_user.tag_ids IS E'用户标签编号列表，以逗号分隔';
-- MySQL column charset/collation: member_user.tag_ids = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.level_id IS E'等级编号';
COMMENT ON COLUMN member_user.experience IS E'经验';
COMMENT ON COLUMN member_user.group_id IS E'用户分组编号';
COMMENT ON COLUMN member_user.creator IS E'创建者';
-- MySQL column charset/collation: member_user.creator = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.create_time IS E'创建时间';
COMMENT ON COLUMN member_user.updater IS E'更新者';
-- MySQL column charset/collation: member_user.updater = utf8mb4 / utf8mb4_general_ci
COMMENT ON COLUMN member_user.update_time IS E'更新时间';
CREATE TRIGGER trg_member_user_update_time
BEFORE UPDATE ON member_user
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN member_user.deleted IS E'是否删除';
COMMENT ON COLUMN member_user.tenant_id IS E'租户编号';

COMMIT;
