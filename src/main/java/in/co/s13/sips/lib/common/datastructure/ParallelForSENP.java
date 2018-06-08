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
package in.co.s13.sips.lib.common.datastructure;

import java.util.ArrayList;
import org.json.JSONObject;

/**
 * Parallel For Start End NodeUUID Pair
 *
 */
public class ParallelForSENP {

    private String start, end, nodeUUID, diff, duplicateOf;
    private ArrayList<String> duplicates = new ArrayList<>();
    private int chunkNo;

    public ParallelForSENP() {
    }

    public ParallelForSENP(int chunkNo, String start, String end, String nodeUUID, String diff) {
        this.chunkNo = chunkNo;
        this.start = start;
        this.end = end;
        this.nodeUUID = nodeUUID;
        this.diff = diff;
    }

    public ParallelForSENP(ParallelForSENP other) {
        this.chunkNo = other.chunkNo;
        this.start = other.start;
        this.end = other.end;
        this.nodeUUID = other.nodeUUID;
        this.diff = other.diff;
        this.duplicateOf = other.duplicateOf;
//        this.duplicates = other.duplicates;
        for (int i = 0; i < other.duplicates.size(); i++) {
            String get = other.duplicates.get(i);
            this.duplicates.add(get);
        }
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

    public String getDiff() {
        return diff;
    }

    public void setDiff(String diff) {
        this.diff = diff;
    }

    public String getId() {
        return toString();
    }

    public ArrayList<String> getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(ArrayList<String> duplicates) {
        this.duplicates = duplicates;
    }

    public void addDuplicate(String duplicateId) {
        this.duplicates.add(duplicateId);
    }

    public String getDuplicateOf() {
        return duplicateOf;
    }

    public void setDuplicateOf(String duplicateOf) {
        this.duplicateOf = duplicateOf;
    }

    public boolean isDuplicate() {
        return duplicateOf != null;
    }

    public boolean hasDuplicates() {
        return duplicates.size() > 0;
    }

    public int getChunkNo() {
        return chunkNo;
    }

    public void setChunkNo(int chunkNo) {
        this.chunkNo = chunkNo;
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
        json.put("chunkNo", chunkNo);
        json.put("start", start);
        json.put("end", end);
        json.put("nodeUUID", nodeUUID);
        json.put("diff", diff);
        json.put("duplicates", duplicates);
        return json;
    }

}
