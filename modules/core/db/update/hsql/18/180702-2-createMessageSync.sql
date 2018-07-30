alter table IMAP_MESSAGE_SYNC add constraint FK_IMAP_MESSAGE_SYNC_MESSAGE foreign key (MESSAGE_ID) references IMAP_MESSAGE(ID);
alter table IMAP_MESSAGE_SYNC add constraint FK_IMAP_MESSAGE_SYNC_FOLDER foreign key (FOLDER_ID) references IMAP_FOLDER(ID);
create unique index IDX_IMAP_MESSAGE_SYNC_UNIQ_MESSAGE_ID on IMAP_MESSAGE_SYNC (MESSAGE_ID) ;
create index IDX_IMAP_MESSAGE_SYNC_MESSAGE on IMAP_MESSAGE_SYNC (MESSAGE_ID);
create index IDX_IMAP_MESSAGE_SYNC_FOLDER on IMAP_MESSAGE_SYNC (FOLDER_ID);
