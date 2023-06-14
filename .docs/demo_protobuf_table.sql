create table if not exists protobuf_table
(
    obj_type text,
    protobuf bytea
);

alter table protobuf_table
    owner to admin;

