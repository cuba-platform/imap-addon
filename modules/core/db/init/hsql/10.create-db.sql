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
-- begin MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK
create table MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK (
    MAIL_FOLDER_ID varchar(36) not null,
    MAIL_EVENT_TYPE_ID varchar(36) not null,
    primary key (MAIL_FOLDER_ID, MAIL_EVENT_TYPE_ID)
)^
-- end MAILCOMPONENT_MAIL_FOLDER_MAIL_EVENT_TYPE_LINK
-- begin MAILCOMPONENT_MAIL_MESSAGE
create table MAILCOMPONENT_MAIL_MESSAGE (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    SEEN boolean,
    FROM_ varchar(255),
    TO_LIST varchar(255),
    CC_LIST varchar(255),
    BCC_LIST varchar(255),
    SUBJECT varchar(255),
    FLAGS_LIST varchar(255),
    DATE_ timestamp,
    SEEN_TIME timestamp,
    MESSAGE_UID bigint not null,
    FOLDER_NAME varchar(255) not null,
    MAIL_BOX_ID varchar(36),
    --
    primary key (ID)
)^
-- end MAILCOMPONENT_MAIL_MESSAGE
