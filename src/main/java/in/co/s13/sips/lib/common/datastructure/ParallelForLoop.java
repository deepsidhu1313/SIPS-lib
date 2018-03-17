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

/**
 *
 * @author nika
 */
public class ParallelForLoop {
    Object init, limit,diff;
    int dataType;
    boolean reverse;

    public ParallelForLoop() {
    }

    
    
    public ParallelForLoop(Object init, Object limit,Object diff, int dataType, boolean reverse) {
        this.init = init;
        this.limit = limit;
        this.diff=diff;
        this.dataType = dataType;
        this.reverse = reverse;
    }
    

    public Object getInit() {
        return init;
    }

    public void setInit(Object init) {
        this.init = init;
    }

    public Object getLimit() {
        return limit;
    }

    public void setLimit(Object limit) {
        this.limit = limit;
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public Object getDiff() {
        return diff;
    }

    public void setDiff(Object diff) {
        this.diff = diff;
    }
    
    
}
