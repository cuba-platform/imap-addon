alter table IMAP_MESSAGE_SYNC alter column NEW_FOLDER_ID rename to OLD_FOLDER_ID ^
drop index IDX_IMAP_MESSAGE_SYNC_NEW_FOLDER ;
alter table IMAP_MESSAGE_SYNC drop constraint FK_IMAP_MESSAGE_SYNC_NEW_FOLDER ;
