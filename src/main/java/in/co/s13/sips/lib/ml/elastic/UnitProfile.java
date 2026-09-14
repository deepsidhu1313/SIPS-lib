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
package in.co.s13.sips.lib.ml.elastic;

/**
 * The size and cost profile of one resident unit, as needed to decide where it should live.
 *
 * <p>{@code hotComputeMillis} and {@code coldComputeMillis} are usually different, since
 * running from the fast tier is normally cheaper than running in place at the slow tier's
 * speed — that is the common case this models. But nothing here enforces that relationship.
 * A caller modeling a unit that is actually cheaper to run cold (its kernel has bad occupancy
 * at this shape, say, or the slow tier happens to be a specialized accelerator of its own) is
 * describing a legitimate scenario, not a mistake, and this record must not rule it out: the
 * entire point of the policy this feeds is to let the measured numbers decide which tier wins,
 * rather than assume the fast tier always does.
 *
 * @param unitId the unit's identifier; must not be blank
 * @param sizeBytes how much fast-tier capacity this unit occupies while HOT; must be {@code > 0}
 * @param fetchMillis the cost to promote this unit from COLD to HOT (e.g. a PCIe transfer in
 *        the real system this models); must be {@code >= 0} — zero is legal, meaning an
 *        effectively free transfer, but negative makes no sense
 * @param hotComputeMillis the cost to run this unit once it is resident in HOT; must be
 *        {@code > 0}
 * @param coldComputeMillis the cost to run this unit directly from COLD, without promoting
 *        (e.g. running on CPU straight from host RAM); must be {@code > 0}
 */
public record UnitProfile(String unitId, long sizeBytes, double fetchMillis,
        double hotComputeMillis, double coldComputeMillis) {

    public UnitProfile {
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("unitId must not be blank");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive for unit '" + unitId + "'");
        }
        if (fetchMillis < 0) {
            throw new IllegalArgumentException("fetchMillis must not be negative for unit '" + unitId + "'");
        }
        if (hotComputeMillis <= 0) {
            throw new IllegalArgumentException("hotComputeMillis must be positive for unit '" + unitId + "'");
        }
        if (coldComputeMillis <= 0) {
            throw new IllegalArgumentException("coldComputeMillis must be positive for unit '" + unitId + "'");
        }
    }
}
