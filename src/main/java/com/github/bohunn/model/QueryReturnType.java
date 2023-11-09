package com.github.bohunn.model;

import java.sql.Clob;

public class QueryReturnType {
    
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

}
