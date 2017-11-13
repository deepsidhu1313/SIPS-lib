/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package in.co.s13.sips.lib;

import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class TaskNodePair {

    private String taskID, nodeUUID;

    public TaskNodePair(String taskID, String nodeUUID) {
        this.taskID = taskID;
        this.nodeUUID = nodeUUID;
    }

    public String getTaskID() {
        return taskID;
    }

    public void setTaskID(String taskID) {
        this.taskID = taskID;
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
        json.put("taskID", taskID);
        json.put("nodeUUID", nodeUUID);
        return json;
    }
}
