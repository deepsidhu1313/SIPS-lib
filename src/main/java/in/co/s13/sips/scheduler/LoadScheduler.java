/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package in.co.s13.sips.scheduler;

import in.co.s13.sips.lib.ParallelForSENP;
import in.co.s13.sips.lib.TaskNodePair;
import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author nika
 */
public class LoadScheduler implements Serializable {

    private Scheduler scheduler;

    public LoadScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public ArrayList<TaskNodePair> schedule() {

        return scheduler.schedule(); //To change body of generated methods, choose Tools | Templates.
    }

    public ArrayList<ParallelForSENP> scheduleParallelFor() {
        return scheduler.scheduleParallelFor();
    }

}
