-- =====================================================
-- Демонстрационная БД для PgOptima
-- =====================================================

-- Таблица пользователей (50 000)
DROP TABLE IF EXISTS users CASCADE;
CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       email VARCHAR(255) NOT NULL,
                       full_name VARCHAR(255) NOT NULL,
                       registration_date DATE NOT NULL,
                       last_login TIMESTAMP,
                       is_active BOOLEAN DEFAULT true,
                       balance DECIMAL(10,2) DEFAULT 0,
                       city VARCHAR(50),
                       age INT
);

INSERT INTO users (email, full_name, registration_date, last_login, is_active, balance, city, age)
SELECT
    'user' || i || '@demo.com',
    'User ' || i,
    DATE '2018-01-01' + (random() * 2200)::int,
        NOW() - (random() * 180 * 86400)::int * interval '1 second',
        random() > 0.2,
    round((random() * 5000)::numeric, 2),
    (ARRAY['Moscow','SPb','Kazan','Novosibirsk','Ekaterinburg'])[floor(random()*5)+1],
    18 + floor(random()*50)::int
FROM generate_series(1, 50000) i;

-- Таблица товаров (10 000)
DROP TABLE IF EXISTS products CASCADE;
CREATE TABLE products (
                          id SERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          category VARCHAR(50),
                          price DECIMAL(10,2) NOT NULL,
                          stock INT DEFAULT 0
);

INSERT INTO products (name, category, price, stock)
SELECT
    'Product ' || i,
    (ARRAY['Electronics','Clothing','Books','Home','Sports'])[floor(random()*5)+1],
    round((random() * 300 + 10)::numeric, 2),
    floor(random() * 200)::int
FROM generate_series(1, 10000) i;

-- Таблица заказов (200 000)
DROP TABLE IF EXISTS orders CASCADE;
CREATE TABLE orders (
                        id SERIAL PRIMARY KEY,
                        user_id INT NOT NULL REFERENCES users(id),
                        order_date DATE NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        total_amount DECIMAL(10,2)
);

INSERT INTO orders (user_id, order_date, status, total_amount)
SELECT
    (random() * 49999 + 1)::int,
        DATE '2019-01-01' + (random() * 1800)::int,
        (ARRAY['pending','paid','shipped','delivered','cancelled'])[floor(random()*5)+1],
    round((random() * 1000 + 10)::numeric, 2)
FROM generate_series(1, 200000) i;

-- Таблица деталей заказов (500 000)
DROP TABLE IF EXISTS order_items CASCADE;
CREATE TABLE order_items (
                             id SERIAL PRIMARY KEY,
                             order_id INT NOT NULL REFERENCES orders(id),
                             product_id INT NOT NULL REFERENCES products(id),
                             quantity INT NOT NULL,
                             price_at_purchase DECIMAL(10,2) NOT NULL
);

INSERT INTO order_items (order_id, product_id, quantity, price_at_purchase)
SELECT
    (random() * 199999 + 1)::int,
        (random() * 9999 + 1)::int,
        1 + floor(random()*4)::int,
        round((random() * 200 + 10)::numeric, 2)
FROM generate_series(1, 500000) i;

-- Таблица логов (500 000) – для демонстрации большого сканирования
DROP TABLE IF EXISTS logs CASCADE;
CREATE TABLE logs (
                      id SERIAL PRIMARY KEY,
                      level VARCHAR(10),
                      message TEXT,
                      created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO logs (level, message)
SELECT
    (ARRAY['INFO','DEBUG','WARN','ERROR'])[floor(random()*4)+1],
    'Log entry ' || i || ' – some demo text for analysis.'
FROM generate_series(1, 500000) i;

-- Создаём внешний ключ на order_items.product_id (индекс не создаётся намеренно)
-- Индексы на user_id в orders и order_id в order_items тоже отсутствуют

-- Несколько проблемных запросов для проверки (выводятся в консоль)
DO $$
DECLARE
    query_text text;
BEGIN
    RAISE NOTICE 'Демо-база создана. Размеры: users=50k, products=10k, orders=200k, order_items=500k, logs=500k.';
RAISE NOTICE 'Проблемные запросы для анализа:';
RAISE NOTICE '1) SELECT * FROM users WHERE balance > 1000 AND city = ''Moscow'';';
RAISE NOTICE '2) SELECT o.id, o.order_date, COUNT(oi.id) FROM orders o JOIN order_items oi ON o.id = oi.order_id WHERE o.order_date > ''2023-01-01'' GROUP BY o.id, o.order_date;';
RAISE NOTICE '3) SELECT * FROM users ORDER BY registration_date DESC LIMIT 100;';
RAISE NOTICE '4) SELECT user_id, COUNT(*) FROM orders GROUP BY user_id ORDER BY COUNT(*) DESC;';
RAISE NOTICE '5) SELECT * FROM logs WHERE level = ''ERROR'' AND created_at > NOW() - INTERVAL ''7 days'';';
END $$;