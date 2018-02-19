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
    SECURE_MODE varchar(50),
    ROOT_CERTIFICATE_ID varchar(36),
    CLIENT_CERTIFICATE_ID varchar(36),
    AUTHENTICATION_METHOD varchar(50) not null,
    AUTHENTICATION_ID varchar(36),
    POLL_INTERVAL integer not null,
    PROCESSING_TIMEOUT integer,
    CUBA_FLAG varchar(255),
    TRASH_FOLDER_NAME varchar(255),
    UPDATE_SLICE_SIZE integer not null,
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
-- begin MAILCOMPONENT_MAIL_EVENT_TYPE
create table MAILCOMPONENT_MAIL_EVENT_TYPE (
    ID varchar(36) not null,
    --
    EVENT_TYPE varchar(50) not null,
    --
    primary key (ID)
)^
-- end MAILCOMPONENT_MAIL_EVENT_TYPE
-- begin MAILCOMPONENT_MAIL_MESSAGE_META
create table MAILCOMPONENT_MAIL_MESSAGE_META (
    ID varchar(36) not null,
    --
    MAIL_BOX_ID varchar(36) not null,
    MSG_UID bigint not null,
    FOLDER_NAME varchar(255) not null,
    IS_DELETED boolean not null,
    IS_FLAGGED boolean not null,
    IS_ANSWERED boolean not null,
    IS_SEEN boolean not null,
    UPDATED_TS time not null,
    --
    primary key (ID)
)^
-- end MAILCOMPONENT_MAIL_MESSAGE_META
-- begin MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK
create table MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK (
    MAIL_FOLDER_ID varchar(36) not null,
    MAIL_EVENT_TYPE_ID varchar(36) not null,
    primary key (MAIL_FOLDER_ID, MAIL_EVENT_TYPE_ID)
)^
-- end MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK
