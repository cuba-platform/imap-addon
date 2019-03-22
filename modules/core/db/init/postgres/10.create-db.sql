-- begin IMAP_FOLDER
create table IMAP_FOLDER (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    MAIL_BOX_ID uuid not null,
    SELECTED boolean not null,
    SELECTABLE boolean not null,
    DISABLED boolean,
    PARENT_FOLDER_ID uuid,
    --
    primary key (ID)
)^
-- end IMAP_FOLDER
-- begin IMAP_MAIL_BOX
create table IMAP_MAIL_BOX (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    NAME varchar(255) not null,
    HOST varchar(255) not null,
    PORT integer not null,
    SECURE_MODE varchar(50),
    ROOT_CERTIFICATE_ID uuid,
    CLIENT_CERTIFICATE_ID uuid,
    AUTHENTICATION_METHOD varchar(50) not null,
    AUTHENTICATION_ID uuid,
    PROXY_ID uuid,
    CUBA_FLAG varchar(255),
    TRASH_FOLDER_NAME varchar(255),
    EVENTS_GENERATOR_CLASS varchar(255),
    FLAGS_SUPPORTED boolean not null,
    --
    primary key (ID)
)^
-- end IMAP_MAIL_BOX
-- begin IMAP_SIMPLE_AUTHENTICATION
create table IMAP_SIMPLE_AUTHENTICATION (
    ID uuid,
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
-- end IMAP_SIMPLE_AUTHENTICATION
-- begin IMAP_MESSAGE
create table IMAP_MESSAGE (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    FOLDER_ID uuid,
    FLAGS text,
    IS_ATL boolean not null,
    MSG_UID bigint not null,
    MSG_NUM integer not null,
    THREAD_ID bigint,
    REFERENCE_ID text,
    MESSAGE_ID text,
    CAPTION text not null,
    RECEIVED_DATE timestamp,
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE
-- begin IMAP_FOLDER_EVENT
create table IMAP_FOLDER_EVENT (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    FOLDER_ID uuid not null,
    EVENT varchar(50) not null,
    --
    primary key (ID)
)^
-- end IMAP_FOLDER_EVENT
-- begin IMAP_EVENT_HANDLER
create table IMAP_EVENT_HANDLER (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    EVENT_ID uuid not null,
    HANDLING_ORDER integer,
    BEAN_NAME varchar(255) not null,
    METHOD_NAME varchar(255) not null,
    --
    primary key (ID)
)^
-- end IMAP_EVENT_HANDLER
-- begin IMAP_MESSAGE_ATTACHMENT
create table IMAP_MESSAGE_ATTACHMENT (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    IMAP_MESSAGE_ID uuid not null,
    CREATED_TS time not null,
    ORDER_NUMBER integer not null,
    NAME varchar(255) not null,
    FILE_SIZE bigint not null,
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE_ATTACHMENT
-- begin IMAP_PROXY
create table IMAP_PROXY (
    ID uuid,
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
-- end IMAP_PROXY
-- begin IMAP_MESSAGE_SYNC
create table IMAP_MESSAGE_SYNC (
    ID uuid,
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    VERSION integer not null,
    --
    MESSAGE_ID uuid not null,
    FLAGS text,
    FOLDER_ID uuid not null,
    STATUS varchar(50) not null,
    NEW_FOLDER_ID uuid,
    NEW_FOLDER_NAME varchar(255),
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE_SYNC
