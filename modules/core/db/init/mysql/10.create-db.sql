-- begin IMAP_MESSAGE_SYNC
create table IMAP_MESSAGE_SYNC (
    ID varchar(32),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    VERSION integer not null,
    --
    MESSAGE_ID varchar(32) not null,
    FLAGS longtext,
    FOLDER_ID varchar(32) not null,
    STATUS varchar(50) not null,
    OLD_FOLDER_ID varchar(32),
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE_SYNC
-- begin IMAP_MAIL_BOX
create table IMAP_MAIL_BOX (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    HOST varchar(255) not null,
    PORT integer not null,
    SECURE_MODE varchar(50),
    ROOT_CERTIFICATE_ID varchar(32),
    CLIENT_CERTIFICATE_ID varchar(32),
    AUTHENTICATION_METHOD varchar(50) not null,
    AUTHENTICATION_ID varchar(32),
    PROXY_ID varchar(32),
    CUBA_FLAG varchar(255),
    TRASH_FOLDER_NAME varchar(255),
    EVENTS_GENERATOR_CLASS varchar(255),
    FLAGS_SUPPORTED boolean not null,
    --
    primary key (ID)
)^
-- end IMAP_MAIL_BOX
-- begin IMAP_PROXY
create table IMAP_PROXY (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    HOST varchar(255),
    PORT integer,
    WEB_PROXY boolean,
    --
    primary key (ID)
)^
-- end IMAP_PROXY
-- begin IMAP_FOLDER_EVENT
create table IMAP_FOLDER_EVENT (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    FOLDER_ID varchar(32) not null,
    EVENT varchar(50) not null,
    --
    primary key (ID)
)^
-- end IMAP_FOLDER_EVENT
-- begin IMAP_FOLDER
create table IMAP_FOLDER (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    MAIL_BOX_ID varchar(32) not null,
    SELECTED boolean not null,
    SELECTABLE boolean not null,
    DISABLED boolean,
    PARENT_FOLDER_ID varchar(32),
    --
    primary key (ID)
)^
-- end IMAP_FOLDER
-- begin IMAP_MESSAGE
create table IMAP_MESSAGE (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    FOLDER_ID varchar(32),
    FLAGS longtext,
    IS_ATL boolean not null,
    MSG_UID bigint not null,
    MSG_NUM integer not null,
    THREAD_ID bigint,
    REFERENCE_ID longtext,
    MESSAGE_ID longtext,
    CAPTION longtext not null,
    RECEIVED_DATE datetime(3),
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE
-- begin IMAP_EVENT_HANDLER
create table IMAP_EVENT_HANDLER (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    EVENT_ID varchar(32) not null,
    HANDLING_ORDER integer,
    BEAN_NAME varchar(255) not null,
    METHOD_NAME varchar(255) not null,
    --
    primary key (ID)
)^
-- end IMAP_EVENT_HANDLER
-- begin IMAP_MESSAGE_ATTACHMENT
create table IMAP_MESSAGE_ATTACHMENT (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    IMAP_MESSAGE_ID varchar(32) not null,
    CREATED_TS time(3) not null,
    ORDER_NUMBER integer not null,
    NAME varchar(255) not null,
    FILE_SIZE bigint not null,
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE_ATTACHMENT
-- begin IMAP_SIMPLE_AUTHENTICATION
create table IMAP_SIMPLE_AUTHENTICATION (
    ID varchar(32),
    VERSION integer not null,
    CREATE_TS datetime(3),
    CREATED_BY varchar(50),
    UPDATE_TS datetime(3),
    UPDATED_BY varchar(50),
    DELETE_TS datetime(3),
    DELETED_BY varchar(50),
    --
    USERNAME varchar(255) not null,
    PASSWORD varchar(255) not null,
    --
    primary key (ID)
)^
-- end IMAP_SIMPLE_AUTHENTICATION
