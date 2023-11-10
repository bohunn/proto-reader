package com.github.bohunn.model;

import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Struct;

import org.jboss.logging.Logger;

public class QueryReturnType {

    private static final Logger LOGGER = Logger.getLogger(QueryReturnType.class);

    
    private String bdeIntlId;

    private Clob schemaClob;

    public QueryReturnType(String bdeIntlId, Clob schemaClob) {
        this.bdeIntlId = bdeIntlId;
        this.schemaClob = schemaClob;
    }

    public QueryReturnType() {
        this.bdeIntlId = "";
        this.schemaClob = null;
    }

    // getters and setters    
    public String getBdeIntlId() {
        return bdeIntlId;
    }

    public void setBdeIntlId(String bdeIntlId) {
        this.bdeIntlId = bdeIntlId;
    }

    public Clob getSchemaClob() {
        return schemaClob;
    }

    public void setSchemaClob(Clob schemaClob) {
        this.schemaClob = schemaClob;
    }

    public String toString() {
        return "QueryReturnType{" +
                "bdeIntlId='" + bdeIntlId + '\'' +
                ", schemaClob='" + schemaClob + '\'' +
                '}';
    }

    public static QueryReturnType fromStruct(Struct struct) {    
        try {
            QueryReturnType queryReturnType = new QueryReturnType();
            Object[] attributes = struct.getAttributes();
            queryReturnType.setBdeIntlId((String) attributes[0]);
            queryReturnType.setSchemaClob((Clob) attributes[1]);

            return queryReturnType;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
