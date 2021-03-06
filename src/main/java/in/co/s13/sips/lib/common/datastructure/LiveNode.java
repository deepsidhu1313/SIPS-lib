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

import static in.co.s13.sips.lib.common.settings.GlobalValues.ADJACENT_NODES_TABLE;
import static in.co.s13.sips.lib.common.settings.GlobalValues.NON_ADJACENT_NODES_TABLE;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class LiveNode implements Node {

    private int task_limit, waiting_in_que;
    private String uuid, operatingSytem, hostname = "", processor_name;

    private long memory, free_memory, hdd_size, hdd_free, lastCheckedOn, lastCheckAgo;
    private ConcurrentMap<String, IPAddress> ipAddresses = new ConcurrentHashMap<>();
    private JSONObject benchmarking_results;
    private long distanceFromCurrent;
    private double cpuAvgLoad;

    public LiveNode(String uuid, String host, String os, String processor, int task_limit,
            int qwait, long ram, long free_memory, long hdd_size, long hdd_free, JSONObject benchmarking_results, long lastCheckedOn, double cpuAvgLoad) {
        this.uuid = uuid;
        this.operatingSytem = os;
        this.hostname = host;
        this.task_limit = task_limit;
        this.waiting_in_que = qwait;
        this.memory = ram;
        this.free_memory = free_memory;
        this.processor_name = processor;
        this.hdd_size = hdd_size;
        this.hdd_free = hdd_free;
        this.benchmarking_results = benchmarking_results;
        this.lastCheckedOn = lastCheckedOn;
        this.cpuAvgLoad = cpuAvgLoad;
    }

    public LiveNode(JSONObject livedbRow) {
        uuid = livedbRow.getString("uuid");
        task_limit = livedbRow.getInt("task_limit");
        waiting_in_que = livedbRow.getInt("waiting_in_que");
        operatingSytem = livedbRow.getString("operatingSytem");
        hostname = livedbRow.getString("hostname");
        processor_name = livedbRow.getString("processor_name");
        memory = livedbRow.getLong("memory");
        free_memory = livedbRow.getLong("free_memory");
        hdd_size = livedbRow.getLong("hdd_size");
        hdd_free = livedbRow.getLong("hdd_free");
        JSONArray array = livedbRow.getJSONArray("ipAddresses");
        for (int i = 0; i < array.length(); i++) {
            JSONObject ipAdd = array.getJSONObject(i);
            String ip = ipAdd.getString("ip", "");
            if (ip.contains("%")) {
                ip = ip.substring(0, ip.indexOf("%"));
            }
            addIP(ip);
            this.ipAddresses.get(ip).setPingScore(ipAdd.getLong("pingScore", 0L));
        }
        cpuAvgLoad = livedbRow.getDouble("CPULOAD", Double.MAX_VALUE);

        benchmarking_results = livedbRow.getJSONObject("benchmarking_results");
        lastCheckAgo = livedbRow.getLong("lastCheckAgo");
        lastCheckedOn = System.currentTimeMillis() - lastCheckAgo;
    }

    public double getCpuAvgLoad() {
        return cpuAvgLoad;
    }

    public void setCpuAvgLoad(double cpuAvgLoad) {
        this.cpuAvgLoad = cpuAvgLoad;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getOperatingSytem() {
        return operatingSytem;
    }

    @Override
    public void setOperatingSytem(String os) {
        this.operatingSytem = os;
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public int getTask_limit() {
        return task_limit;
    }

    @Override
    public void setTask_limit(int length) {
        this.task_limit = length;
    }

    @Override
    public int getWaiting_in_que() {
        return waiting_in_que;
    }

    @Override
    public void setWaiting_in_que(int alreadyInQue) {

        this.waiting_in_que = alreadyInQue;
    }

    @Override
    public long getMemory() {
        return memory;
    }

    @Override
    public void setMemory(long Memory) {
        this.memory = Memory;
    }

    @Override
    public long getFree_memory() {
        return free_memory;
    }

    @Override
    public void setFree_memory(long free_memory) {
        this.free_memory = free_memory;
    }

    @Override
    public ConcurrentMap<String, IPAddress> getIpAddresses() {
        return ipAddresses;
    }

    @Override
    public void setIpAddresses(ConcurrentMap<String, IPAddress> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    @Override
    public String getProcessor_name() {
        return processor_name;
    }

    @Override
    public void setProcessor_name(String name) {
        this.processor_name = name;
    }

    @Override
    public long getHdd_size() {
        return hdd_size;
    }

    @Override
    public void setHdd_size(long hdd_size) {
        this.hdd_size = hdd_size;
    }

    @Override
    public long getHdd_free() {
        return hdd_free;
    }

    @Override
    public void setHdd_free(long hdd_free) {
        this.hdd_free = hdd_free;
    }

    @Override
    public void addIP(String ip) {
        if (!this.ipAddresses.containsKey(ip)) {
            IPAddress ipAddress = new IPAddress(ip);
            this.ipAddresses.put(ip, ipAddress);
        }
    }

    @Override
    public IPAddress getIP(String ip) {
        if (!this.ipAddresses.containsKey(ip)) {
            IPAddress ipAddress = new IPAddress(ip);
            this.ipAddresses.put(ip, ipAddress);
        }
        return this.ipAddresses.get(ip);
    }

    @Override
    public IPAddress removeIp(String ip) {
        return this.ipAddresses.remove(ip);
    }

    @Override
    public JSONObject getBenchmarking_results() {
        return benchmarking_results;
    }

    @Override
    public double getCPUScore() {
        return this.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Composite Score");
    }

    @Override
    public void setBenchmarking_results(JSONObject benchmarking_results) {
        this.benchmarking_results = benchmarking_results;
    }

    @Override
    public long getLastCheckAgo() {
        return System.currentTimeMillis() - lastCheckedOn;
    }

    @Override
    public long getLastCheckedOn() {
        return lastCheckedOn;
    }

    @Override
    public long getDistanceFromCurrent() {
        if (ADJACENT_NODES_TABLE.containsKey(uuid)) {
            this.distanceFromCurrent = ADJACENT_NODES_TABLE.get(uuid).getDistance();
        } else if (NON_ADJACENT_NODES_TABLE.containsKey(uuid)) {
            UniqueElementList uniqueElementList = NON_ADJACENT_NODES_TABLE.get(uuid);
            uniqueElementList.sortElementsInAscendingOrderDistance();
            Hop hop = uniqueElementList.getNearestHop();
            this.distanceFromCurrent = hop.getDistance();
        }
        return distanceFromCurrent;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 31 * hash + Objects.hashCode(this.uuid);
        hash = 31 * hash + Objects.hashCode(this.operatingSytem);
        hash = 31 * hash + Objects.hashCode(this.hostname);
        hash = 31 * hash + Objects.hashCode(this.processor_name);
        hash = 31 * hash + (int) (this.memory ^ (this.memory >>> 32));
        return hash;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject result = new JSONObject();
        result.put("uuid", uuid);
        result.put("task_limit", task_limit);
        result.put("waiting_in_que", waiting_in_que);
        result.put("operatingSytem", operatingSytem);
        result.put("hostname", hostname);
        result.put("processor_name", processor_name);
        result.put("CPULOAD", cpuAvgLoad);
        result.put("memory", memory);
        result.put("free_memory", free_memory);
        result.put("hdd_size", hdd_size);
        result.put("hdd_free", hdd_free);
        result.put("ipAddresses", new JSONArray(ipAddresses.values().toArray()));
        result.put("benchmarking_results", benchmarking_results);
        result.put("lastCheckedOn", lastCheckedOn);
        result.put("lastCheckAgo", this.getLastCheckAgo());
        result.put("distanceFromCurrent", this.getDistanceFromCurrent());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LiveNode other = (LiveNode) obj;

        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        return true;
    }

    public enum LiveNodeComparator implements Comparator<Node> {

        UUID {
            @Override
            public int compare(Node o1, Node o2) {
                return o1.getUuid().compareTo(o2.getUuid());
            }
        },
        OS {
            @Override
            public int compare(Node o1, Node o2) {
                return (o1.getOperatingSytem()).compareTo(o2.getOperatingSytem());
            }
        },
        HOST {
            @Override
            public int compare(Node o1, Node o2) {
                return (o1.getHostname()).compareTo(o2.getHostname());
            }
        },
        QLEN {
            @Override
            public int compare(Node o1, Node o2) {
                return Integer.valueOf(o1.getTask_limit()).compareTo(o2.getTask_limit());
            }
        },
        QWAIT {
            @Override
            public int compare(Node o1, Node o2) {
                return Integer.valueOf(o1.getWaiting_in_que()).compareTo(o2.getWaiting_in_que());
            }
        },
        RAM {
            @Override
            public int compare(Node o1, Node o2) {
                return Long.valueOf(o1.getMemory()).compareTo(o2.getMemory());
            }
        },
        RAM_FREE {
            @Override
            public int compare(Node o1, Node o2) {
                return Long.valueOf(o1.getFree_memory()).compareTo(o2.getFree_memory());
            }
        },
        DISTANCE_FROM_CURRENT {
            @Override
            public int compare(Node o1, Node o2) {
                return Long.valueOf(o1.getDistanceFromCurrent()).compareTo(o2.getDistanceFromCurrent());
            }
        },
        HDD_FREE {
            @Override
            public int compare(Node o1, Node o2) {
                return Long.valueOf(o1.getHdd_free()).compareTo(o2.getHdd_free());
            }
        },
        HDD {
            @Override
            public int compare(Node o1, Node o2) {
                return Long.valueOf(o1.getHdd_size()).compareTo(o2.getHdd_size());
            }
        },
        HDD_READ_SPEED {
            @Override
            public int compare(Node o1, Node o2) {
                String data1[] = o1.getBenchmarking_results().getJSONObject("HDD").getJSONObject("Benchmarks").getString("AvgRead").split("\\s+");
                double speed1 = Double.parseDouble(data1[0]);
                String data2[] = o2.getBenchmarking_results().getJSONObject("HDD").getJSONObject("Benchmarks").getString("AvgRead").split("\\s+");
                double speed2 = Double.parseDouble(data2[0]);
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        HDD_WRITE_SPEED {
            @Override
            public int compare(Node o1, Node o2) {
                String data1[] = o1.getBenchmarking_results().getJSONObject("HDD").getJSONObject("Benchmarks").getString("AvgWrite").split("\\s+");
                double speed1 = Double.parseDouble(data1[0]);
                String data2[] = o2.getBenchmarking_results().getJSONObject("HDD").getJSONObject("Benchmarks").getString("AvgWrite").split("\\s+");
                double speed2 = Double.parseDouble(data2[0]);
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_COMPOSITE_SCORE {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Composite Score");
                double speed2 = o2.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Composite Score");
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_AVG_LOAD {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getCpuAvgLoad();
                double speed2 = o2.getCpuAvgLoad();
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_MONTE_CARLO {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Monte Carlo");
                double speed2 = o2.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Monte Carlo");
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_FFT {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("FFT");
                double speed2 = o2.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("FFT");
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_LU {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("LU");
                double speed2 = o2.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("LU");
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_SOR {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("SOR");
                double speed2 = o2.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("SOR");
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        CPU_SPARSE_MAT_MUL {
            @Override
            public int compare(Node o1, Node o2) {
                double speed1 = o1.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Sparse matmult");
                double speed2 = o2.getBenchmarking_results().getJSONObject("CPU").getJSONObject("Benchmarks").getDouble("Sparse matmult");
                return Double.valueOf(speed1).compareTo(speed2);
            }
        },
        PROCESSOR {
            @Override
            public int compare(Node o1, Node o2) {
                return (o1.getProcessor_name()).compareTo(o2.getProcessor_name());
            }
        };

        public static Comparator<Node> decending(final Comparator<Node> other) {
            return (Node o1, Node o2) -> -1 * other.compare(o1, o2);
        }

        public static Comparator<Node> getComparator(final LiveNodeComparator... multipleOptions) {
            return (Node o1, Node o2) -> {
                for (LiveNodeComparator option : multipleOptions) {
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
