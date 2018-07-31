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
package test;

import org.json.JSONArray;
import org.json.JSONObject;
import util.tools;

/**
 *
 * @author nika
 */
public class toCSV {

    public toCSV() {
    }

    public static void main(String[] args) {
        JSONArray jsonarray = new JSONArray(tools.readFile("array.json").trim());
        StringBuilder csvContent = new StringBuilder("id,startTime,length\n");
        for (int i = 0; i < jsonarray.length(); i++) {
            JSONObject obj = jsonarray.getJSONObject(i);
            System.out.println("Object: "+obj.toString(2));
            csvContent.append(obj.getInt("id"));
            csvContent.append(",");
            csvContent.append(obj.getLong("startTime",0l));
            csvContent.append(",");
            csvContent.append(obj.getLong("length",0l));
            csvContent.append("\n");
        }
        tools.write("array.csv", csvContent.toString());
    }
}
