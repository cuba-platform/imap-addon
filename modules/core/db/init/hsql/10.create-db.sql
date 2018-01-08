-- begin MAILCOMPONENT_MAIL_FOLDER
create table MAILCOMPONENT_MAIL_FOLDER (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    LISTEN_NEW_EMAIL boolean not null,
    LISTEN_EMAIL_SEEN boolean not null,
    LISTEN_NEW_ANSWER boolean not null,
    LISTEN_EMAIL_MOVE boolean not null,
    LISTEN_FLAGS_UPDATE boolean not null,
    LISTEN_EMAIL_REMOVE boolean not null,
    LISTEN_NEW_THREAD boolean not null,
    MAIL_BOX_ID varchar(36) not null,
    --
    primary key (ID)
)^
-- end MAILCOMPONENT_MAIL_FOLDER
-- begin MAILCOMPONENT_MAIL_BOX
create table MAILCOMPONENT_MAIL_BOX (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    HOST varchar(255) not null,
    PORT integer not null,
    SECURE_MODE integer,
    ROOT_CERTIFICATE_ID varchar(36),
    CLIENT_CERTIFICATE_ID varchar(36),
    AUTHENTICATION_METHOD integer not null,
    AUTHENTICATION_ID varchar(36),
    POLL_INTERVAL integer not null,
    --
    primary key (ID)
)^
-- end MAILCOMPONENT_MAIL_BOX
-- begin MAILCOMPONENT_MAIL_SIMPLE_AUTHENTICATION
create table MAILCOMPONENT_MAIL_SIMPLE_AUTHENTICATION (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    USERNAME varchar(255) not null,
    PASSWORD varchar(255) not null,
    --
    primary key (ID)
)^
-- end MAILCOMPONENT_MAIL_SIMPLE_AUTHENTICATION
