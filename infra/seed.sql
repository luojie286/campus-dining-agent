-- 园区餐饮学习测试数据，2026-10-12。只供本项目本地学习库使用。
-- 5个分类、8个菜品、3个套餐、8条套餐组成、1条营业设置。
-- 不新增/修改账号，不写地址、购物车、订单或Agent记录；不更改表结构。
-- 不指定自增ID，关联按唯一名称查找，数据库ID与前端商品ID转换见设计文档。
-- 同名记录跳过，不覆写价格、库存、状态；只为本次新建套餐插入组成。
-- 事务内执行；任一SQL错误应停止并ROLLBACK，不能继续COMMIT。
-- 可在数据库客户端中运行，目标应为campus_dining；不要同时运行两份脚本。
-- 标签、描述、库存均为虚构学习数据，不是实际营业信息。
SET NAMES utf8mb4;
USE `campus_dining`;
START TRANSACTION;
SET @seed_operator_id = (SELECT MIN(id) FROM employee WHERE status = 1);

-- 一、分类
INSERT INTO category (type,name,sort,status,create_user,update_user)
SELECT 1,'荤食主菜',1,1,@seed_operator_id,@seed_operator_id
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='荤食主菜');

INSERT INTO category (type,name,sort,status,create_user,update_user)
SELECT 1,'轻食时蔬',2,1,@seed_operator_id,@seed_operator_id
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='轻食时蔬');

INSERT INTO category (type,name,sort,status,create_user,update_user)
SELECT 1,'主食汤品',3,1,@seed_operator_id,@seed_operator_id
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='主食汤品');

INSERT INTO category (type,name,sort,status,create_user,update_user)
SELECT 2,'人气套餐',4,1,@seed_operator_id,@seed_operator_id
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='人气套餐');

INSERT INTO category (type,name,sort,status,create_user,update_user)
SELECT 2,'轻盈套餐',5,1,@seed_operator_id,@seed_operator_id
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='轻盈套餐');

-- 二、菜品：默认提供起售样本，另保留一个停售且零库存样本。
INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '香煎鸡腿排',c.id,18.00,'鸡腿排，午餐主菜。',1,45,'["不辣"]','chicken',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='荤食主菜' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='香煎鸡腿排');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '清蒸鲜鱼',c.id,22.00,'清蒸鱼，葱姜提味。',1,28,'["不辣","清淡"]','fish',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='荤食主菜' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='清蒸鲜鱼');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '黑椒牛肉',c.id,24.00,'牛肉搭配彩椒和黑椒。',1,30,'["微辣"]','beef',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='荤食主菜' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='黑椒牛肉');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '田园蔬菜沙拉',c.id,12.00,'生菜、玉米、番茄搭配油醋汁。',1,55,'["不辣","素食","清淡"]','salad',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='轻食时蔬' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='田园蔬菜沙拉');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '番茄鸡蛋面',c.id,16.00,'番茄汤底搭配鸡蛋与面条。',1,40,'["不辣"]','noodle',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='主食汤品' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='番茄鸡蛋面');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '麻婆豆腐',c.id,14.00,'豆腐和肉末，测试停售及零库存展示。',0,0,'["辣"]','tofu',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='荤食主菜' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='麻婆豆腐');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '米饭',c.id,2.00,'一份白米饭。',1,150,'["不辣","素食"]','noodle',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='主食汤品' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='米饭');

INSERT INTO dish (name,category_id,price,description,status,stock,tags,art,create_user,update_user)
SELECT '紫菜蛋花汤',c.id,4.00,'紫菜、鸡蛋制作的汤品。',1,100,'["不辣","清淡"]','tofu',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='主食汤品' AND c.type=1
AND NOT EXISTS (SELECT 1 FROM dish WHERE name='紫菜蛋花汤');

-- 三、套餐：只有本次新建的套餐才添加组成，避免重复执行修改已有套餐。
SET @seed_new_meal_1 = NOT EXISTS (SELECT 1 FROM setmeal WHERE name='元气鸡腿套餐');
INSERT INTO setmeal (category_id,name,price,description,status,stock,tags,art,create_user,update_user)
SELECT c.id,'元气鸡腿套餐',24.00,'香煎鸡腿排 + 米饭 + 紫菜蛋花汤。',1,20,'["不辣"]','chicken',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='人气套餐' AND c.type=2 AND @seed_new_meal_1=1;
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='香煎鸡腿排'
WHERE s.name='元气鸡腿套餐' AND @seed_new_meal_1=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='米饭'
WHERE s.name='元气鸡腿套餐' AND @seed_new_meal_1=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='紫菜蛋花汤'
WHERE s.name='元气鸡腿套餐' AND @seed_new_meal_1=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);

SET @seed_new_meal_2 = NOT EXISTS (SELECT 1 FROM setmeal WHERE name='清蒸鲜鱼套餐');
INSERT INTO setmeal (category_id,name,price,description,status,stock,tags,art,create_user,update_user)
SELECT c.id,'清蒸鲜鱼套餐',27.00,'清蒸鲜鱼 + 米饭 + 紫菜蛋花汤。',1,15,'["不辣","清淡"]','fish',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='人气套餐' AND c.type=2 AND @seed_new_meal_2=1;
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='清蒸鲜鱼'
WHERE s.name='清蒸鲜鱼套餐' AND @seed_new_meal_2=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='米饭'
WHERE s.name='清蒸鲜鱼套餐' AND @seed_new_meal_2=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='紫菜蛋花汤'
WHERE s.name='清蒸鲜鱼套餐' AND @seed_new_meal_2=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);

SET @seed_new_meal_3 = NOT EXISTS (SELECT 1 FROM setmeal WHERE name='轻盈田园套餐');
INSERT INTO setmeal (category_id,name,price,description,status,stock,tags,art,create_user,update_user)
SELECT c.id,'轻盈田园套餐',25.00,'田园蔬菜沙拉 + 番茄鸡蛋面。',1,20,'["不辣"]','salad',@seed_operator_id,@seed_operator_id
FROM category c WHERE c.name='轻盈套餐' AND c.type=2 AND @seed_new_meal_3=1;
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='田园蔬菜沙拉'
WHERE s.name='轻盈田园套餐' AND @seed_new_meal_3=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);
INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
SELECT s.id,d.id,d.name,d.price,1 FROM setmeal s JOIN dish d ON d.name='番茄鸡蛋面'
WHERE s.name='轻盈田园套餐' AND @seed_new_meal_3=1
AND NOT EXISTS (SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id AND x.dish_id=d.id);

-- 四、营业设置：仅缺少id=1时创建；已有配置不修改。
INSERT INTO shop (id,name,status,opening_hours,pickup_point)
SELECT 1,'园区食堂',1,'10:30 — 13:30 · 17:00 — 19:00','园区食堂一楼自提点'
WHERE NOT EXISTS (SELECT 1 FROM shop WHERE id=1);

COMMIT;

-- 校验数量；重复执行不会重复增加已有名称的数据。
SELECT 'category' AS table_name,COUNT(*) AS row_count FROM category
UNION ALL SELECT 'dish',COUNT(*) FROM dish
UNION ALL SELECT 'setmeal',COUNT(*) FROM setmeal
UNION ALL SELECT 'setmeal_dish',COUNT(*) FROM setmeal_dish
UNION ALL SELECT 'shop',COUNT(*) FROM shop;
