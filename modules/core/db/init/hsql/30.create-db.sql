insert into MAILCOMPONENT_MAIL_EVENT_TYPE(
  id,
  VERSION,
  CREATE_TS,
  NAME,
  DESCRIPTION
) values
('5aacdc0b-9983-4b9c-8c00-c26940f98643', 1, NOW (), 'NEW_EMAIL', 'A new email appeared'),
('70479c8e-0007-43fe-8de4-f41f2ed887e8', 1, NOW (), 'EMAIL_SEEN', 'An email was marked as “Seen”'),
('4b5377c0-bb92-4f19-aceb-4cb50f2147d0', 1, NOW (), 'NEW_ANSWER', 'An email was answered'),
('79311f31-fc84-466f-b152-dd1d7e632ff0', 1, NOW (), 'EMAIL_MOVED', 'An email was moved'),
('f4901e65-140d-43d5-82f5-d830c8a6e1b4', 1, NOW (), 'FLAGS_UPDATED', 'Any flags on any emails were updated'),
('223f2237-6320-494e-9d6d-402e6e751bd1', 1, NOW (), 'EMAIL_DELETED', 'An email was deleted permanently'),
('ff68c949-1f37-48f8-9b23-e3bc7bb4a242', 1, NOW (), 'NEW_THREAD', 'A new email thread was created');
