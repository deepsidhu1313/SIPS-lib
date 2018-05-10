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

import java.util.Objects;
import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class FileCoverage {

    private String path;
    private int beginLine, beginColumn, endLine, endColumn;

    public FileCoverage(String path, int beginLine, int beginColumn, int endLine, int endColumn) {
        this.path = path;
        this.beginLine = beginLine;
        this.beginColumn = beginColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getBeginLine() {
        return beginLine;
    }

    public void setBeginLine(int beginLine) {
        this.beginLine = beginLine;
    }

    public int getBeginColumn() {
        return beginColumn;
    }

    public void setBeginColumn(int beginColumn) {
        this.beginColumn = beginColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.path);
        hash = 37 * hash + this.beginLine;
        hash = 37 * hash + this.beginColumn;
        hash = 37 * hash + this.endLine;
        hash = 37 * hash + this.endColumn;
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
        final FileCoverage other = (FileCoverage) obj;
        if (this.beginLine != other.beginLine) {
            return false;
        }
        if (this.beginColumn != other.beginColumn) {
            return false;
        }
        if (this.endLine != other.endLine) {
            return false;
        }
        if (this.endColumn != other.endColumn) {
            return false;
        }
        if (!Objects.equals(this.path, other.path)) {
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
        result.put("path", path);
        result.put("beginLine", beginLine);
        result.put("beginColumn", beginColumn);
        result.put("endLine", endLine);
        result.put("endColumn", endColumn);
        return result;
    }

}
