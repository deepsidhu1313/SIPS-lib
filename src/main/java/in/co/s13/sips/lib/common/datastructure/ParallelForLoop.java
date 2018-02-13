/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
