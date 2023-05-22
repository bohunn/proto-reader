CREATE TABLE protobuf_table (
                                obj_type text,
                                protobuf bytea
);


INSERT INTO protobuf_table (obj_type, protobuf) VALUES
                                                    ('obj1', 'message Object1 { optional string name = 1; optional int32 id = 2; optional string email = 3; }'),
                                                    ('obj2', 'message Object2 { optional string name = 1; optional int32 id = 2; optional string address = 3; }'),
                                                    ('obj3', 'message Object3 { optional string name = 1; optional int32 id = 2; optional string phone = 3; }'),
                                                    ('obj4', 'message Object4 { optional string name = 1; optional int32 id = 2; optional string city = 3; }'),
                                                    ('obj5', 'message Object5 { optional string name = 1; optional int32 id = 2; optional string country = 3; }');
