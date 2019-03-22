-- alter table IMAP_MESSAGE_SYNC add column NEW_FOLDER_ID varchar(36) ^
-- update IMAP_MESSAGE_SYNC set NEW_FOLDER_ID = <default_value> ;
-- alter table IMAP_MESSAGE_SYNC alter column NEW_FOLDER_ID set not null ;
alter table IMAP_MESSAGE_SYNC add column NEW_FOLDER_ID varchar(36);
