create table IF NOT EXISTS download_step
(
    id        integer not null
        constraint download_step_pk
            primary key autoincrement,
    step_name varchar(200),
    succeed   boolean default false,
    "order"   int
);

create unique index IF NOT EXISTS download_step_id_uindex
    on download_step (id);

create table IF NOT EXISTS download_video
(
    id      integer not null
        constraint download_video_pk
            primary key autoincrement,
    url     text,
    title   text,
    succeed boolean default false
);

create unique index IF NOT EXISTS download_video_id_uindex
    on download_video (id);
