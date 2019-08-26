-- begin IMAP_MESSAGE_SYNC
create table IMAP_MESSAGE_SYNC (
    ID varchar2(32),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    VERSION number(10) not null,
    --
    MESSAGE_ID varchar2(32) not null,
    FLAGS clob,
    FOLDER_ID varchar2(32) not null,
    STATUS varchar2(50 char) not null,
    OLD_FOLDER_ID varchar2(32),
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE_SYNC
-- begin IMAP_MAIL_BOX
create table IMAP_MAIL_BOX (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    NAME varchar2(255 char) not null,
    HOST varchar2(255 char) not null,
    PORT number(10) not null,
    SECURE_MODE varchar2(50 char),
    ROOT_CERTIFICATE_ID varchar2(32),
    CLIENT_CERTIFICATE_ID varchar2(32),
    AUTHENTICATION_METHOD varchar2(50 char) not null,
    AUTHENTICATION_ID varchar2(32),
    PROXY_ID varchar2(32),
    CUBA_FLAG varchar2(255 char),
    TRASH_FOLDER_NAME varchar2(255 char),
    EVENTS_GENERATOR_CLASS varchar2(255 char),
    FLAGS_SUPPORTED char(1) not null,
    --
    primary key (ID)
)^
-- end IMAP_MAIL_BOX
-- begin IMAP_PROXY
create table IMAP_PROXY (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    HOST varchar2(255 char),
    PORT number(10),
    WEB_PROXY char(1),
    --
    primary key (ID)
)^
-- end IMAP_PROXY
-- begin IMAP_FOLDER_EVENT
create table IMAP_FOLDER_EVENT (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    FOLDER_ID varchar2(32) not null,
    EVENT varchar2(50 char) not null,
    --
    primary key (ID)
)^
-- end IMAP_FOLDER_EVENT
-- begin IMAP_FOLDER
create table IMAP_FOLDER (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    NAME varchar2(255 char) not null,
    MAIL_BOX_ID varchar2(32) not null,
    SELECTED char(1) not null,
    SELECTABLE char(1) not null,
    DISABLED char(1),
    PARENT_FOLDER_ID varchar2(32),
    --
    primary key (ID)
)^
-- end IMAP_FOLDER
-- begin IMAP_MESSAGE
create table IMAP_MESSAGE (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    FOLDER_ID varchar2(32),
    FLAGS clob,
    IS_ATL char(1) not null,
    MSG_UID number(19) not null,
    MSG_NUM number(10) not null,
    THREAD_ID number(19),
    REFERENCE_ID clob,
    MESSAGE_ID clob,
    CAPTION clob not null,
    RECEIVED_DATE timestamp,
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE
-- begin IMAP_EVENT_HANDLER
create table IMAP_EVENT_HANDLER (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    EVENT_ID varchar2(32) not null,
    HANDLING_ORDER number(10),
    BEAN_NAME varchar2(255 char) not null,
    METHOD_NAME varchar2(255 char) not null,
    --
    primary key (ID)
)^
-- end IMAP_EVENT_HANDLER
-- begin IMAP_MESSAGE_ATTACHMENT
create table IMAP_MESSAGE_ATTACHMENT (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    IMAP_MESSAGE_ID varchar2(32) not null,
    CREATED_TS timestamp not null,
    ORDER_NUMBER number(10) not null,
    NAME varchar2(255 char) not null,
    FILE_SIZE number(19) not null,
    --
    primary key (ID)
)^
-- end IMAP_MESSAGE_ATTACHMENT
-- begin IMAP_SIMPLE_AUTHENTICATION
create table IMAP_SIMPLE_AUTHENTICATION (
    ID varchar2(32),
    VERSION number(10) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar2(50 char),
    UPDATE_TS timestamp,
    UPDATED_BY varchar2(50 char),
    DELETE_TS timestamp,
    DELETED_BY varchar2(50 char),
    --
    USERNAME varchar2(255 char) not null,
    PASSWORD varchar2(255 char) not null,
    --
    primary key (ID)
)^
-- end IMAP_SIMPLE_AUTHENTICATION
