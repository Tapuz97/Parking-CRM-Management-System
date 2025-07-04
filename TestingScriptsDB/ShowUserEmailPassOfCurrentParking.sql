SELECT DISTINCT
    s.subscriber_email,
    s.subscriber_password,
    o.confirmation_code
FROM 
    parking p
JOIN 
    orders o ON p.confirmation_code = o.confirmation_code
JOIN 
    subscribers s ON o.subscriber_id = s.subscriber_id
WHERE 
    p.status = 'occupied';
