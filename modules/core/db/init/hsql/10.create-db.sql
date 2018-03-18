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
    POLL_INTERVAL integer not null,
    PROCESSING_TIMEOUT integer,
    CUBA_FLAG varchar(255),
    TRASH_FOLDER_NAME varchar(255),
    UPDATE_SLICE_SIZE integer not null,
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
-- begin IMAPCOMPONENT_IMAP_MESSAGE_REF
create table IMAPCOMPONENT_IMAP_MESSAGE_REF (
    ID varchar(36) not null,
    --
    FOLDER_ID varchar(36),
    IS_ATL boolean not null,
    MSG_UID bigint not null,
    THREAD_ID bigint,
    REFERENCE_ID varchar(255),
    CAPTION varchar(255) not null,
    IS_DELETED boolean not null,
    IS_FLAGGED boolean not null,
    IS_ANSWERED boolean not null,
    IS_SEEN boolean not null,
    UPDATED_TS time not null,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_MESSAGE_REF
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
    BEAN_NAME varchar(255),
    METHOD_NAME varchar(255),
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_FOLDER_EVENT
-- begin IMAPCOMPONENT_IMAP_MESSAGE_ATTACHMENT_REF
create table IMAPCOMPONENT_IMAP_MESSAGE_ATTACHMENT_REF (
    ID varchar(36) not null,
    --
    IMAP_MESSAGE_REF_ID varchar(36) not null,
    CREATED_TS time not null,
    ORDER_NUMBER integer not null,
    NAME varchar(255) not null,
    FILE_SIZE bigint not null,
    --
    primary key (ID)
)^
-- end IMAPCOMPONENT_IMAP_MESSAGE_ATTACHMENT_REF
