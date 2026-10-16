USE shopmind_merchant;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_merchant;

CREATE TABLE t_merchant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商家ID',
    merchant_code VARCHAR(32) NOT NULL UNIQUE COMMENT '商家编号',
    merchant_name VARCHAR(64) NOT NULL COMMENT '店铺名称',
    system_prompt TEXT COMMENT 'AI客服系统人设指令',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1:正常 0:禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家与AI人设表';

CREATE TABLE t_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    merchant_id BIGINT NOT NULL COMMENT '所属商家ID',
    product_code VARCHAR(32) NOT NULL UNIQUE COMMENT '商品编码',
    product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
    category VARCHAR(64) COMMENT '类目',
    price DECIMAL(10, 2) NOT NULL COMMENT '售价',
    stock_count INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    description VARCHAR(512) COMMENT '商品介绍',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1:在架 0:下架',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

CREATE TABLE t_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    merchant_id BIGINT NOT NULL COMMENT '商家ID',
    user_id BIGINT NOT NULL COMMENT '买家ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
    quantity INT NOT NULL DEFAULT 1 COMMENT '数量',
    total_amount DECIMAL(10, 2) NOT NULL COMMENT '总金额',
    order_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-已支付 2-已发货 3-已完成 4-已退款',
    express_no VARCHAR(64) COMMENT '物流单号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_user (user_id),
    KEY idx_order_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

-- 插入初始种子数据
INSERT INTO t_merchant (id, merchant_code, merchant_name, system_prompt, status)
VALUES (1, 'M8888', '智店数码旗舰店', '你叫「小智」，是【智店数码旗舰店】的官方金牌AI客服。回答风格幽默风趣、专业热情。核心服务守则：\n1. 本店所有数码产品享受7天无理由退货、1年全国联保；\n2. 开封且已激活的数码产品如无硬件质量问题不支持无理由退货；\n3. 发货默认顺丰速运，每天16点前付款的订单当日发出；\n4. 当顾客询问退换货或物流时，依据守则耐心解答。', 1);

INSERT INTO t_product (id, merchant_id, product_code, product_name, category, price, stock_count, description)
VALUES 
(1, 1, 'SKU-KB-01', '极客三模机械键盘-客制化轴体', '外设', 399.00, 100, '支持蓝牙5.0/2.4G/有线三模连接，RGB背光，热插拔轴体，续航可达40小时。'),
(2, 1, 'SKU-EP-02', '降噪无线入耳式耳机 Pro', '音频', 299.00, 50, '45dB深度主动混合降噪，空间音频算法，支持通透模式，配合充电盒超长续航36小时。'),
(3, 1, 'SKU-PB-03', '磁吸无线快充移动电源10000mAh', '配件', 129.00, 200, '强劲磁吸，支持20W快充与15W无线充，轻薄便携，可直接携带上飞机。');

INSERT INTO t_order (id, order_no, merchant_id, user_id, product_id, product_name, quantity, total_amount, order_status, express_no)
VALUES
(1, 'ORD-20261015-001', 1, 1001, 1, '极客三模机械键盘-客制化轴体', 1, 399.00, 2, 'SF10086952701'),
(2, 'ORD-20261015-002', 1, 1001, 2, '降噪无线入耳式耳机 Pro', 1, 299.00, 1, NULL);
