-- begin IMAP_MESSAGE_SYNC
create table IMAP_MESSAGE_SYNC (
    ID uniqueidentifier,
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    VERSION integer not null,
    --
    MESSAGE_ID uniqueidentifier not null,
    FLAGS varchar(max),
    FOLDER_ID uniqueidentifier not null,
    STATUS varchar(50) not null,
    OLD_FOLDER_ID uniqueidentifier,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_MESSAGE_SYNC
-- begin IMAP_MAIL_BOX
create table IMAP_MAIL_BOX (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    HOST varchar(255) not null,
    PORT integer not null,
    SECURE_MODE varchar(50),
    ROOT_CERTIFICATE_ID uniqueidentifier,
    CLIENT_CERTIFICATE_ID uniqueidentifier,
    AUTHENTICATION_METHOD varchar(50) not null,
    AUTHENTICATION_ID uniqueidentifier,
    PROXY_ID uniqueidentifier,
    CUBA_FLAG varchar(255),
    TRASH_FOLDER_NAME varchar(255),
    EVENTS_GENERATOR_CLASS varchar(255),
    FLAGS_SUPPORTED tinyint not null,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_MAIL_BOX
-- begin IMAP_PROXY
create table IMAP_PROXY (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    HOST varchar(255),
    PORT integer,
    WEB_PROXY tinyint,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_PROXY
-- begin IMAP_FOLDER_EVENT
create table IMAP_FOLDER_EVENT (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    FOLDER_ID uniqueidentifier not null,
    EVENT varchar(50) not null,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_FOLDER_EVENT
-- begin IMAP_FOLDER
create table IMAP_FOLDER (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    MAIL_BOX_ID uniqueidentifier not null,
    SELECTED tinyint not null,
    SELECTABLE tinyint not null,
    DISABLED tinyint,
    PARENT_FOLDER_ID uniqueidentifier,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_FOLDER
-- begin IMAP_MESSAGE
create table IMAP_MESSAGE (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    FOLDER_ID uniqueidentifier,
    FLAGS varchar(max),
    IS_ATL tinyint not null,
    MSG_UID bigint not null,
    MSG_NUM integer not null,
    THREAD_ID bigint,
    REFERENCE_ID varchar(max),
    MESSAGE_ID varchar(max),
    CAPTION varchar(max) not null,
    RECEIVED_DATE datetime2,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_MESSAGE
-- begin IMAP_EVENT_HANDLER
create table IMAP_EVENT_HANDLER (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    EVENT_ID uniqueidentifier not null,
    HANDLING_ORDER integer,
    BEAN_NAME varchar(255) not null,
    METHOD_NAME varchar(255) not null,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_EVENT_HANDLER
-- begin IMAP_MESSAGE_ATTACHMENT
create table IMAP_MESSAGE_ATTACHMENT (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    IMAP_MESSAGE_ID uniqueidentifier not null,
    CREATED_TS datetime2 not null,
    ORDER_NUMBER integer not null,
    NAME varchar(255) not null,
    FILE_SIZE bigint not null,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_MESSAGE_ATTACHMENT
-- begin IMAP_SIMPLE_AUTHENTICATION
create table IMAP_SIMPLE_AUTHENTICATION (
    ID uniqueidentifier,
    VERSION integer not null,
    CREATE_TS datetime2,
    CREATED_BY varchar(50),
    UPDATE_TS datetime2,
    UPDATED_BY varchar(50),
    DELETE_TS datetime2,
    DELETED_BY varchar(50),
    --
    USERNAME varchar(255) not null,
    PASSWORD varchar(255) not null,
    --
    primary key nonclustered (ID)
)^
-- end IMAP_SIMPLE_AUTHENTICATION
