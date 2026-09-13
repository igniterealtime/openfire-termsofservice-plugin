CREATE TABLE ofTosDocument (
    version       VARCHAR(191)          NOT NULL,
    status        VARCHAR(20)           NOT NULL,
    body          BLOB SUB_TYPE TEXT    NOT NULL,
    createdBy     VARCHAR(191)          NOT NULL,
    createdAt     TIMESTAMP             NOT NULL,
    updatedBy     VARCHAR(191)          NOT NULL,
    updatedAt     TIMESTAMP             NOT NULL,
    activatedAt   TIMESTAMP,
    replacedAt    TIMESTAMP,
    CONSTRAINT ofTosDocument_pk PRIMARY KEY (version)
);

CREATE TABLE ofTosAcceptance (
    username      VARCHAR(191)   NOT NULL,
    version       VARCHAR(191)   NOT NULL,
    mechanism     VARCHAR(40)    NOT NULL,
    acceptedAt    TIMESTAMP      NOT NULL,
    CONSTRAINT ofTosAcceptance_pk PRIMARY KEY (username, version),
    CONSTRAINT ofTosAcceptance_fk FOREIGN KEY (version) REFERENCES ofTosDocument (version)
);

CREATE INDEX ofTosAccept_uname_idx ON ofTosAcceptance (username);

INSERT INTO ofVersion (name, version) VALUES ('termsofservice', 0);

