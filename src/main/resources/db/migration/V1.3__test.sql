create table IF NOT EXISTS last_time
(
    url              TEXT,
    title            TEXT,
    begin_video_path TEXT,
    new_column       INT,
    id               INTEGER not null
        constraint last_time_pk
            primary key autoincrement
);


