CREATE TABLE code_bde_entity (
                                 obj_type_id VARCHAR(255) PRIMARY KEY,
                                 active CHAR(1)
);

INSERT INTO code_bde_entity (obj_type_id, active) VALUES
                                                      ('obj1', '+'),
                                                      ('obj2', '+'),
                                                      ('obj3', '+'),
                                                      ('obj4', '+'),
                                                      ('obj5', '+'),
                                                      ('obj6', NULL),
                                                      ('obj7', NULL),
                                                      ('obj8', NULL),
                                                      ('obj9', NULL),
                                                      ('obj10', NULL);
