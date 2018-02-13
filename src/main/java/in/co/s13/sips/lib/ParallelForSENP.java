/* 
 * Copyright (C) 2018 Navdeep Singh Sidhu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package in.co.s13.sips.lib;

import org.json.JSONObject;

/**
 * Parallel For Start End NodeUUID Pair
 *
 */
public class ParallelForSENP {

    private String start, end, nodeUUID;

    public ParallelForSENP() {
    }
    
    
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
