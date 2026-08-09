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

/**
 * Work done against energy spent.
 *
 * <p>Performance per watt is the figure that matters on anything power-capped —
 * a laptop, a dense rack, a battery-backed edge node — where the fastest device
 * is not always the one you want. It is a plain division, but the units are
 * easy to get wrong, so they are computed in one place.
 *
 * @param elementsProcessed pixels, rows, or whatever the kernel counts
 * @param milliseconds      wall-clock duration of the work
 * @param watts             average draw during that work
 */
public record PowerReading(long elementsProcessed, double milliseconds, double watts) {

    public PowerReading {
        if (elementsProcessed < 0) {
            throw new IllegalArgumentException("Elements cannot be negative: " + elementsProcessed);
        }
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + milliseconds);
        }
        if (watts < 0) {
            throw new IllegalArgumentException("Power cannot be negative: " + watts);
        }
    }

    /** Millions of elements per second. */
    public double throughputMillionsPerSecond() {
        return (elementsProcessed / 1e6) / (milliseconds / 1000.0);
    }

    /** Joules consumed: watts times seconds. */
    public double joules() {
        return watts * (milliseconds / 1000.0);
    }

    /**
     * Millions of elements per joule — the efficiency figure.
     *
     * @return 0 when no power was measured, since dividing by zero watts would
     *         otherwise report infinite efficiency
     */
    public double millionsPerJoule() {
        double energy = joules();
        return energy <= 0 ? 0 : (elementsProcessed / 1e6) / energy;
    }

    /** Whether a real power reading backs this, or only timing. */
    public boolean hasPowerData() {
        return watts > 0;
    }
}
