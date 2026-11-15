-- ============================================================================
-- 苍穹外卖 · 报表优化基准数据脚本（改造③-c）
-- 用途：为报表/统计类 SQL 的 EXPLAIN 前后对比准备 10w 级数据
--       （orders 10w + order_detail 20w + user 500）
-- 说明：数据只落在本地容器的 MySQL 数据卷中，不随 doc/sky.sql 初始化；
--       docker compose down -v 重建环境后，重跑本脚本即可恢复基准数据
-- 运行：docker exec -i sky-mysql mysql -uroot -psky123456 --default-character-set=utf8mb4 \
--           sky_take_out < doc/benchmark-data.sql
-- ============================================================================

USE sky_take_out;

DROP PROCEDURE IF EXISTS gen_benchmark_data;

DELIMITER $$

CREATE PROCEDURE gen_benchmark_data()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE d INT DEFAULT 0;
    DECLARE oid BIGINT;
    DECLARE ot DATETIME;
    DECLARE st INT;

    START TRANSACTION;

    -- 500 个用户，create_time 随机分布近 30 天（用户统计 SQL 的数据源）
    WHILE i < 500 DO
        INSERT INTO `user` (openid, name, phone, sex, id_number, avatar, create_time)
        VALUES (CONCAT('benchmark-openid-', LPAD(i, 6, '0')),
                CONCAT('压测用户', i),
                CONCAT('138', LPAD(FLOOR(RAND() * 100000000), 8, '0')),
                ELT(1 + FLOOR(RAND() * 2), '1', '2'),
                NULL,
                '',
                NOW() - INTERVAL FLOOR(RAND() * 30) DAY
                        - INTERVAL FLOOR(RAND() * 86400) SECOND);
        SET i = i + 1;
    END WHILE;

    -- 10 万笔订单（近 30 天随机分布；状态加权：60% 已完成，其余均匀 1/2/3/4/6）
    SET i = 0;
    WHILE i < 100000 DO
        SET ot = NOW() - INTERVAL FLOOR(RAND() * 30) DAY
                       - INTERVAL FLOOR(RAND() * 86400) SECOND;
        SET st = IF(RAND() < 0.6, 5, ELT(1 + FLOOR(RAND() * 5), '1', '2', '3', '4', '6'));

        INSERT INTO orders (number, status, user_id, address_book_id,
                            order_time, checkout_time,
                            pay_method, pay_status, amount,
                            phone, address, user_name, consignee,
                            estimated_delivery_time, delivery_status,
                            pack_amount, tableware_number, tableware_status)
        VALUES (CONCAT('BM', DATE_FORMAT(ot, '%Y%m%d%H%i%s'), LPAD(i, 6, '0')),
                st,
                1 + FLOOR(RAND() * 100),
                1 + FLOOR(RAND() * 100),
                ot,
                IF(st >= 2, ot + INTERVAL FLOOR(RAND() * 600) SECOND, NULL),
                1,
                IF(st >= 2, 1, 0),
                20 + ROUND(RAND() * 180, 2),
                CONCAT('139', LPAD(FLOOR(RAND() * 100000000), 8, '0')),
                '基准测试地址',
                CONCAT('压测用户', 1 + FLOOR(RAND() * 100)),
                CONCAT('压测用户', 1 + FLOOR(RAND() * 100)),
                ot + INTERVAL 1 HOUR,
                1,
                2,
                2,
                1);

        SET oid = LAST_INSERT_ID();

        -- 每单 2 条明细（20 万条；name 从 20 个菜品名池随机取，供 getSalesTop10 分组统计）
        SET d = 0;
        WHILE d < 2 DO
            INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id,
                                      dish_flavor, number, amount)
            VALUES (ELT(1 + FLOOR(RAND() * 20),
                        '宫保鸡丁', '鱼香肉丝', '水煮鱼', '剁椒鱼头', '西红柿炒鸡蛋',
                        '红烧肉', '地三鲜', '酸辣土豆丝', '可乐鸡翅', '麻婆豆腐',
                        '糖醋里脊', '回锅肉', '北京烤鸭', '小炒黄牛肉', '蒜蓉虾',
                        '干煸四季豆', '皮蛋瘦肉粥', '扬州炒饭', '葱油拌面', '清凉小菜'),
                    '',
                    oid,
                    NULL, NULL, NULL,
                    1 + FLOOR(RAND() * 3),
                    10 + ROUND(RAND() * 40, 2));
            SET d = d + 1;
        END WHILE;

        SET i = i + 1;
    END WHILE;

    COMMIT;
END$$

DELIMITER ;

CALL gen_benchmark_data();
DROP PROCEDURE IF EXISTS gen_benchmark_data;

-- 造数结果自检
SELECT 'orders' AS tbl, COUNT(*) AS cnt FROM orders
UNION ALL SELECT 'order_detail', COUNT(*) FROM order_detail
UNION ALL SELECT 'user', COUNT(*) FROM `user`;
