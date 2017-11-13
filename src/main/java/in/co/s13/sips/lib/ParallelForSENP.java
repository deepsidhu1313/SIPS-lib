/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package in.co.s13.sips.lib;

import org.json.JSONObject;

/**
 * Parallel For Start End NodeUUID Pair
 *
 */
public class ParallelForSENP {

    private String start, end, nodeUUID;

    public ParallelForSENP(String start, String end, String nodeUUID) {
        this.start = start;
        this.end = end;
        this.nodeUUID = nodeUUID;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getNodeUUID() {
        return nodeUUID;
    }

    public void setNodeUUID(String nodeUUID) {
        this.nodeUUID = nodeUUID;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }

    public String toString(int indentFactor) {
        return toJSON().toString(indentFactor);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("start", start);
        json.put("end", end);
        json.put("nodeUUID", nodeUUID);
        return json;
    }

}
