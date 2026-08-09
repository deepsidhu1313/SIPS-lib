/*
 * Copyright (C) 2026 Navdeep Singh Sidhu
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
package in.co.s13.sips.lib.accelerator;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Wire format for the devices a node advertises to its peers.
 *
 * <p>Parsing is deliberately lenient. A peer running an older build advertises
 * no devices at all, and a newer one may advertise a backend this node has never
 * heard of; neither should prevent the rest of the node's description from being
 * understood.
 */
public final class Devices {

    private Devices() {
    }

    public static JSONArray toJSON(List<Device> devices) {
        JSONArray array = new JSONArray();
        for (Device device : devices) {
            JSONObject entry = new JSONObject();
            entry.put("backend", device.backend().name());
            entry.put("id", device.id());
            entry.put("name", device.name());
            entry.put("vendor", device.vendor());
            entry.put("type", device.type().name());
            entry.put("computeUnits", device.computeUnits());
            entry.put("memory", device.globalMemoryBytes());
            array.put(entry);
        }
        return array;
    }

    /**
     * @param array a devices array, or null when the peer advertised none
     * @return the devices that could be understood; never null
     */
    public static List<Device> fromJSON(JSONArray array) {
        List<Device> devices = new ArrayList<>();
        if (array == null) {
            return devices;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            try {
                devices.add(new Device(
                        Backend.valueOf(entry.getString("backend")),
                        entry.getString("id"),
                        entry.optString("name", ""),
                        entry.optString("vendor", ""),
                        AcceleratorType.valueOf(entry.getString("type")),
                        entry.optInt("computeUnits", 1),
                        entry.optLong("memory", 1L)));
            } catch (RuntimeException ex) {
                // Unknown backend or type, or a missing required field: skip
                // this entry rather than discarding the whole advertisement.
            }
        }
        return devices;
    }

    /** This node's own devices, ready to advertise. */
    public static JSONArray local() {
        return toJSON(AcceleratorRegistry.devices());
    }
}
