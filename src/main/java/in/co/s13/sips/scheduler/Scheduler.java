/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package in.co.s13.sips.scheduler;

import in.co.s13.sips.lib.ParallelForSENP;
import in.co.s13.sips.lib.TaskNodePair;
import java.util.ArrayList;

/**
 *
 * @author nika
 */
public interface Scheduler {
    public ArrayList<TaskNodePair> schedule();
    public ArrayList<ParallelForSENP> scheduleParallelFor();
}
