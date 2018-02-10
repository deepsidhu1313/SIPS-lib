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

import java.util.Comparator;
import java.util.Objects;
import org.json.JSONObject;

/**
 *
 * @author nika
 */
public class Hop {

    private String id;
    private long distance, timestamp;

    public Hop(String id, long distance) {
        this.id = id;
        this.distance = distance;
        timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getDistance() {
        return distance;
    }

    public void setDistance(long distance) {
        this.distance = distance;
        timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("distance", distance);
        return json;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.id);
        hash = 59 * hash + (int) (this.distance ^ (this.distance >>> 32));
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
        final Hop other = (Hop) obj;
        if (this.distance != other.distance) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    enum HopComparator implements Comparator<Hop> {
        DISTANCE_SORT {
            @Override
            public int compare(Hop o1, Hop o2) {
                return Long.valueOf(o1.getDistance()).compareTo(o2.getDistance());
            }
        },
        TIMESTAMP_SORT {
            @Override
            public int compare(Hop o1, Hop o2) {
                return Long.valueOf(o1.getTimestamp()).compareTo(o2.getTimestamp());
            }
        };

        public static Comparator<Hop> decending(final Comparator<Hop> other) {
            return (Hop o1, Hop o2) -> -1 * other.compare(o1, o2);
        }

        public static Comparator<Hop> getComparator(final HopComparator... multipleOptions) {
            return (Hop o1, Hop o2) -> {
                for (HopComparator option : multipleOptions) {
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
