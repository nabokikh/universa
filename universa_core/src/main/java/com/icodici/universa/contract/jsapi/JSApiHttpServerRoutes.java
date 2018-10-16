package com.icodici.universa.contract.jsapi;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class JSApiHttpServerRoutes {

    private int portToListen = 8080;
    private String[] jsParams = new String[0];
    private Map<String, RouteModel> routes = new HashMap<>();

    public void addNewRoute(String endpoint, String handlerMethodName, Contract contract, String scriptName, HashId slotId) {
        RouteModel prev = routes.putIfAbsent(endpoint, new RouteModel(endpoint, handlerMethodName, contract, scriptName, slotId));
        if (prev != null)
            throw new IllegalArgumentException("JSApiHttpServerRoutes error: endpoint duplicates");
    }

    public void addNewRoute(String endpoint, String handlerMethodName, Contract contract, String scriptName) {
        addNewRoute(endpoint, handlerMethodName, contract, scriptName, null);
    }

    public void setPortToListen(int newValue) {
        this.portToListen = newValue;
    }

    public int getPortToListen() {
        return portToListen;
    }

    public void setJsParams(String[] newValue) {
        this.jsParams = newValue;
    }

    public String[] getJsParams() {
        return jsParams;
    }

    public void forEach(BiConsumer<String, RouteModel> lambda) {
        routes.forEach(lambda);
    }

    class RouteModel {
        public String endpoint;
        public String handlerMethodName;
        public Contract contract;
        public String scriptName;
        public HashId slotId;
        public RouteModel(String endpoint, String handlerMethodName, Contract contract, String scriptName, HashId slotId) {
            this.endpoint = endpoint;
            this.handlerMethodName = handlerMethodName;
            this.contract = contract;
            this.scriptName = scriptName;
            this.slotId = slotId;
        }
    }
}
