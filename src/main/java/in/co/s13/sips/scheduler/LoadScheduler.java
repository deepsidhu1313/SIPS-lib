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
package in.co.s13.sips.scheduler;

import in.co.s13.sips.lib.common.datastructure.ParallelForSENP;
import in.co.s13.sips.lib.common.datastructure.Node;
import in.co.s13.sips.lib.common.datastructure.ParallelForLoop;
import in.co.s13.sips.lib.common.datastructure.SIPSTask;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class LoadScheduler implements Serializable {

    private Scheduler scheduler;

    public LoadScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public ArrayList<SIPSTask> schedule(ConcurrentHashMap<String, Node> nodes, ConcurrentHashMap<String, SIPSTask> tasks, JSONObject schedulerSettings) {
        return scheduler.schedule(nodes, tasks, schedulerSettings);
    }

    public ArrayList<ParallelForSENP> scheduleParallelFor(ConcurrentHashMap<String, Node> nodes, ParallelForLoop loop, JSONObject schedulerSettings) {
        return scheduler.scheduleParallelFor(nodes, loop, schedulerSettings);
    }

    public int getTotalNodes() {
        return scheduler.getTotalNodes();
    }

    public int getSelectedNodes() {
        return scheduler.getSelectedNodes();
    }

    public ArrayList<Node> getBackupNodes() {
        return scheduler.getBackupNodes();
    }

    public int getTotalChunks() {
        return scheduler.getTotalChunks();
    }

    public ArrayList<String> getErrors() {
        return scheduler.getErrors();
    }

    public ArrayList<String> getOutputs() {
        return scheduler.getErrors();

    }
}
