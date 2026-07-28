-- V2__users.sql seeded 'user' with a bcrypt hash of 'password' instead of 'user',
-- inconsistent with the 'admin' and 'demo' rows (each hashed to their own username).
UPDATE users
SET password = '{bcrypt}$2a$10$ZSQmXosPwCOZfWOOQG.38Ox7pImjBqVrLVuJy3nL2CqMR8qVLANWq'
WHERE username = 'user';
