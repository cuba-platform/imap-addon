alter table IMAP_MAIL_BOX add column FLAGS_SUPPORTED boolean ^
update IMAP_MAIL_BOX set FLAGS_SUPPORTED = true where FLAGS_SUPPORTED is null ;
alter table IMAP_MAIL_BOX alter column FLAGS_SUPPORTED set not null ;
