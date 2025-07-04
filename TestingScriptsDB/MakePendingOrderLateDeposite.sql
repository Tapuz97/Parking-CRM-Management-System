-- =========================
-- Only use with pending orders that were made in advanced!
-- i,e did not deposit vehicle while order date and time passed
-- =========================
-- Replace order_number =  
-- with your target order number


SET SQL_SAFE_UPDATES = 0;
UPDATE orders
SET order_date = CURRENT_DATE,
    order_time = SUBTIME(CURRENT_TIME, '00:15:00'),
    order_status = 'pending'
WHERE order_number = 34;
SET SQL_SAFE_UPDATES = 1;
