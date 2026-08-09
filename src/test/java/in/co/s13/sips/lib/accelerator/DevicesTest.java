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

import java.util.List;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advertising devices to peers.
 *
 * <p>Nodes describe themselves in the ping response, and a scheduler can only
 * place work on hardware it knows about. These pin the wire format so a node
 * running an older build stays readable.
 */
class DevicesTest {

    private static final Device GPU = new Device(Backend.OPENCL, "opencl:1",
            "AMD Radeon Pro 5300M", "AMD", AcceleratorType.DISCRETE_GPU, 20, 4278190080L);

    @Test
    void roundTripsASingleDevice() {
        List<Device> restored = Devices.fromJSON(Devices.toJSON(List.of(GPU)));

        assertEquals(1, restored.size());
        assertEquals(GPU, restored.get(0));
    }

    @Test
    void roundTripsSeveralDevices() {
        List<Device> original = List.of(GPU,
                new Device(Backend.JAVA_CPU, "cpu:0", "host CPU", "Eclipse Adoptium",
                        AcceleratorType.CPU, 12, 8L << 30));

        assertEquals(original, Devices.fromJSON(Devices.toJSON(original)));
    }

    @Test
    void survivesASerialisedJsonRoundTrip() {
        // Exactly what happens in transit.
        JSONArray reparsed = new JSONArray(Devices.toJSON(List.of(GPU)).toString());
        assertEquals(List.of(GPU), Devices.fromJSON(reparsed));
    }

    @Test
    void emptyListRoundTrips() {
        assertTrue(Devices.fromJSON(Devices.toJSON(List.of())).isEmpty());
    }

    @Test
    void missingArrayYieldsNoDevicesRatherThanThrowing() {
        // A peer running an older build advertises no DEVICES field at all.
        assertTrue(Devices.fromJSON(null).isEmpty());
    }

    @Test
    void skipsEntriesWithAnUnknownBackendOrType() {
        // Forward compatibility: a newer peer may advertise a backend this node
        // has never heard of. Drop that entry, keep the rest.
        JSONArray wire = Devices.toJSON(List.of(GPU));
        wire.getJSONObject(0).put("backend", "QUANTUM_ANNEALER");

        assertTrue(Devices.fromJSON(wire).isEmpty());
    }

    @Test
    void skipsMalformedEntriesButKeepsGoodOnes() {
        JSONArray wire = Devices.toJSON(List.of(GPU));
        wire.put(new org.json.JSONObject().put("nonsense", true));

        List<Device> restored = Devices.fromJSON(wire);
        assertEquals(1, restored.size());
        assertEquals(GPU, restored.get(0));
    }

    @Test
    void describesTheLocalHostForAdvertisement() {
        JSONArray local = Devices.local();
        assertTrue(local.length() > 0, "a node always has at least a CPU to advertise");
    }
}
