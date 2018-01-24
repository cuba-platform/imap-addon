create table MAILCOMPONENT_MAIL_MESSAGE (
    ID varchar(36) not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    SEEN boolean,
    MESSAGE_UID bigint not null,
    FOLDER_NAME varchar(255) not null,
    MAIL_BOX_ID varchar(36),
    --
    primary key (ID)
);
