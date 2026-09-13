CREATE TABLE ofTosDocument (
    version       VARCHAR2(191)  NOT NULL,
    status        VARCHAR2(20)   NOT NULL,
    body          CLOB           NOT NULL,
    createdBy     VARCHAR2(191)  NOT NULL,
    createdAt     TIMESTAMP      NOT NULL,
    updatedBy     VARCHAR2(191)  NOT NULL,
    updatedAt     TIMESTAMP      NOT NULL,
    activatedAt   TIMESTAMP      NULL,
    replacedAt    TIMESTAMP      NULL,
    CONSTRAINT ofTosDocument_pk PRIMARY KEY (version)
);

CREATE TABLE ofTosAcceptance (
    username      VARCHAR2(191)  NOT NULL,
    version       VARCHAR2(191)  NOT NULL,
    mechanism     VARCHAR2(40)   NOT NULL,
    acceptedAt    TIMESTAMP      NOT NULL,
    CONSTRAINT ofTosAcceptance_pk PRIMARY KEY (username, version),
    CONSTRAINT ofTosAcceptance_fk FOREIGN KEY (version) REFERENCES ofTosDocument (version)
);

CREATE INDEX ofTosAccept_uname_idx ON ofTosAcceptance (username);

INSERT INTO ofVersion (name, version) VALUES ('termsofservice', 0);

