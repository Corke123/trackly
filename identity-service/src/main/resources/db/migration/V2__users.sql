CREATE TABLE users
(
    username VARCHAR(200) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  BOOLEAN      NOT NULL
);

CREATE TABLE authorities
(
    username  VARCHAR(200) NOT NULL,
    authority VARCHAR(50)  NOT NULL,
    CONSTRAINT fk_authorities_user FOREIGN KEY (username) REFERENCES users (username),
    CONSTRAINT username_authority UNIQUE (username, authority)
);

INSERT INTO users (username, password, enabled)
VALUES ('admin', '{bcrypt}$2a$10$tiJjsXtwenC/VzR/artQOeHwpTuicnAWYi.3M9g7ME6cH8MxtUovK', true);
INSERT INTO users (username, password, enabled)
VALUES ('user', '{bcrypt}$2a$10$K4nPRbjfGTPLmrRu07PMT.h6TWPJvSGuZRUzATYyDHJ1JZBsRrNoi', true);
INSERT INTO users (username, password, enabled)
VALUES ('demo', '{bcrypt}$2a$10$Je/aju84w67.65ndBZWsKuhY0vK.bUFSu8zlAxKHh0tBLJNYQJcgW', true);

INSERT INTO authorities (username, authority)
VALUES ('admin', 'ROLE_ADMIN');
INSERT INTO authorities (username, authority)
VALUES ('user', 'ROLE_USER');
INSERT INTO authorities (username, authority)
VALUES ('demo', 'ROLE_USER');
