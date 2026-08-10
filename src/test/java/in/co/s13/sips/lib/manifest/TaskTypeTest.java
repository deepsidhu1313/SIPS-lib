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
package in.co.s13.sips.lib.manifest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a manifest says what kind of job it is. */
class TaskTypeTest {

    @Test
    void aManifestWithoutATypeIsAJavaJob() {
        // Every manifest written before this field existed must keep working.
        assertEquals(TaskType.JAVA, TaskType.of(null));
        assertEquals(TaskType.JAVA, TaskType.of(""));
        assertEquals(TaskType.JAVA, TaskType.of("   "));
    }

    @Test
    void theSpellingInAManifestIsForgiving() {
        assertEquals(TaskType.WASM, TaskType.of("wasm"));
        assertEquals(TaskType.WASM, TaskType.of("WASM"));
        assertEquals(TaskType.WASM, TaskType.of(" Wasm "));
    }

    @Test
    void anUnknownTypeNamesWhatThisNodeCanRun() {
        // A node that quietly fell back to Java here would run the wrong thing
        // and blame the job.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> TaskType.of("lambda"));

        assertTrue(thrown.getMessage().contains("lambda"));
        assertTrue(thrown.getMessage().contains("JAVA"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("WASM"), thrown.getMessage());
    }

    @Test
    void aTypeRoundTripsThroughAManifest() {
        for (TaskType type : TaskType.values()) {
            assertEquals(type, TaskType.of(type.manifestValue()));
        }
    }
}
