-- PostgreSQL schema for yudao-module-pay.

BEGIN;

DROP TABLE IF EXISTS pay_wallet_transaction;
DROP TABLE IF EXISTS pay_wallet_recharge_package;
DROP TABLE IF EXISTS pay_wallet_recharge;
DROP TABLE IF EXISTS pay_wallet;
DROP TABLE IF EXISTS pay_transfer;
DROP TABLE IF EXISTS pay_refund;
DROP TABLE IF EXISTS pay_order_extension;
DROP TABLE IF EXISTS pay_order;
DROP TABLE IF EXISTS pay_notify_task;
DROP TABLE IF EXISTS pay_notify_log;
DROP TABLE IF EXISTS pay_demo_withdraw;
DROP TABLE IF EXISTS pay_demo_order;
DROP TABLE IF EXISTS pay_channel;
DROP TABLE IF EXISTS pay_app;
DROP SEQUENCE IF EXISTS pay_wallet_transaction_seq;
DROP SEQUENCE IF EXISTS pay_wallet_recharge_package_seq;
DROP SEQUENCE IF EXISTS pay_wallet_recharge_seq;
DROP SEQUENCE IF EXISTS pay_wallet_seq;
DROP SEQUENCE IF EXISTS pay_transfer_seq;
DROP SEQUENCE IF EXISTS pay_refund_seq;
DROP SEQUENCE IF EXISTS pay_order_extension_seq;
DROP SEQUENCE IF EXISTS pay_order_seq;
DROP SEQUENCE IF EXISTS pay_notify_task_seq;
DROP SEQUENCE IF EXISTS pay_notify_log_seq;
DROP SEQUENCE IF EXISTS pay_demo_withdraw_seq;
DROP SEQUENCE IF EXISTS pay_demo_order_seq;
DROP SEQUENCE IF EXISTS pay_channel_seq;
DROP SEQUENCE IF EXISTS pay_app_seq;

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
-- Table structure for pay_app
-- ----------------------------
CREATE SEQUENCE pay_app_seq AS bigint START WITH 10;
CREATE TABLE pay_app (
  id bigint DEFAULT nextval('pay_app_seq'::regclass) NOT NULL,
  app_key varchar(64) NOT NULL,
  name varchar(64) NOT NULL,
  status smallint NOT NULL,
  remark varchar(255) DEFAULT NULL NULL,
  order_notify_url varchar(1024) NOT NULL,
  refund_notify_url varchar(1024) NOT NULL,
  transfer_notify_url varchar(1024) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_app_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_app_seq OWNED BY pay_app.id;
-- MySQL index name: idx_app_key
CREATE INDEX pay_app_idx_app_key ON pay_app (app_key);
COMMENT ON TABLE pay_app IS E'支付应用信息';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.id IS E'应用编号';
COMMENT ON COLUMN pay_app.app_key IS E'应用标识';
-- MySQL column charset/collation: pay_app.app_key = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.name IS E'应用名';
-- MySQL column charset/collation: pay_app.name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.status IS E'开启状态';
COMMENT ON COLUMN pay_app.remark IS E'备注';
-- MySQL column charset/collation: pay_app.remark = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.order_notify_url IS E'支付结果的回调地址';
-- MySQL column charset/collation: pay_app.order_notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.refund_notify_url IS E'退款结果的回调地址';
-- MySQL column charset/collation: pay_app.refund_notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.transfer_notify_url IS E'转账结果的回调地址';
-- MySQL column charset/collation: pay_app.transfer_notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.creator IS E'创建者';
-- MySQL column charset/collation: pay_app.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.create_time IS E'创建时间';
COMMENT ON COLUMN pay_app.updater IS E'更新者';
-- MySQL column charset/collation: pay_app.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_app.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_app_update_time
BEFORE UPDATE ON pay_app
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_app.deleted IS E'是否删除';
COMMENT ON COLUMN pay_app.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_channel
-- ----------------------------
CREATE SEQUENCE pay_channel_seq AS bigint START WITH 51;
CREATE TABLE pay_channel (
  id bigint DEFAULT nextval('pay_channel_seq'::regclass) NOT NULL,
  code varchar(32) NOT NULL,
  status smallint NOT NULL,
  remark varchar(255) DEFAULT NULL NULL,
  fee_rate double precision DEFAULT 0 NOT NULL,
  app_id bigint NOT NULL,
  config varchar(15000) NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_channel_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_channel_seq OWNED BY pay_channel.id;
-- MySQL index name: idx_app_id_code
CREATE INDEX pay_channel_idx_app_id_code ON pay_channel (app_id, code);
COMMENT ON TABLE pay_channel IS E'支付渠道\n';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_channel.id IS E'商户编号';
COMMENT ON COLUMN pay_channel.code IS E'渠道编码';
-- MySQL column charset/collation: pay_channel.code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_channel.status IS E'开启状态';
COMMENT ON COLUMN pay_channel.remark IS E'备注';
-- MySQL column charset/collation: pay_channel.remark = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_channel.fee_rate IS E'渠道费率，单位：百分比';
COMMENT ON COLUMN pay_channel.app_id IS E'应用编号';
COMMENT ON COLUMN pay_channel.config IS E'支付渠道配置';
-- MySQL column charset/collation: pay_channel.config = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_channel.creator IS E'创建者';
-- MySQL column charset/collation: pay_channel.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_channel.create_time IS E'创建时间';
COMMENT ON COLUMN pay_channel.updater IS E'更新者';
-- MySQL column charset/collation: pay_channel.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_channel.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_channel_update_time
BEFORE UPDATE ON pay_channel
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_channel.deleted IS E'是否删除';
COMMENT ON COLUMN pay_channel.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_demo_order
-- ----------------------------
CREATE SEQUENCE pay_demo_order_seq AS bigint START WITH 208;
CREATE TABLE pay_demo_order (
  id bigint DEFAULT nextval('pay_demo_order_seq'::regclass) NOT NULL,
  user_id numeric(20, 0) NOT NULL CHECK (user_id BETWEEN 0 AND 18446744073709551615),
  spu_id bigint NOT NULL,
  spu_name varchar(255) NOT NULL,
  price integer NOT NULL,
  pay_status smallint DEFAULT 0 NOT NULL CHECK (pay_status IN (0, 1)),
  pay_order_id bigint DEFAULT NULL NULL,
  pay_channel_code varchar(16) DEFAULT NULL NULL,
  pay_time timestamp without time zone DEFAULT NULL NULL,
  pay_refund_id bigint DEFAULT NULL NULL,
  refund_price integer DEFAULT 0 NOT NULL,
  refund_time timestamp without time zone DEFAULT NULL NULL,
  transfer_channel_package_info varchar(2048) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_demo_order_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_demo_order_seq OWNED BY pay_demo_order.id;
COMMENT ON TABLE pay_demo_order IS E'示例订单\n';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_order.id IS E'订单编号';
COMMENT ON COLUMN pay_demo_order.user_id IS E'用户编号';
COMMENT ON COLUMN pay_demo_order.spu_id IS E'商品编号';
COMMENT ON COLUMN pay_demo_order.spu_name IS E'商品名字';
-- MySQL column charset/collation: pay_demo_order.spu_name = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_order.price IS E'价格，单位：分';
COMMENT ON COLUMN pay_demo_order.pay_status IS E'是否已支付：[0:未支付 1:已经支付过]';
COMMENT ON COLUMN pay_demo_order.pay_order_id IS E'支付订单编号';
COMMENT ON COLUMN pay_demo_order.pay_channel_code IS E'支付成功的支付渠道';
-- MySQL column charset/collation: pay_demo_order.pay_channel_code = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_order.pay_time IS E'订单支付时间';
COMMENT ON COLUMN pay_demo_order.pay_refund_id IS E'退款订单编号';
COMMENT ON COLUMN pay_demo_order.refund_price IS E'退款金额，单位：分';
COMMENT ON COLUMN pay_demo_order.refund_time IS E'退款时间';
COMMENT ON COLUMN pay_demo_order.transfer_channel_package_info IS E'渠道 package 信息';
-- MySQL column charset/collation: pay_demo_order.transfer_channel_package_info = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_demo_order.creator IS E'创建者';
-- MySQL column charset/collation: pay_demo_order.creator = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_order.create_time IS E'创建时间';
COMMENT ON COLUMN pay_demo_order.updater IS E'更新者';
-- MySQL column charset/collation: pay_demo_order.updater = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_order.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_demo_order_update_time
BEFORE UPDATE ON pay_demo_order
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_demo_order.deleted IS E'是否删除';
COMMENT ON COLUMN pay_demo_order.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_demo_withdraw
-- ----------------------------
CREATE SEQUENCE pay_demo_withdraw_seq AS bigint START WITH 68;
CREATE TABLE pay_demo_withdraw (
  id bigint DEFAULT nextval('pay_demo_withdraw_seq'::regclass) NOT NULL,
  subject varchar(32) NOT NULL,
  price integer NOT NULL,
  user_account varchar(64) NOT NULL,
  user_name varchar(64) DEFAULT NULL NULL,
  type smallint NOT NULL,
  status smallint NOT NULL,
  pay_transfer_id bigint DEFAULT NULL NULL,
  transfer_channel_code varchar(16) DEFAULT NULL NULL,
  transfer_time timestamp without time zone DEFAULT NULL NULL,
  transfer_error_msg varchar(4096) DEFAULT E'' NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_demo_withdraw_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_demo_withdraw_seq OWNED BY pay_demo_withdraw.id;
COMMENT ON TABLE pay_demo_withdraw IS E'示例业务提现单';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_demo_withdraw.id IS E'提现单编号';
COMMENT ON COLUMN pay_demo_withdraw.subject IS E'提现标题';
-- MySQL column charset/collation: pay_demo_withdraw.subject = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_demo_withdraw.price IS E'提现金额，单位：分';
COMMENT ON COLUMN pay_demo_withdraw.user_account IS E'收款人账号';
-- MySQL column charset/collation: pay_demo_withdraw.user_account = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_demo_withdraw.user_name IS E'收款人姓名';
-- MySQL column charset/collation: pay_demo_withdraw.user_name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_demo_withdraw.type IS E'提现方式';
COMMENT ON COLUMN pay_demo_withdraw.status IS E'提现状态';
COMMENT ON COLUMN pay_demo_withdraw.pay_transfer_id IS E'转账订单编号';
COMMENT ON COLUMN pay_demo_withdraw.transfer_channel_code IS E'转账渠道';
-- MySQL column charset/collation: pay_demo_withdraw.transfer_channel_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_demo_withdraw.transfer_time IS E'转账支付时间';
COMMENT ON COLUMN pay_demo_withdraw.transfer_error_msg IS E'转账错误提示';
-- MySQL column charset/collation: pay_demo_withdraw.transfer_error_msg = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_withdraw.creator IS E'创建者';
-- MySQL column charset/collation: pay_demo_withdraw.creator = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_withdraw.create_time IS E'创建时间';
COMMENT ON COLUMN pay_demo_withdraw.updater IS E'更新者';
-- MySQL column charset/collation: pay_demo_withdraw.updater = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_demo_withdraw.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_demo_withdraw_update_time
BEFORE UPDATE ON pay_demo_withdraw
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_demo_withdraw.deleted IS E'是否删除';
COMMENT ON COLUMN pay_demo_withdraw.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_notify_log
-- ----------------------------
CREATE SEQUENCE pay_notify_log_seq AS bigint START WITH 372371;
CREATE TABLE pay_notify_log (
  id bigint DEFAULT nextval('pay_notify_log_seq'::regclass) NOT NULL,
  task_id bigint NOT NULL,
  notify_times smallint NOT NULL,
  response varchar(2048) NOT NULL,
  status smallint NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_notify_log_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_notify_log_seq OWNED BY pay_notify_log.id;
-- MySQL index name: idx_task_id
CREATE INDEX pay_notify_log_idx_task_id ON pay_notify_log (task_id);
COMMENT ON TABLE pay_notify_log IS E'支付通知 App 的日志';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_log.id IS E'日志编号';
COMMENT ON COLUMN pay_notify_log.task_id IS E'通知任务编号';
COMMENT ON COLUMN pay_notify_log.notify_times IS E'第几次被通知';
COMMENT ON COLUMN pay_notify_log.response IS E'请求参数';
-- MySQL column charset/collation: pay_notify_log.response = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_log.status IS E'通知状态';
COMMENT ON COLUMN pay_notify_log.creator IS E'创建者';
-- MySQL column charset/collation: pay_notify_log.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_log.create_time IS E'创建时间';
COMMENT ON COLUMN pay_notify_log.updater IS E'更新者';
-- MySQL column charset/collation: pay_notify_log.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_log.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_notify_log_update_time
BEFORE UPDATE ON pay_notify_log
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_notify_log.deleted IS E'是否删除';
COMMENT ON COLUMN pay_notify_log.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_notify_task
-- ----------------------------
CREATE SEQUENCE pay_notify_task_seq AS bigint START WITH 554;
CREATE TABLE pay_notify_task (
  id bigint DEFAULT nextval('pay_notify_task_seq'::regclass) NOT NULL,
  app_id bigint NOT NULL,
  type smallint NOT NULL,
  data_id bigint NOT NULL,
  merchant_order_id varchar(64) DEFAULT NULL NULL,
  merchant_refund_id varchar(64) DEFAULT NULL NULL,
  merchant_transfer_id varchar(64) DEFAULT NULL NULL,
  status smallint NOT NULL,
  next_notify_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  last_execute_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  notify_times smallint NOT NULL,
  max_notify_times smallint NOT NULL,
  notify_url varchar(1024) NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_notify_task_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_notify_task_seq OWNED BY pay_notify_task.id;
-- MySQL index name: idx_status_next_notify_time
CREATE INDEX pay_notify_task_idx_status_next_notify_time ON pay_notify_task (status, next_notify_time);
-- MySQL index name: idx_app_id_status_create_time
CREATE INDEX pay_notify_task_idx_app_id_status_create_time ON pay_notify_task (app_id, status, create_time);
COMMENT ON TABLE pay_notify_task IS E'商户支付、退款等的通知\n';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.id IS E'任务编号';
COMMENT ON COLUMN pay_notify_task.app_id IS E'应用编号';
COMMENT ON COLUMN pay_notify_task.type IS E'通知类型';
COMMENT ON COLUMN pay_notify_task.data_id IS E'数据编号';
COMMENT ON COLUMN pay_notify_task.merchant_order_id IS E'商户订单编号（商户系统生成）';
-- MySQL column charset/collation: pay_notify_task.merchant_order_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.merchant_refund_id IS E'商户退款编号（商户系统生成）';
-- MySQL column charset/collation: pay_notify_task.merchant_refund_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.merchant_transfer_id IS E'商户转账编号（商户系统生成）';
-- MySQL column charset/collation: pay_notify_task.merchant_transfer_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.status IS E'通知状态';
COMMENT ON COLUMN pay_notify_task.next_notify_time IS E'下一次通知时间';
COMMENT ON COLUMN pay_notify_task.last_execute_time IS E'最后一次执行时间';
COMMENT ON COLUMN pay_notify_task.notify_times IS E'当前通知次数';
COMMENT ON COLUMN pay_notify_task.max_notify_times IS E'最大可通知次数';
COMMENT ON COLUMN pay_notify_task.notify_url IS E'异步通知商户地址';
-- MySQL column charset/collation: pay_notify_task.notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.creator IS E'创建者';
-- MySQL column charset/collation: pay_notify_task.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.create_time IS E'创建时间';
COMMENT ON COLUMN pay_notify_task.updater IS E'更新者';
-- MySQL column charset/collation: pay_notify_task.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_notify_task.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_notify_task_update_time
BEFORE UPDATE ON pay_notify_task
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_notify_task.deleted IS E'是否删除';
COMMENT ON COLUMN pay_notify_task.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_order
-- ----------------------------
CREATE SEQUENCE pay_order_seq AS bigint START WITH 566;
CREATE TABLE pay_order (
  id bigint DEFAULT nextval('pay_order_seq'::regclass) NOT NULL,
  app_id bigint NOT NULL,
  channel_id bigint DEFAULT NULL NULL,
  channel_code varchar(32) DEFAULT NULL NULL,
  user_id bigint DEFAULT NULL NULL,
  user_type smallint DEFAULT NULL NULL,
  merchant_order_id varchar(64) NOT NULL,
  subject varchar(32) NOT NULL,
  body varchar(128) NOT NULL,
  notify_url varchar(1024) NOT NULL,
  price bigint NOT NULL,
  channel_fee_rate double precision DEFAULT 0 NULL,
  channel_fee_price bigint DEFAULT 0 NULL,
  status smallint NOT NULL,
  user_ip varchar(50) NOT NULL,
  expire_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  success_time timestamp without time zone DEFAULT NULL NULL,
  extension_id bigint DEFAULT NULL NULL,
  no varchar(64) DEFAULT NULL NULL,
  refund_price bigint NOT NULL,
  channel_user_id varchar(255) DEFAULT NULL NULL,
  channel_order_no varchar(64) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_order_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_order_seq OWNED BY pay_order.id;
-- MySQL index name: idx_app_id_merchant_order_id
CREATE INDEX pay_order_idx_app_id_merchant_order_id ON pay_order (app_id, merchant_order_id);
-- MySQL index name: idx_no
CREATE INDEX pay_order_idx_no ON pay_order (no);
-- MySQL index name: idx_status_expire_time
CREATE INDEX pay_order_idx_status_expire_time ON pay_order (status, expire_time);
COMMENT ON TABLE pay_order IS E'支付订单\n';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.id IS E'支付订单编号';
COMMENT ON COLUMN pay_order.app_id IS E'应用编号';
COMMENT ON COLUMN pay_order.channel_id IS E'渠道编号';
COMMENT ON COLUMN pay_order.channel_code IS E'渠道编码';
-- MySQL column charset/collation: pay_order.channel_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.user_id IS E'用户编号';
COMMENT ON COLUMN pay_order.user_type IS E'用户类型';
COMMENT ON COLUMN pay_order.merchant_order_id IS E'商户订单编号';
-- MySQL column charset/collation: pay_order.merchant_order_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.subject IS E'商品标题';
-- MySQL column charset/collation: pay_order.subject = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.body IS E'商品描述';
-- MySQL column charset/collation: pay_order.body = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.notify_url IS E'异步通知地址';
-- MySQL column charset/collation: pay_order.notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.price IS E'支付金额，单位：分';
COMMENT ON COLUMN pay_order.channel_fee_rate IS E'渠道手续费，单位：百分比';
COMMENT ON COLUMN pay_order.channel_fee_price IS E'渠道手续金额，单位：分';
COMMENT ON COLUMN pay_order.status IS E'支付状态';
COMMENT ON COLUMN pay_order.user_ip IS E'用户 IP';
-- MySQL column charset/collation: pay_order.user_ip = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.expire_time IS E'订单失效时间';
COMMENT ON COLUMN pay_order.success_time IS E'订单支付成功时间';
COMMENT ON COLUMN pay_order.extension_id IS E'支付成功的订单拓展单编号';
COMMENT ON COLUMN pay_order.no IS E'支付订单号';
-- MySQL column charset/collation: pay_order.no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.refund_price IS E'退款总金额，单位：分';
COMMENT ON COLUMN pay_order.channel_user_id IS E'渠道用户编号';
-- MySQL column charset/collation: pay_order.channel_user_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.channel_order_no IS E'渠道订单号';
-- MySQL column charset/collation: pay_order.channel_order_no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.creator IS E'创建者';
-- MySQL column charset/collation: pay_order.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.create_time IS E'创建时间';
COMMENT ON COLUMN pay_order.updater IS E'更新者';
-- MySQL column charset/collation: pay_order.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_order_update_time
BEFORE UPDATE ON pay_order
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_order.deleted IS E'是否删除';
COMMENT ON COLUMN pay_order.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_order_extension
-- ----------------------------
CREATE SEQUENCE pay_order_extension_seq AS bigint START WITH 1152;
CREATE TABLE pay_order_extension (
  id bigint DEFAULT nextval('pay_order_extension_seq'::regclass) NOT NULL,
  no varchar(64) NOT NULL,
  order_id bigint NOT NULL,
  channel_id bigint NOT NULL,
  channel_code varchar(32) NOT NULL,
  user_ip varchar(50) NOT NULL,
  status smallint NOT NULL,
  channel_extras varchar(256) DEFAULT NULL NULL,
  channel_error_code varchar(128) DEFAULT NULL NULL,
  channel_error_msg varchar(256) DEFAULT NULL NULL,
  channel_notify_data varchar(4096) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_order_extension_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_order_extension_seq OWNED BY pay_order_extension.id;
-- MySQL index name: idx_no
CREATE INDEX pay_order_extension_idx_no ON pay_order_extension (no);
-- MySQL index name: idx_order_id_status
CREATE INDEX pay_order_extension_idx_order_id_status ON pay_order_extension (order_id, status);
-- MySQL index name: idx_status_create_time
CREATE INDEX pay_order_extension_idx_status_create_time ON pay_order_extension (status, create_time);
COMMENT ON TABLE pay_order_extension IS E'支付订单\n';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.id IS E'支付订单编号';
COMMENT ON COLUMN pay_order_extension.no IS E'支付订单号';
-- MySQL column charset/collation: pay_order_extension.no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.order_id IS E'支付订单编号';
COMMENT ON COLUMN pay_order_extension.channel_id IS E'渠道编号';
COMMENT ON COLUMN pay_order_extension.channel_code IS E'渠道编码';
-- MySQL column charset/collation: pay_order_extension.channel_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.user_ip IS E'用户 IP';
-- MySQL column charset/collation: pay_order_extension.user_ip = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.status IS E'支付状态';
COMMENT ON COLUMN pay_order_extension.channel_extras IS E'支付渠道的额外参数';
-- MySQL column charset/collation: pay_order_extension.channel_extras = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.channel_error_code IS E'渠道调用报错时，错误码';
-- MySQL column charset/collation: pay_order_extension.channel_error_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.channel_error_msg IS E'渠道调用报错时，错误信息';
-- MySQL column charset/collation: pay_order_extension.channel_error_msg = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.channel_notify_data IS E'支付渠道异步通知的内容';
-- MySQL column charset/collation: pay_order_extension.channel_notify_data = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.creator IS E'创建者';
-- MySQL column charset/collation: pay_order_extension.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.create_time IS E'创建时间';
COMMENT ON COLUMN pay_order_extension.updater IS E'更新者';
-- MySQL column charset/collation: pay_order_extension.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_order_extension.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_order_extension_update_time
BEFORE UPDATE ON pay_order_extension
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_order_extension.deleted IS E'是否删除';
COMMENT ON COLUMN pay_order_extension.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_refund
-- ----------------------------
CREATE SEQUENCE pay_refund_seq AS bigint START WITH 120;
CREATE TABLE pay_refund (
  id bigint DEFAULT nextval('pay_refund_seq'::regclass) NOT NULL,
  no varchar(64) NOT NULL,
  app_id bigint NOT NULL,
  channel_id bigint NOT NULL,
  channel_code varchar(32) NOT NULL,
  order_id bigint NOT NULL,
  order_no varchar(64) NOT NULL,
  user_id bigint DEFAULT NULL NULL,
  user_type smallint DEFAULT NULL NULL,
  merchant_order_id varchar(64) NOT NULL,
  merchant_refund_id varchar(64) NOT NULL,
  notify_url varchar(1024) NOT NULL,
  status smallint NOT NULL,
  pay_price bigint NOT NULL,
  refund_price bigint NOT NULL,
  reason varchar(256) NOT NULL,
  user_ip varchar(50) DEFAULT NULL NULL,
  channel_order_no varchar(64) NOT NULL,
  channel_refund_no varchar(64) DEFAULT NULL NULL,
  success_time timestamp without time zone DEFAULT NULL NULL,
  channel_error_code varchar(128) DEFAULT NULL NULL,
  channel_error_msg varchar(256) DEFAULT NULL NULL,
  channel_notify_data varchar(4096) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_refund_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_refund_seq OWNED BY pay_refund.id;
-- MySQL index name: idx_app_id_merchant_refund_id
CREATE INDEX pay_refund_idx_app_id_merchant_refund_id ON pay_refund (app_id, merchant_refund_id);
-- MySQL index name: idx_no
CREATE INDEX pay_refund_idx_no ON pay_refund (no);
-- MySQL index name: idx_app_id_order_id_status
CREATE INDEX pay_refund_idx_app_id_order_id_status ON pay_refund (app_id, order_id, status);
-- MySQL index name: idx_status
CREATE INDEX pay_refund_idx_status ON pay_refund (status);
COMMENT ON TABLE pay_refund IS E'退款订单';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.id IS E'支付退款编号';
COMMENT ON COLUMN pay_refund.no IS E'退款单号';
-- MySQL column charset/collation: pay_refund.no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.app_id IS E'应用编号';
COMMENT ON COLUMN pay_refund.channel_id IS E'渠道编号';
COMMENT ON COLUMN pay_refund.channel_code IS E'渠道编码';
-- MySQL column charset/collation: pay_refund.channel_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.order_id IS E'支付订单编号 pay_order 表id';
COMMENT ON COLUMN pay_refund.order_no IS E'支付订单 no';
-- MySQL column charset/collation: pay_refund.order_no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.user_id IS E'用户编号';
COMMENT ON COLUMN pay_refund.user_type IS E'用户类型';
COMMENT ON COLUMN pay_refund.merchant_order_id IS E'商户订单编号（商户系统生成）';
-- MySQL column charset/collation: pay_refund.merchant_order_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.merchant_refund_id IS E'商户退款订单号（商户系统生成）';
-- MySQL column charset/collation: pay_refund.merchant_refund_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.notify_url IS E'异步通知商户地址';
-- MySQL column charset/collation: pay_refund.notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.status IS E'退款状态';
COMMENT ON COLUMN pay_refund.pay_price IS E'支付金额,单位分';
COMMENT ON COLUMN pay_refund.refund_price IS E'退款金额,单位分';
COMMENT ON COLUMN pay_refund.reason IS E'退款原因';
-- MySQL column charset/collation: pay_refund.reason = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.user_ip IS E'用户 IP';
-- MySQL column charset/collation: pay_refund.user_ip = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.channel_order_no IS E'渠道订单号，pay_order 中的 channel_order_no 对应';
-- MySQL column charset/collation: pay_refund.channel_order_no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.channel_refund_no IS E'渠道退款单号，渠道返回';
-- MySQL column charset/collation: pay_refund.channel_refund_no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.success_time IS E'退款成功时间';
COMMENT ON COLUMN pay_refund.channel_error_code IS E'渠道调用报错时，错误码';
-- MySQL column charset/collation: pay_refund.channel_error_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.channel_error_msg IS E'渠道调用报错时，错误信息';
-- MySQL column charset/collation: pay_refund.channel_error_msg = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.channel_notify_data IS E'支付渠道异步通知的内容';
-- MySQL column charset/collation: pay_refund.channel_notify_data = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.creator IS E'创建者';
-- MySQL column charset/collation: pay_refund.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.create_time IS E'创建时间';
COMMENT ON COLUMN pay_refund.updater IS E'更新者';
-- MySQL column charset/collation: pay_refund.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_refund.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_refund_update_time
BEFORE UPDATE ON pay_refund
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_refund.deleted IS E'是否删除';
COMMENT ON COLUMN pay_refund.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_transfer
-- ----------------------------
CREATE SEQUENCE pay_transfer_seq AS bigint START WITH 88;
CREATE TABLE pay_transfer (
  id bigint DEFAULT nextval('pay_transfer_seq'::regclass) NOT NULL,
  no varchar(64) NOT NULL,
  app_id bigint NOT NULL,
  channel_id bigint NOT NULL,
  channel_code varchar(32) NOT NULL,
  user_id bigint DEFAULT NULL NULL,
  user_type smallint DEFAULT NULL NULL,
  merchant_transfer_id varchar(64) NOT NULL,
  status smallint NOT NULL,
  success_time timestamp without time zone DEFAULT NULL NULL,
  price integer NOT NULL,
  subject varchar(512) NOT NULL,
  user_name varchar(64) DEFAULT NULL NULL,
  user_account varchar(64) NOT NULL,
  notify_url varchar(1024) NOT NULL,
  user_ip varchar(50) NOT NULL,
  channel_extras varchar(512) DEFAULT NULL NULL,
  channel_transfer_no varchar(64) DEFAULT NULL NULL,
  channel_error_code varchar(128) DEFAULT NULL NULL,
  channel_error_msg varchar(256) DEFAULT NULL NULL,
  channel_notify_data varchar(4096) DEFAULT NULL NULL,
  channel_package_info varchar(2048) DEFAULT NULL NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_transfer_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_transfer_seq OWNED BY pay_transfer.id;
-- MySQL index name: idx_app_id_merchant_transfer_id
CREATE INDEX pay_transfer_idx_app_id_merchant_transfer_id ON pay_transfer (app_id, merchant_transfer_id);
-- MySQL index name: idx_no
CREATE INDEX pay_transfer_idx_no ON pay_transfer (no);
-- MySQL index name: idx_status
CREATE INDEX pay_transfer_idx_status ON pay_transfer (status);
COMMENT ON TABLE pay_transfer IS E'转账单表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.id IS E'编号';
COMMENT ON COLUMN pay_transfer.no IS E'转账单号';
-- MySQL column charset/collation: pay_transfer.no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.app_id IS E'应用编号';
COMMENT ON COLUMN pay_transfer.channel_id IS E'转账渠道编号';
COMMENT ON COLUMN pay_transfer.channel_code IS E'转账渠道编码';
-- MySQL column charset/collation: pay_transfer.channel_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.user_id IS E'用户编号';
COMMENT ON COLUMN pay_transfer.user_type IS E'用户类型';
COMMENT ON COLUMN pay_transfer.merchant_transfer_id IS E'商户转账单编号';
-- MySQL column charset/collation: pay_transfer.merchant_transfer_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.status IS E'转账状态';
COMMENT ON COLUMN pay_transfer.success_time IS E'转账成功时间';
COMMENT ON COLUMN pay_transfer.price IS E'转账金额，单位：分';
COMMENT ON COLUMN pay_transfer.subject IS E'转账标题';
-- MySQL column charset/collation: pay_transfer.subject = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.user_name IS E'收款人姓名';
-- MySQL column charset/collation: pay_transfer.user_name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.user_account IS E'收款人账号';
-- MySQL column charset/collation: pay_transfer.user_account = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.notify_url IS E'异步通知商户地址';
-- MySQL column charset/collation: pay_transfer.notify_url = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.user_ip IS E'用户 IP';
-- MySQL column charset/collation: pay_transfer.user_ip = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.channel_extras IS E'渠道的额外参数';
-- MySQL column charset/collation: pay_transfer.channel_extras = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.channel_transfer_no IS E'渠道转账单号';
-- MySQL column charset/collation: pay_transfer.channel_transfer_no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.channel_error_code IS E'调用渠道的错误码';
-- MySQL column charset/collation: pay_transfer.channel_error_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.channel_error_msg IS E'调用渠道的错误提示';
-- MySQL column charset/collation: pay_transfer.channel_error_msg = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.channel_notify_data IS E'渠道的同步/异步通知的内容';
-- MySQL column charset/collation: pay_transfer.channel_notify_data = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.channel_package_info IS E'渠道 package 信息';
-- MySQL column charset/collation: pay_transfer.channel_package_info = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.creator IS E'创建者';
-- MySQL column charset/collation: pay_transfer.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.create_time IS E'创建时间';
COMMENT ON COLUMN pay_transfer.updater IS E'更新者';
-- MySQL column charset/collation: pay_transfer.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_transfer.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_transfer_update_time
BEFORE UPDATE ON pay_transfer
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_transfer.deleted IS E'是否删除';
COMMENT ON COLUMN pay_transfer.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_wallet
-- ----------------------------
CREATE SEQUENCE pay_wallet_seq AS bigint START WITH 26;
CREATE TABLE pay_wallet (
  id bigint DEFAULT nextval('pay_wallet_seq'::regclass) NOT NULL,
  user_id bigint NOT NULL,
  user_type smallint DEFAULT 0 NOT NULL,
  balance integer DEFAULT 0 NOT NULL,
  total_expense integer DEFAULT 0 NOT NULL,
  total_recharge integer DEFAULT 0 NOT NULL,
  freeze_price integer DEFAULT 0 NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_wallet_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_wallet_seq OWNED BY pay_wallet.id;
-- MySQL index name: idx_user_id_user_type
CREATE INDEX pay_wallet_idx_user_id_user_type ON pay_wallet (user_id, user_type);
COMMENT ON TABLE pay_wallet IS E'会员钱包表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet.id IS E'编号';
COMMENT ON COLUMN pay_wallet.user_id IS E'用户编号';
COMMENT ON COLUMN pay_wallet.user_type IS E'用户类型';
COMMENT ON COLUMN pay_wallet.balance IS E'余额，单位分';
COMMENT ON COLUMN pay_wallet.total_expense IS E'累计支出，单位分';
COMMENT ON COLUMN pay_wallet.total_recharge IS E'累计充值，单位分';
COMMENT ON COLUMN pay_wallet.freeze_price IS E'冻结金额，单位分';
COMMENT ON COLUMN pay_wallet.creator IS E'创建者';
-- MySQL column charset/collation: pay_wallet.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet.create_time IS E'创建时间';
COMMENT ON COLUMN pay_wallet.updater IS E'更新者';
-- MySQL column charset/collation: pay_wallet.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_wallet_update_time
BEFORE UPDATE ON pay_wallet
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_wallet.deleted IS E'是否删除';
COMMENT ON COLUMN pay_wallet.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_wallet_recharge
-- ----------------------------
CREATE SEQUENCE pay_wallet_recharge_seq AS bigint START WITH 37;
CREATE TABLE pay_wallet_recharge (
  id bigint DEFAULT nextval('pay_wallet_recharge_seq'::regclass) NOT NULL,
  wallet_id bigint NOT NULL,
  total_price integer NOT NULL,
  pay_price integer NOT NULL,
  bonus_price integer NOT NULL,
  package_id bigint DEFAULT NULL NULL,
  pay_status smallint DEFAULT 0 NOT NULL CHECK (pay_status IN (0, 1)),
  pay_order_id bigint DEFAULT NULL NULL,
  pay_channel_code varchar(16) DEFAULT NULL NULL,
  pay_time timestamp without time zone DEFAULT NULL NULL,
  pay_refund_id bigint DEFAULT NULL NULL,
  refund_total_price integer DEFAULT 0 NOT NULL,
  refund_pay_price integer DEFAULT 0 NOT NULL,
  refund_bonus_price integer DEFAULT 0 NOT NULL,
  refund_time timestamp without time zone DEFAULT NULL NULL,
  refund_status integer DEFAULT 0 NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_wallet_recharge_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_wallet_recharge_seq OWNED BY pay_wallet_recharge.id;
-- MySQL index name: idx_wallet_id_pay_status_id
CREATE INDEX pay_wallet_recharge_idx_wallet_id_pay_status_id ON pay_wallet_recharge (wallet_id, pay_status, id);
COMMENT ON TABLE pay_wallet_recharge IS E'会员钱包充值';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge.id IS E'编号';
COMMENT ON COLUMN pay_wallet_recharge.wallet_id IS E'会员钱包 id';
COMMENT ON COLUMN pay_wallet_recharge.total_price IS E'用户实际到账余额，例如充 100 送 20，则该值是 120';
COMMENT ON COLUMN pay_wallet_recharge.pay_price IS E'实际支付金额';
COMMENT ON COLUMN pay_wallet_recharge.bonus_price IS E'钱包赠送金额';
COMMENT ON COLUMN pay_wallet_recharge.package_id IS E'充值套餐编号';
COMMENT ON COLUMN pay_wallet_recharge.pay_status IS E'是否已支付：[0:未支付 1:已经支付过]';
COMMENT ON COLUMN pay_wallet_recharge.pay_order_id IS E'支付订单编号';
COMMENT ON COLUMN pay_wallet_recharge.pay_channel_code IS E'支付成功的支付渠道';
-- MySQL column charset/collation: pay_wallet_recharge.pay_channel_code = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge.pay_time IS E'订单支付时间';
COMMENT ON COLUMN pay_wallet_recharge.pay_refund_id IS E'支付退款单编号';
COMMENT ON COLUMN pay_wallet_recharge.refund_total_price IS E'退款金额，包含赠送金额';
COMMENT ON COLUMN pay_wallet_recharge.refund_pay_price IS E'退款支付金额';
COMMENT ON COLUMN pay_wallet_recharge.refund_bonus_price IS E'退款钱包赠送金额';
COMMENT ON COLUMN pay_wallet_recharge.refund_time IS E'退款时间';
COMMENT ON COLUMN pay_wallet_recharge.refund_status IS E'退款状态';
COMMENT ON COLUMN pay_wallet_recharge.creator IS E'创建者';
-- MySQL column charset/collation: pay_wallet_recharge.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge.create_time IS E'创建时间';
COMMENT ON COLUMN pay_wallet_recharge.updater IS E'更新者';
-- MySQL column charset/collation: pay_wallet_recharge.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_wallet_recharge_update_time
BEFORE UPDATE ON pay_wallet_recharge
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_wallet_recharge.deleted IS E'是否删除';
COMMENT ON COLUMN pay_wallet_recharge.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_wallet_recharge_package
-- ----------------------------
CREATE SEQUENCE pay_wallet_recharge_package_seq AS bigint START WITH 3;
CREATE TABLE pay_wallet_recharge_package (
  id bigint DEFAULT nextval('pay_wallet_recharge_package_seq'::regclass) NOT NULL,
  name varchar(64) NOT NULL,
  pay_price integer NOT NULL,
  bonus_price integer NOT NULL,
  status smallint NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_wallet_recharge_package_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_wallet_recharge_package_seq OWNED BY pay_wallet_recharge_package.id;
COMMENT ON TABLE pay_wallet_recharge_package IS E'充值套餐表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge_package.id IS E'编号';
COMMENT ON COLUMN pay_wallet_recharge_package.name IS E'套餐名';
-- MySQL column charset/collation: pay_wallet_recharge_package.name = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge_package.pay_price IS E'支付金额';
COMMENT ON COLUMN pay_wallet_recharge_package.bonus_price IS E'赠送金额';
COMMENT ON COLUMN pay_wallet_recharge_package.status IS E'状态';
COMMENT ON COLUMN pay_wallet_recharge_package.creator IS E'创建者';
-- MySQL column charset/collation: pay_wallet_recharge_package.creator = utf8mb4 / utf8mb4_bin
COMMENT ON COLUMN pay_wallet_recharge_package.create_time IS E'创建时间';
COMMENT ON COLUMN pay_wallet_recharge_package.updater IS E'更新者';
-- MySQL column charset/collation: pay_wallet_recharge_package.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_recharge_package.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_wallet_recharge_package_update_time
BEFORE UPDATE ON pay_wallet_recharge_package
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_wallet_recharge_package.deleted IS E'是否删除';
COMMENT ON COLUMN pay_wallet_recharge_package.tenant_id IS E'租户编号';

-- ----------------------------
-- Table structure for pay_wallet_transaction
-- ----------------------------
CREATE SEQUENCE pay_wallet_transaction_seq AS bigint START WITH 79;
CREATE TABLE pay_wallet_transaction (
  id bigint DEFAULT nextval('pay_wallet_transaction_seq'::regclass) NOT NULL,
  wallet_id bigint NOT NULL,
  biz_type smallint NOT NULL,
  biz_id varchar(64) NOT NULL,
  no varchar(64) NOT NULL,
  title varchar(128) NOT NULL,
  price integer NOT NULL,
  balance integer NOT NULL,
  creator varchar(64) DEFAULT E'' NULL,
  create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updater varchar(64) DEFAULT E'' NULL,
  update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted smallint DEFAULT 0 NOT NULL CHECK (deleted IN (0, 1)),
  tenant_id bigint DEFAULT 0 NOT NULL,
  CONSTRAINT pay_wallet_transaction_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE pay_wallet_transaction_seq OWNED BY pay_wallet_transaction.id;
-- MySQL index name: idx_no
CREATE INDEX pay_wallet_transaction_idx_no ON pay_wallet_transaction (no);
-- MySQL index name: idx_wallet_id_id
CREATE INDEX pay_wallet_transaction_idx_wallet_id_id ON pay_wallet_transaction (wallet_id, id);
-- MySQL index name: idx_wallet_id_create_time
CREATE INDEX pay_wallet_transaction_idx_wallet_id_create_time ON pay_wallet_transaction (wallet_id, create_time);
-- MySQL index name: idx_biz_id_biz_type
CREATE INDEX pay_wallet_transaction_idx_biz_id_biz_type ON pay_wallet_transaction (biz_id, biz_type);
COMMENT ON TABLE pay_wallet_transaction IS E'会员钱包流水表';
-- MySQL table charset/collation: utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_transaction.id IS E'编号';
COMMENT ON COLUMN pay_wallet_transaction.wallet_id IS E'会员钱包 id';
COMMENT ON COLUMN pay_wallet_transaction.biz_type IS E'关联类型';
COMMENT ON COLUMN pay_wallet_transaction.biz_id IS E'关联业务编号';
-- MySQL column charset/collation: pay_wallet_transaction.biz_id = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_transaction.no IS E'流水号';
-- MySQL column charset/collation: pay_wallet_transaction.no = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_transaction.title IS E'流水标题';
-- MySQL column charset/collation: pay_wallet_transaction.title = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_transaction.price IS E'交易金额, 单位分';
COMMENT ON COLUMN pay_wallet_transaction.balance IS E'余额, 单位分';
COMMENT ON COLUMN pay_wallet_transaction.creator IS E'创建者';
-- MySQL column charset/collation: pay_wallet_transaction.creator = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_transaction.create_time IS E'创建时间';
COMMENT ON COLUMN pay_wallet_transaction.updater IS E'更新者';
-- MySQL column charset/collation: pay_wallet_transaction.updater = utf8mb4 / utf8mb4_unicode_ci
COMMENT ON COLUMN pay_wallet_transaction.update_time IS E'更新时间';
CREATE TRIGGER trg_pay_wallet_transaction_update_time
BEFORE UPDATE ON pay_wallet_transaction
FOR EACH ROW
EXECUTE FUNCTION mysql_on_update_current_timestamp();
COMMENT ON COLUMN pay_wallet_transaction.deleted IS E'是否删除';
COMMENT ON COLUMN pay_wallet_transaction.tenant_id IS E'租户编号';

COMMIT;
