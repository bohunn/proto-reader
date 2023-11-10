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

    public QueryReturnType(Struct struct) {    
        try {
            Object[] attributes = struct.getAttributes();
            this.bdeIntlId = (String) attributes[0];
            this.schemaClob = (Clob) attributes[1];
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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



    public String clobToString() {
        String clobValue = null;
        Clob clob = this.schemaClob;

        if (clob != null) {
            try (Reader reader = clob.getCharacterStream()) {
                StringBuilder stringBuilder = new StringBuilder();
                char[] buffer = new char[1024];
                int bytesRead;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    stringBuilder.append(buffer, 0, bytesRead);
                }
                clobValue = stringBuilder.toString();
            } catch (IOException e) {
                throw new SQLException(e);
            }
        }
        return clobValue;
    }

}
