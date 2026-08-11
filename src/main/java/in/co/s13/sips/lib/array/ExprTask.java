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
package in.co.s13.sips.lib.array;

import in.co.s13.sips.lib.ml.Tensors;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One self-contained unit of work: an expression and everything it reads.
 *
 * <p>{@link Expr#toJson} moves the computation; this moves the computation
 * <em>and its data</em> as one document. That is what a dialled-in worker — a
 * phone — actually receives: one frame, needing nothing else to produce its
 * answer. No file server round trip, no shared directory, no second request
 * from a device whose radio just went back to sleep.
 *
 * <p>Matrix bytes travel in the little-endian layout {@link Tensors} defines —
 * the same one WASM linear memory uses — so a Swift worker and a Java master
 * cannot disagree about what the bytes mean.
 *
 * <p>Everything is validated at the boundary. A worker must refuse a bad task
 * before it burns battery on it, and a master must refuse a malformed document
 * before it ships it anywhere: construction requires exactly the inputs the
 * expression reads, correctly shaped — no missing, no extra. Extra data the
 * expression never reads is at best waste on a metered link and at worst a
 * smuggling channel.
 */
public final class ExprTask {

    /** The wire format version {@link #toJson} writes. */
    public static final int JSON_VERSION = 1;

    private final Expr expr;
    private final Map<String, Mat> inputs;

    public ExprTask(Expr expr, Map<String, Mat> inputs) {
        if (expr == null) {
            throw new IllegalArgumentException("A task needs an expression");
        }
        if (inputs == null) {
            throw new IllegalArgumentException("A task needs its inputs");
        }
        for (String needed : expr.inputNames()) {
            Mat supplied = inputs.get(needed);
            if (supplied == null) {
                throw new IllegalArgumentException("Input '" + needed + "' is missing; "
                        + "a task must be self-contained and this expression reads "
                        + expr.inputNames());
            }
            int[] shape = expr.inputShape(needed);
            if (supplied.rows() != shape[0] || supplied.cols() != shape[1]) {
                throw new IllegalArgumentException("Input '" + needed + "' must be "
                        + shape[0] + "x" + shape[1] + ", not "
                        + supplied.rows() + "x" + supplied.cols());
            }
        }
        for (String name : inputs.keySet()) {
            if (!expr.inputNames().contains(name)) {
                throw new IllegalArgumentException("Input '" + name + "' is not read by "
                        + "this expression; a task carries exactly what it computes on");
            }
        }
        this.expr = expr;
        this.inputs = Map.copyOf(inputs);
    }

    /** The expression this task evaluates. */
    public Expr expr() {
        return expr;
    }

    /** Evaluates the task. */
    public Mat run() {
        return ArrayCompute.eval(expr, inputs);
    }

    /** Evaluates and encodes the answer as the bytes a worker sends home. */
    public byte[] runToBytes() {
        return Tensors.toBytes(run().data());
    }

    /** This task as one document a frame can carry. */
    public JSONObject toJson() {
        JSONObject encoded = new JSONObject()
                .put("v", JSON_VERSION)
                .put("expr", expr.toJson());
        JSONObject encodedInputs = new JSONObject();
        for (Map.Entry<String, Mat> entry : inputs.entrySet()) {
            encodedInputs.put(entry.getKey(), new JSONObject()
                    .put("rows", entry.getValue().rows())
                    .put("cols", entry.getValue().cols())
                    .put("data", Base64.getEncoder()
                            .encodeToString(Tensors.toBytes(entry.getValue().data()))));
        }
        return encoded.put("inputs", encodedInputs);
    }

    /**
     * Rebuilds a task from its document, validating everything again — the
     * document is input from the network, wherever it claims to come from.
     */
    public static ExprTask fromJson(JSONObject document) {
        if (document == null) {
            throw new IllegalArgumentException("There is no task document to read");
        }
        int version = document.optInt("v", -1);
        if (version != JSON_VERSION) {
            throw new IllegalArgumentException("Task document version " + version
                    + " is not understood; this build reads version " + JSON_VERSION);
        }
        try {
            Expr expr = Expr.fromJson(document.getJSONObject("expr"));
            JSONObject encodedInputs = document.getJSONObject("inputs");
            Map<String, Mat> inputs = new LinkedHashMap<>();
            for (String name : encodedInputs.keySet()) {
                JSONObject encoded = encodedInputs.getJSONObject(name);
                int rows = encoded.getInt("rows");
                int cols = encoded.getInt("cols");
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(encoded.getString("data"));
                } catch (IllegalArgumentException corrupt) {
                    throw new IllegalArgumentException("Input '" + name
                            + "' carries unreadable bytes", corrupt);
                }
                float[] values = Tensors.fromBytes(bytes);
                if (values.length != (long) rows * cols) {
                    throw new IllegalArgumentException("Input '" + name + "' claims "
                            + rows + "x" + cols + " but carries " + values.length
                            + " values");
                }
                inputs.put(name, new Mat(rows, cols, values));
            }
            // The constructor re-checks the match between expression and
            // inputs, so a document with missing, extra or misshapen data is
            // refused exactly as the equivalent source would be.
            return new ExprTask(expr, inputs);
        } catch (JSONException malformed) {
            throw new IllegalArgumentException("Malformed task document: "
                    + malformed.getMessage(), malformed);
        }
    }
}
