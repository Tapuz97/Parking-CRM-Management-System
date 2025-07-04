-- =========================
-- Only use with active orders that were not made in advanced!
-- i,e deposite vehicle without order number
-- =========================
-- Replace order_number =  
-- with your target order number


SET SQL_SAFE_UPDATES = 0;
UPDATE orders
SET order_time = DATE_SUB(order_time, INTERVAL 4 HOUR)
WHERE order_number = 71;

UPDATE parking_history
SET parking_time = DATE_SUB(parking_time, INTERVAL 4 HOUR)
WHERE order_number = 71;
SET SQL_SAFE_UPDATES = 1;
