/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package in.co.s13.sips.lib.common.datastructure;

import java.util.Comparator;
import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class IPAddress {

    private String ip;
    private long pingScore, distance;

    public IPAddress(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public long getPingScore() {
        return pingScore;
    }

    public long incrementPingScore() {
        return (pingScore++);
    }

    public long decrementPingScore() {
        return (pingScore--);
    }

    public void setPingScore(long pingScore) {
        this.pingScore = pingScore;
    }

    public long getDistance() {
        return distance;
    }

    public void setDistance(long distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    
    
    public String toString(int indentFactor){
    return toJSON().toString(indentFactor);
    }
    
    public JSONObject toJSON() {
        JSONObject result = new JSONObject();
        result.put("ip", ip);
        result.put("distance", distance);
        result.put("pingScore", pingScore);
        return result;
    }
    
    public enum IPAddressComparator implements Comparator<IPAddress> {

        IP {
            @Override
            public int compare(IPAddress o1, IPAddress o2) {
                return o1.getIp().compareTo(o2.getIp());
            }
        },
        DISTANCE {
            @Override
            public int compare(IPAddress o1, IPAddress o2) {
                return Long.valueOf(o1.getDistance()).compareTo(o2.getDistance());
            }
        },
        PING_SCORE {
            @Override
            public int compare(IPAddress o1, IPAddress o2) {
                return Long.valueOf(o1.getPingScore()).compareTo(o2.getPingScore());
            }
        };

        public static Comparator<IPAddress> decending(final Comparator<IPAddress> other) {
            return (IPAddress o1, IPAddress o2) -> -1 * other.compare(o1, o2);
        }

        public static Comparator<IPAddress> getComparator(final IPAddressComparator... multipleOptions) {
            return (IPAddress o1, IPAddress o2) -> {
                for (IPAddressComparator option : multipleOptions) {
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
