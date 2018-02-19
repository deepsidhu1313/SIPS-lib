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
 *
 * @author nika
 */
public interface Node {

    public String getUuid();

    public void setUuid(String uuid);

    public String getOperatingSytem();

    public void setOperatingSytem(String os);

    public String getHostname();

    public void setHostname(String hostname);

    public int getTask_limit();

    public void setTask_limit(int length);

    public int getWaiting_in_que();

    public void setWaiting_in_que(int alreadyInQue);

    public long getMemory();

    public void setMemory(long Memory);

    public long getFree_memory();

    public void setFree_memory(long free_memory);

    public ArrayList<String> getIpAddresses();

    public void setIpAddresses(ArrayList<String> ipAddresses);

    public String getProcessor_name();

    public void setProcessor_name(String name);

    public long getHdd_size();

    public void setHdd_size(long hdd_size);

    public long getHdd_free();

    public void setHdd_free(long hdd_free);

    public void addIp(String ip);

    public boolean removeIp(String ip);

    public JSONObject getBenchmarking_results();

    public void setBenchmarking_results(JSONObject benchmarking_results);

    public long getLastCheckAgo();

    public long getLastCheckedOn();

    public long getDistanceFromCurrent();

    public String toString();

    public int hashCode();

    public JSONObject toJSON();

    public boolean equals(Object obj);
    
    public double getCPUScore();
}
