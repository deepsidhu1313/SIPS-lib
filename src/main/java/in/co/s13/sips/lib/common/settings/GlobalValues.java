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
package in.co.s13.sips.lib.common.settings;

import in.co.s13.sips.lib.common.datastructure.Hop;
import in.co.s13.sips.lib.common.datastructure.UniqueElementList;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author nika
 */
public class GlobalValues {

    public static ConcurrentHashMap<String, Hop> ADJACENT_NODES_TABLE = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, UniqueElementList> NON_ADJACENT_NODES_TABLE = new ConcurrentHashMap<>();
    public static long NODE_EXPIRY_TIME = 600;

}
