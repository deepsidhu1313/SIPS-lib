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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class Task {

    String className, name, file;
    BigInteger length, timeout;
    ArrayList<String> resources;
    
    public Task(String className, String name, String file, BigInteger length, BigInteger timeout, ArrayList<String> resources) {
        this.className = className;
        this.name = name;
        this.file = file;
        this.length = length;
        this.timeout = timeout;
        this.resources = resources;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFile() {
        return file;
    }
    
    public void setFile(String file) {
        this.file = file;
    }
    
    public BigInteger getLength() {
        return length;
    }
    
    public void setLength(BigInteger length) {
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
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.className);
        hash = 41 * hash + Objects.hashCode(this.name);
        hash = 41 * hash + Objects.hashCode(this.file);
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
        final Task other = (Task) obj;
        if (!Objects.equals(this.className, other.className)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.file, other.file)) {
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
        result.put("className", className);
        result.put("name", name);
        result.put("file", file);
        result.put("length", length);
        result.put("timeout", timeout);
        result.put("resources", resources);
        return result;
    }
    
}
