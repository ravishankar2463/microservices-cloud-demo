INSERT INTO products (sku, name, description, category, price, active, created_at, updated_at)
VALUES 
('SKU-LAPTOP-001', 'MacBook Pro M3', 'High-performance laptop for developers', 'ELECTRONICS', 1999.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SKU-PHONE-002', 'iPhone 15 Pro', 'Flagship smartphone with titanium design', 'ELECTRONICS', 999.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SKU-HEADPHONE-003', 'Sony WH-1000XM5', 'Noise cancelling wireless headphones', 'ACCESSORIES', 349.99, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SKU-KEYBOARD-004', 'Mechanical Keyboard', 'Wireless mechanical keyboard with RGB', 'ACCESSORIES', 129.50, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (sku) DO NOTHING;
