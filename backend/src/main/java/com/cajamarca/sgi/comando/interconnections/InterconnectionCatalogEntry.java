package com.cajamarca.sgi.comando.interconnections;

import java.util.ArrayList;
import java.util.List;

public class InterconnectionCatalogEntry {
    public String interconnectionRef;
    public String interconnectionId;
    public List<String> legacyConnectionIds = new ArrayList<>();
    public String sourceProgramId;
    public String targetProgramId;
    public String purpose;
    public String interactionType;
    public String mode;
    public String protocol;
    public String contractVersion;
    public String dataOwnerProgramId;
    public String criticality;
    public String status;
    public boolean legacyTarget;
    public String targetImplementationStatus;
    public List<InterfaceDefinition> interfaces = new ArrayList<>();

    public static class InterfaceDefinition {
        public String interfaceId;
        public String method;
        public String path;
        public String purpose;
        public String contentType;
        public Object request;
        public Object response;
    }
}
