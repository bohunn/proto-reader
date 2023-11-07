--+------------------------------------------------------------------------+
create or replace PACKAGE proto_gen# IS

--+------------------------------------------------------------------------+
  
-- Package: proto_gen#
-- Package that contains a procecude to read the protobuf defition from table  
  
  
--+------------------------------------------------------------------------+
  PROCEDURE FILL_META_VERSION_SCHEMA();

  FUNCTION GEN(
    I_OBJ_TYPE_ID                       PLS_INTEGER
  ) RETURN CLOB;

  FUNCTION GEN2(
    I_OBJ_TYPE_ID                       PLS_INTEGER
  ) RETURN PROTO_TYPE;

END proto_gen#;
-- Path: .docs/PROTO_GEN#.sql
create or replace PACKAGE BODY proto_gen# IS

  B_BUF                                           BUF#.T_BUF;

  PROCEDURE FILL_META_VERSION_SCHEMA
  IS
    CURSOR c_bde_entity_ids IS
    SELECT cbev.id
    FROM code_bde_entity_version cbev
    WHERE cbev.activ = '+' and cbev.gen_view = '+';

    V_BDE_ENTITY_ID CODE_BDE_ENTITY_VERSION.ID%TYPE;
  BEGIN
    OPEN c_bde_entity_ids;

    LOOP
      FETCH c_bde_entity_ids INTO V_BDE_ENTITY_ID;
      EXIT WHEN c_bde_entity_ids%NOTFOUND;
      -- use avaloq procedure to produce protobuf entries into meta_bde_entity_version
      meta_bde_prot_buf#.gen(I_BDE_ENTITY_VERSION_ID => V_BDE_ENTITY_ID);
    END LOOP;

  END FILL_META_VERSION_SCHEMA;

  PROCEDURE BUF#ADD_LINE(
    I_LINE                                        VARCHAR2    := NULL
  )
  IS
  BEGIN
    BUF#.BUF#WRITE(B_BUF, I_LINE || DEF.RTN);
  EXCEPTION
    WHEN OTHERS THEN RAISE_FA_ERR('buf#add_line('||I_LINE||')');
  END BUF#ADD_LINE;

  FUNCTION GEN2(
    I_OBJ_TYPE_ID                       PLS_INTEGER
  ) RETURN PROTO_TYPE
  IS
  BEGIN
    DBMS_OUTPUT.put_line('test');
  EXCEPTION
    WHEN OTHERS THEN
      ERR#.RAISE_FA_ERR('gen('||I_OBJ_TYPE_ID||')', BUF#.REMV_TO_CLOB(B_BUF));  
  END GEN2;


  FUNCTION GEN(
    I_OBJ_TYPE_ID                       PLS_INTEGER
  ) RETURN CLOB
  IS
    L_BDE_VERSION_ID                              PLS_INTEGER;
    L_CODE_BDE_ENTITY_ID                          PLS_INTEGER;
    L_ENTITY                                      VARCHAR2(100);
    L_VERSION                                     VARCHAR2(100);
    L_CLOB                                        CLOB;
    L_COMPD_TYPE                                  VARCHAR2(100);
    L_COMPD_MAP                                   DEF.MAP_CHAR;
    L_TYPE_MAP                                    DEF.MAP_BOOLEAN;
    L_BDE_TYPE_MAP                                DEF.MAP_BOOLEAN;
    L_BDE_TYPE                                    VARCHAR2(100);
    L_DO_SKIP_COMPD                               BOOLEAN := FALSE;
    L_FLD_INFO                                    VARCHAR2(4000);
  BEGIN
    B_BUF := BUF#.NEW;

    SELECT CBE.ID
    INTO L_CODE_BDE_ENTITY_ID
    FROM CODE_BDE_ENTITY CBE
    WHERE CBE.OBJ_TYPE_ID = I_OBJ_TYPE_ID;

    SELECT CBEV.ID
    INTO L_BDE_VERSION_ID
    FROM CODE_BDE_ENTITY_VERSION CBEV
    WHERE CBEV.ACTIV = '+'
    AND CBEV.BDE_ENTITY_ID = l_code_bde_entity_id;
    
    IF L_BDE_VERSION_ID IS NOT NULL THEN
        FOR C IN (
          SELECT ROW_NUMBER() OVER (PARTITION BY V.ENTITY, V.ENTITY_VERSION ORDER BY V.FLD_LVL, V.COMPD_PATH_BASE NULLS FIRST, V.PROT_BUF_FLD_NR) RN
                ,ROW_NUMBER() OVER (PARTITION BY V.ENTITY, V.ENTITY_VERSION, V.COMPD_PATH_BASE ORDER BY V.PROT_BUF_FLD_NR)            COMPD_RN
                ,COUNT(1) OVER (PARTITION BY V.ENTITY, V.ENTITY_VERSION, V.COMPD_PATH_BASE)                                           COMPD_RCNT
                ,V.*
          FROM   (
                  SELECT RTRIM(ENTITY ||'#'||COMPD_PATH_BASE, '#') COMPD_PATH
                        ,BES.*
                  FROM   BDE_ENTITY_STRUCT_V BES
                  WHERE  BES.BDE_ENTITY_VERSION_ID = L_BDE_VERSION_ID
                    AND  BES.PROTO_ACTIV IS NOT NULL
                 ) V
          ORDER BY RN
        ) LOOP
          IF C.RN = 1 THEN
            L_ENTITY := C.ENTITY;
            L_VERSION := C.ENTITY_VERSION;
            L_COMPD_MAP(C.ENTITY) := L_ENTITY;
            BUF#ADD_LINE('syntax = "proto3";');
            BUF#ADD_LINE;
            BUF#ADD_LINE('package com.avaloq.acp.bde.protobuf.'||L_ENTITY||';');
            BUF#ADD_LINE;
            BUF#ADD_LINE('import "meta_model.proto";');
            BUF#ADD_LINE('import "wrappers.proto";');
            BUF#ADD_LINE('import "options.proto";');
            BUF#ADD_LINE;
            BUF#ADD_LINE('option (version) = "'||L_VERSION||'";');
          END IF;
          IF C.COMPD_RN = 1 THEN
            BEGIN
              L_COMPD_TYPE := L_COMPD_MAP(C.COMPD_PATH);
            EXCEPTION
              WHEN OTHERS THEN RAISE_FA_ERR('not found: ' || C.COMPD_PATH || ' (' || I_OBJ_TYPE_ID || ' - ' || C.ENTITY || ' v' || C.ENTITY_VERSION || ')');
            END;
            IF L_TYPE_MAP.EXISTS(L_COMPD_TYPE) THEN
              L_DO_SKIP_COMPD := TRUE;
            ELSE
              L_DO_SKIP_COMPD := FALSE;
              L_TYPE_MAP(L_COMPD_TYPE) := TRUE;
              BUF#ADD_LINE;
              BUF#ADD_LINE('message ' || UPPER(SUBSTR(L_COMPD_TYPE, 1, 1)) || LOWER(SUBSTR(L_COMPD_TYPE, 2)) || ' {');
              L_BDE_TYPE_MAP(UPPER(SUBSTR(L_COMPD_TYPE, 1, 1)) || LOWER(SUBSTR(L_COMPD_TYPE, 2))) := TRUE;
            END IF;
            IF C.RN = 1 THEN
              BUF#ADD_LINE('  option (is_bde_entity) = true;');
            END IF;
          END IF;
          IF C.IS_COMPD IS NOT NULL THEN
            L_COMPD_MAP(C.ENTITY || '#' || C.FLD_PATH) := LOWER(C.BDE_FLD_TYPE);
            IF NOT L_BDE_TYPE_MAP.EXISTS(C.BDE_FLD_TYPE) THEN
              L_BDE_TYPE_MAP(C.BDE_FLD_TYPE) := FALSE;
            END IF;
          END IF;
          IF L_DO_SKIP_COMPD THEN CONTINUE; END IF;
          L_FLD_INFO := NULL;
          IF C.REF_BDE_ENTITY IS NOT NULL THEN
            L_FLD_INFO := CASE WHEN L_FLD_INFO IS NOT NULL THEN L_FLD_INFO || ', ' END || '(bde_entity) = "'||C.REF_BDE_ENTITY||'"';
          END IF;
          IF C.REF_BDE_TYPE IS NOT NULL THEN
            L_FLD_INFO := CASE WHEN L_FLD_INFO IS NOT NULL THEN L_FLD_INFO || ', ' END || '(bde_ref_type) = "'||C.REF_BDE_TYPE||'"';
          END IF;
          IF C.BDE_FLD_FORMAT IS NOT NULL THEN
            L_FLD_INFO := CASE WHEN L_FLD_INFO IS NOT NULL THEN L_FLD_INFO || ', ' END || '(format) = "'||C.BDE_FLD_FORMAT||'"';
          END IF;
          IF L_FLD_INFO IS NOT NULL THEN
            L_FLD_INFO := ' [' || L_FLD_INFO || ']';
          END IF;
          BUF#ADD_LINE(
              '  '
            ||CASE WHEN C.IS_LIST IS NOT NULL THEN 'repeated ' END
            ||C.BDE_FLD_TYPE
            ||' '
            ||C.FLD_NAME_DDIC
            ||' = '
            ||C.PROT_BUF_FLD_NR
            ||L_FLD_INFO
            ||';'
          );
          IF C.COMPD_RN = C.COMPD_RCNT THEN
            BUF#ADD_LINE('}');
          END IF;
        END LOOP;
    
    
        L_BDE_TYPE := L_BDE_TYPE_MAP.FIRST;
        WHILE L_BDE_TYPE IS NOT NULL LOOP
          IF NOT L_BDE_TYPE_MAP(L_BDE_TYPE) THEN
            BUF#ADD_LINE('');
            BUF#ADD_LINE('message ' || L_BDE_TYPE || ' {');
            BUF#ADD_LINE('}');
          END IF;
          L_BDE_TYPE := L_BDE_TYPE_MAP.NEXT(L_BDE_TYPE);
        END LOOP;
    
        L_CLOB := BUF#.BUF#CLOB(B_BUF ,I_FORCE => TRUE);
    END IF;

    return L_CLOB;

--  EXCEPTION
--    WHEN OTHERS THEN
--      ERR#.RAISE_FA_ERR('gen('||I_OBJ_TYPE_ID||')', BUF#.REMV_TO_CLOB(B_BUF));
  END GEN;


END proto_gen#;