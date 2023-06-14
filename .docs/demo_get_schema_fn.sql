CREATE OR REPLACE FUNCTION get_schema(p_obj_type_id VARCHAR(255))
    RETURNS VARCHAR AS $$
DECLARE
    v_schema VARCHAR;
BEGIN
    RAISE NOTICE 'Received obj_type_id: %', p_obj_type_id;

    SELECT protobuf INTO v_schema
    FROM protobuf_table
    WHERE obj_type = p_obj_type_id;

    RETURN v_schema;
END;
$$ LANGUAGE plpgsql;
