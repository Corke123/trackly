-- Trackly is one board per deployment (CONTEXT.md), and the client opens whichever board exists.
-- Seeding it here means a fresh deployment is usable immediately rather than showing an empty shell.
insert into board (name)
values ('Trackly Board');

insert into swimlane (board_id, title, position)
select board.id, seed.title, seed.position
from board,
     (values ('To Do', 0), ('In Progress', 1), ('Done', 2)) as seed(title, position)
where board.name = 'Trackly Board';
