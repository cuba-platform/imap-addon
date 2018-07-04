create table IMAP_MESSAGE_SYNC (
    ID varchar(36) not null,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    FLAGS longvarchar,
    --
    MESSAGE_ID varchar(36) not null,
    FOLDER_ID varchar(36) not null,
    STATUS varchar(50) not null,
    FOLDERS_TO_CHECK_NUM integer,
    CHECKED_FOLDERS_NUM integer,
    NEW_FOLDER_NAME varchar(255),
    --
    primary key (ID)
);
