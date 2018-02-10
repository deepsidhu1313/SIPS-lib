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

import static in.co.s13.sips.lib.common.datastructure.Hop.HopComparator.TIMESTAMP_SORT;
import in.co.s13.sips.lib.common.settings.GlobalValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author nika
 */
public class UniqueElementList {

    private ArrayList<Hop> arrayList = new ArrayList<>();

    public UniqueElementList() {
    }

    public UniqueElementList(Hop... h) {
        for (int i = 0; i < h.length; i++) {
            Hop hop = h[i];
            this.addHop(hop);
        }
    }

    public synchronized boolean addHop(Hop e) {
        for (int i = 0; i < arrayList.size(); i++) {
            Hop get = arrayList.get(i);
            if (get.getId().equals(e.getId())) {
                get.setDistance(e.getDistance());
                return true;
            }
        }

        return arrayList.add(e); //To change body of generated methods, choose Tools | Templates.
    }

    public synchronized void remove(String id) {
        for (int i = 0; i < arrayList.size(); i++) {
            Hop get = arrayList.get(i);
            if (get.getId().equals(id)) {
                arrayList.remove(i);
                return;
            }
        }
    }

    public ArrayList<Hop> getArrayList() {
        return arrayList;
    }

    public synchronized void sortElementsInAscendingOrderDistance() {
        Collections.sort(arrayList, Hop.HopComparator.DISTANCE_SORT);
    }

    public synchronized void sortElementsInAscendingOrderTimestamp() {
        Collections.sort(arrayList, Hop.HopComparator.TIMESTAMP_SORT);
    }

    public synchronized void sortElementsInDecendingOrderTimestamp() {
        Collections.sort(arrayList, Hop.HopComparator.decending(TIMESTAMP_SORT));
    }

    public synchronized void removeExpiredElements() {
        this.sortElementsInDecendingOrderTimestamp();
        for (int i = arrayList.size() - 1; i >= 0; i--) {
            Hop get = arrayList.get(i);
            if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - get.getTimestamp()) >= GlobalValues.NODE_EXPIRY_TIME) {
                arrayList.remove(i);
            } else {
                break;
            }
        }
    }

    public synchronized Hop getNearestHop() {
        this.sortElementsInAscendingOrderDistance();
        if (arrayList.size() > 0) {
            return arrayList.get(0);
        }
        return new Hop("Empty", Long.MAX_VALUE);
    }

}
