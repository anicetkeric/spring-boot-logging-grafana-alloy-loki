drop table if exists public.book;
drop table if exists public.author;

create table public.author
(
    id        serial primary key,
    firstname character varying(255),
    lastname  character varying(255)
);

create table public.book
(
    id          serial primary key,
    description text,
    isbn        character varying(255),
    page        integer,
    price       decimal(15, 2),
    title       character varying(100),
    author_id   integer references public.author (id)
);