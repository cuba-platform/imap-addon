-- begin IMAPCOMPONENT_IMAP_FOLDER
create table IMAPCOMPONENT_IMAP_FOLDER (
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
    SELECTED boolean not null,
    SELECTABLE boolean not null,
    DISABLED boolean,
    PARENT_FOLDER_ID varchar(36),
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_FOLDER
-- begin IMAPCOMPONENT_IMAP_MAIL_BOX
create table IMAPCOMPONENT_IMAP_MAIL_BOX (
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
    PROXY_ID varchar(36),
    CUBA_FLAG varchar(255),
    TRASH_FOLDER_NAME varchar(255),
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_MAIL_BOX
-- begin IMAPCOMPONENT_IMAP_SIMPLE_AUTHENTICATION
create table IMAPCOMPONENT_IMAP_SIMPLE_AUTHENTICATION (
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
-- end IMAPCOMPONENT_IMAP_SIMPLE_AUTHENTICATION
-- begin IMAPCOMPONENT_IMAP_MESSAGE
create table IMAPCOMPONENT_IMAP_MESSAGE (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    FOLDER_ID varchar(36),
    FLAGS longvarchar,
    IS_ATL boolean not null,
    MSG_UID bigint not null,
    MSG_NUM integer not null,
    THREAD_ID bigint,
    REFERENCE_ID varchar(255),
    MESSAGE_ID varchar(255),
    CAPTION varchar(255) not null,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_MESSAGE
-- begin IMAPCOMPONENT_IMAP_FOLDER_EVENT
create table IMAPCOMPONENT_IMAP_FOLDER_EVENT (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    FOLDER_ID varchar(36) not null,
    EVENT varchar(50) not null,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_FOLDER_EVENT
-- begin IMAPCOMPONENT_IMAP_EVENT_HANDLER
create table IMAPCOMPONENT_IMAP_EVENT_HANDLER (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    EVENT_ID varchar(36) not null,
    HANDLING_ORDER integer not null,
    BEAN_NAME varchar(255) not null,
    METHOD_NAME varchar(255) not null,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_EVENT_HANDLER
-- begin IMAPCOMPONENT_IMAP_MESSAGE_ATTACHMENT
create table IMAPCOMPONENT_IMAP_MESSAGE_ATTACHMENT (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    IMAP_MESSAGE_ID varchar(36) not null,
    CREATED_TS time not null,
    ORDER_NUMBER integer not null,
    NAME varchar(255) not null,
    FILE_SIZE bigint not null,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_MESSAGE_ATTACHMENT
-- begin IMAPCOMPONENT_IMAP_PROXY
create table IMAPCOMPONENT_IMAP_PROXY (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    HOST varchar(255),
    PORT integer,
    WEB_PROXY boolean,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_PROXY
