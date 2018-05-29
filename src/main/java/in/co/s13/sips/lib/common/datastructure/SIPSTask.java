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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class SIPSTask {

    private String name;
    private BigDecimal length = new BigDecimal("1");
    private BigInteger timeout = new BigInteger("1000000");
    private ArrayList<String> resources = new ArrayList<>();
    private ArrayList<FileCoverage> files = new ArrayList<>();
    private String nodeUUID, duplicateOf;
    private ArrayList<String> duplicates = new ArrayList<>();
    private ArrayList<String> dependsOn = new ArrayList<>();
    private int id;

    public SIPSTask(int id, String name) {
        this.name = name;
        this.id = id;
    }

    public SIPSTask(SIPSTask otherTask) {
        this.name = otherTask.name;
        this.nodeUUID = otherTask.nodeUUID;
        this.duplicateOf = otherTask.duplicateOf;
        this.id = otherTask.id;
        this.length = otherTask.length;
        this.timeout = otherTask.timeout;
        this.resources = otherTask.resources;
        this.files = otherTask.files;
        this.duplicates = otherTask.duplicates;
        this.dependsOn = otherTask.dependsOn;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<FileCoverage> getFiles() {
        return files;
    }

    public void setFiles(ArrayList<FileCoverage> files) {
        this.files = files;
    }

    public void addFile(FileCoverage filename) {
        this.files.add(filename);
    }

    public BigDecimal getLength() {
        return length;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public BigInteger getTimeout() {
        return timeout;
    }

    public void setTimeout(BigInteger timeout) {
        this.timeout = timeout;
    }

    public ArrayList<String> getResources() {
        return resources;
    }

    public void setResources(ArrayList<String> resources) {
        this.resources = resources;
    }

    public void addResource(String resourceName) {
        if (!resources.contains(resourceName)) {
            this.resources.add(resourceName);
        }
    }

    public String getNodeUUID() {
        return nodeUUID;
    }

    public void setNodeUUID(String nodeUUID) {
        this.nodeUUID = nodeUUID;
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ArrayList<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(ArrayList<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public void addDependency(String dependsOn) {
        if (!this.dependsOn.contains(dependsOn)) {
            this.dependsOn.add(dependsOn);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.name);
        hash = 41 * hash + Objects.hashCode(this.files);
        hash = 41 * hash + Objects.hashCode(this.length);
        hash = 41 * hash + Objects.hashCode(this.timeout);
        hash = 41 * hash + Objects.hashCode(this.resources);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SIPSTask other = (SIPSTask) obj;

        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.files, other.files)) {
            return false;
        }
        if (!Objects.equals(this.length, other.length)) {
            return false;
        }
        if (!Objects.equals(this.timeout, other.timeout)) {
            return false;
        }
        if (!Objects.equals(this.resources, other.resources)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }

    public String toString(int indentFactor) {
        return toJSON().toString(indentFactor);
    }

    public JSONObject toJSON() {
        JSONObject result = new JSONObject();
        result.put("name", name);
        result.put("files", files);
        result.put("length", length.doubleValue());
        result.put("timeout", timeout.longValue());
        result.put("resources", resources);
        result.put("id", id);
        return result;
    }

    public enum SIPSTaskComparator implements Comparator<SIPSTask> {

        Name {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return o1.getName().compareTo(o2.getName());
            }
        },
        NO_OF_FILES {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return Integer.valueOf(o1.getFiles().size()).compareTo(o2.getFiles().size());
            }
        },
        WEIGHT {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return (o1.getLength()).compareTo(o2.getLength());
            }
        },
        TIMEOUT {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return (o1.getTimeout()).compareTo(o2.getTimeout());
            }
        }, ID {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return Integer.valueOf(o1.getId()).compareTo(o2.getId());
            }
        },
        NO_OF_RESOURCES {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return Integer.valueOf(o1.getResources().size()).compareTo(o2.getResources().size());
            }
        },
        NO_OF_DEPENDENCIES {
            @Override
            public int compare(SIPSTask o1, SIPSTask o2) {
                return Integer.valueOf(o1.getDependsOn().size()).compareTo(o2.getDependsOn().size());
            }
        };

        public static Comparator<SIPSTask> decending(final Comparator<SIPSTask> other) {
            return (SIPSTask o1, SIPSTask o2) -> -1 * other.compare(o1, o2);
        }

        public static Comparator<SIPSTask> getComparator(final SIPSTaskComparator... multipleOptions) {
            return (SIPSTask o1, SIPSTask o2) -> {
                for (SIPSTaskComparator option : multipleOptions) {
                    int result = option.compare(o1, o2);
                    if (result != 0) {
                        return result;
                    }
                }
                return 0;
            };
        }
    }

}
