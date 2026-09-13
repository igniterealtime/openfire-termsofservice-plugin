CREATE TABLE ofTosDocument (
    version       NVARCHAR(191)  NOT NULL,
    status        NVARCHAR(20)   NOT NULL,
    body          NVARCHAR(MAX)  NOT NULL,
    createdBy     NVARCHAR(191)  NOT NULL,
    createdAt     DATETIME2      NOT NULL,
    updatedBy     NVARCHAR(191)  NOT NULL,
    updatedAt     DATETIME2      NOT NULL,
    activatedAt   DATETIME2      NULL,
    replacedAt    DATETIME2      NULL,
    CONSTRAINT ofTosDocument_pk PRIMARY KEY (version)
);

CREATE TABLE ofTosAcceptance (
    username      NVARCHAR(191)  NOT NULL,
    version       NVARCHAR(191)  NOT NULL,
    mechanism     NVARCHAR(40)   NOT NULL,
    acceptedAt    DATETIME2      NOT NULL,
    CONSTRAINT ofTosAcceptance_pk PRIMARY KEY (username, version),
    CONSTRAINT ofTosAcceptance_fk FOREIGN KEY (version) REFERENCES ofTosDocument (version)
);

CREATE INDEX ofTosAccept_uname_idx ON ofTosAcceptance (username);

INSERT INTO ofVersion (name, version) VALUES ('termsofservice', 0);

