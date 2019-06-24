/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.apache.mxnet.engine;

import com.amazon.ai.Context;
import com.amazon.ai.ndarray.Matrix;
import com.amazon.ai.ndarray.NDArray;
import com.amazon.ai.ndarray.NDFactory;
import com.amazon.ai.ndarray.NDList;
import com.amazon.ai.ndarray.internal.NDArrayEx;
import com.amazon.ai.ndarray.types.DataDesc;
import com.amazon.ai.ndarray.types.DataType;
import com.amazon.ai.ndarray.types.Layout;
import com.amazon.ai.ndarray.types.Shape;
import com.amazon.ai.ndarray.types.SparseFormat;
import com.amazon.ai.training.GradReq;
import com.amazon.ai.util.PairList;
import com.amazon.ai.util.Utils;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import org.apache.mxnet.jna.FunctionInfo;
import org.apache.mxnet.jna.JnaUtils;

public class MxNDArray extends NativeResource implements NDArray {

    private static final int MAX_DEPTH = 10;
    private static final int MAX_PRINT_ROWS = 10;
    private static final int MAX_PRINT_ITEMS = 20;
    private static final String LF = System.getProperty("line.separator");

    private Context context;
    private SparseFormat sparseFormat;
    private DataType dataType;
    private Shape shape;
    private MxNDFactory factory;
    private boolean isReady;

    MxNDArray(
            MxNDFactory factory, Context context, Shape shape, DataType dataType, Pointer handle) {
        super(handle);
        this.factory = factory;
        this.context = context;
        this.dataType = dataType;
        this.shape = shape;
        this.isReady = false;
    }

    MxNDArray(MxNDFactory factory, Context context, Shape shape, DataType dataType) {
        this(
                factory,
                context,
                shape,
                dataType,
                JnaUtils.createNdArray(context, shape, dataType, shape.dimension(), false));
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getEncoded() {
        return new byte[0];
    }

    /** {@inheritDoc} */
    @Override
    public void encode(OutputStream os) {}

    @Override
    public NDFactory getFactory() {
        return factory;
    }

    public void detach() {
        factory.detach(this);
        factory = MxNDFactory.SYSTEM_FACTORY;
    }

    public void attach(MxNDFactory factory) {
        detach();
        this.factory = factory;
        factory.attach(this);
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        if (dataType == null) {
            dataType = JnaUtils.getDataType(getHandle());
        }
        return dataType;
    }

    /** {@inheritDoc} */
    @Override
    public Context getContext() {
        if (context == null) {
            context = JnaUtils.getContext(getHandle());
        }
        return context;
    }

    /** {@inheritDoc} */
    @Override
    public Shape getShape() {
        if (shape == null) {
            shape = JnaUtils.getShape(getHandle());
        }
        return shape;
    }

    public SparseFormat getSparseFormat() {
        if (sparseFormat == null) {
            sparseFormat = JnaUtils.getStorageType(getHandle());
        }
        return sparseFormat;
    }

    /** {@inheritDoc} */
    @Override
    public Layout getLayout() {
        return Layout.UNDEFINED;
    }

    /** {@inheritDoc} */
    @Override
    public DataDesc getDataDescriptor() {
        return new DataDesc(getShape(), getDataType(), null, getLayout(), getContext());
    }

    /** {@inheritDoc} */
    @Override
    public void set(Buffer data) {
        if (data.remaining() != getShape().size()) {
            throw new IllegalArgumentException(
                    "array size ("
                            + data.remaining()
                            + ")do not match the size of NDArray ("
                            + getShape().size());
        }
        JnaUtils.syncCopyFromCPU(getHandle(), data);
    }

    /** {@inheritDoc} */
    @Override
    public void set(List<Float> data) {
        waitToWrite();
        int size = data.size();
        FloatBuffer output =
                ByteBuffer.allocateDirect(size * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        for (Float v : data) {
            output.put(v);
        }
        output.rewind();
        set(output);
    }

    /** {@inheritDoc} */
    @Override
    public void set(float[] data) {
        waitToWrite();
        int size = data.length;
        FloatBuffer output =
                ByteBuffer.allocateDirect(size * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        for (float v : data) {
            output.put(v);
        }
        output.rewind();
        set(output);
    }

    /** {@inheritDoc} */
    @Override
    public void set(int[] data) {
        waitToWrite();
        int size = data.length;
        IntBuffer output =
                ByteBuffer.allocateDirect(size * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        for (int v : data) {
            output.put(v);
        }
        output.rewind();
        set(output);
    }

    /** {@inheritDoc} */
    @Override
    public void set(double[] data) {
        waitToWrite();
        int size = data.length;
        DoubleBuffer output =
                ByteBuffer.allocateDirect(size * 4).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();
        for (double v : data) {
            output.put(v);
        }
        output.rewind();
        set(output);
    }

    /** {@inheritDoc} */
    @Override
    public void set(long[] data) {
        waitToWrite();
        int size = data.length;
        LongBuffer output =
                ByteBuffer.allocateDirect(size * 4).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
        for (long v : data) {
            output.put(v);
        }
        output.rewind();
        set(output);
    }

    /** {@inheritDoc} */
    @Override
    public void set(byte[] data) {
        waitToWrite();
        int size = data.length;
        ShortBuffer output =
                ByteBuffer.allocateDirect(size * 4).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (byte v : data) {
            output.put(v);
        }
        output.rewind();
        set(output);
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray at(int index) {
        Pointer pointer = JnaUtils.ndArrayAt(getHandle(), index);
        return factory.create(pointer);
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray slice(int begin, int end) {
        Pointer pointer = JnaUtils.slice(getHandle(), begin, end);
        return factory.create(pointer);
    }

    /** {@inheritDoc} */
    @Override
    public void copyTo(NDArray ndArray) {
        if (!(ndArray instanceof MxNDArray)) {
            throw new IllegalArgumentException("Only MxNDArray is supported.");
        }
        Shape inShape = getShape();
        Shape destShape = ndArray.getShape();
        if (!Arrays.equals(inShape.getShape(), destShape.getShape())) {
            throw new IllegalArgumentException(
                    String.format("shape are diff. Required: %s, Actual %s", destShape, inShape));
        }
        NDArray[] src = new NDArray[] {this};
        NDArray[] dest = new NDArray[] {ndArray};
        factory.invoke("_copyto", src, dest, null);
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray asInContext(Context ctx, boolean copy) {
        if (ctx.equals(getContext()) && !copy) {
            return this;
        }
        MxNDArray nd = factory.create(ctx, getShape(), getDataType());
        copyTo(nd);
        return nd;
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray asType(DataType dtype, boolean copy) {
        if (dtype.equals(getDataType()) && !copy) {
            return this;
        }
        MxNDArray nd = factory.create(getContext(), getShape(), dtype);
        copyTo(nd);
        return nd;
    }

    /** {@inheritDoc} */
    public void waitToRead() {
        if (!isReady) {
            JnaUtils.waitToRead(getHandle());
            isReady = true;
        }
    }

    public void waitToWrite() {
        if (!isReady) {
            JnaUtils.waitToWrite(getHandle());
            isReady = true;
        }
    }

    public void waitAll() {
        JnaUtils.waitToRead(getHandle());
        isReady = true;
    }

    /** {@inheritDoc} */
    @Override
    public void attachGrad() {
        attachGrad(GradReq.WRITE, null);
    }

    /** {@inheritDoc} */
    @Override
    public void attachGrad(GradReq gradReq, SparseFormat sparseFormat) {
        MxNDArray grad;
        if (sparseFormat == null || sparseFormat == SparseFormat.UNDEFINED) {
            grad = (MxNDArray) zerosLike();
        } else {
            grad = (MxNDArray) factory.zeros(context, shape, dataType);
        }
        int gradReqValue = gradReq.getValue();
        IntBuffer gradReqBuffer = IntBuffer.allocate(1);
        gradReqBuffer.put(0, gradReqValue);
        JnaUtils.autogradMarkVariables(1, getHandle(), gradReqBuffer, grad.getHandle());
    }

    /** {@inheritDoc} */
    @Override
    public void backward() {
        backward(null, false, true);
    }

    /** {@inheritDoc} */
    @Override
    public void backward(boolean retainGraph, boolean isTraining) {
        backward(null, retainGraph, isTraining);
    }

    /** {@inheritDoc} */
    @Override
    public void backward(NDArray outGrad, boolean retainGraph, boolean isTraining) {
        Pointer outGradHandle;
        if (outGrad != null) {
            MxNDArray outGradND = (MxNDArray) outGrad;
            outGradHandle = outGradND.getHandle();
        } else {
            outGradHandle = null;
        }

        JnaUtils.autogradBackwardExecute(
                1,
                getHandle(),
                outGradHandle,
                0,
                null,
                retainGraph ? 1 : 0,
                0,
                isTraining ? 1 : 0,
                null,
                null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray getGradient() {
        Pointer pointer = JnaUtils.getGradient(getHandle());
        return factory.create(pointer);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray argsort(int axis, boolean ascending) {
        MxOpParams params = new MxOpParams();
        params.addParam("axis", axis);
        params.addParam("is_ascend", ascending);
        params.setDataType(DataType.INT32);
        return factory.invoke("argsort", this, params);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray softmax(int[] axes) {
        if (axes.length != 1) {
            // TODO:
            throw new UnsupportedOperationException("Not implemented");
        }
        MxOpParams params = new MxOpParams();
        params.addParam("axis", axes[0]);
        return factory.invoke("softmax", this, params);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray softmax(int[] axes, double temperature) {
        if (axes.length != 1) {
            // TODO:
            throw new UnsupportedOperationException("Not implemented");
        }
        MxOpParams params = new MxOpParams();
        params.addParam("axis", axes[0]);
        params.addParam("temperature", temperature);
        return factory.invoke("softmax", this, params);
    }

    /** {@inheritDoc} */
    @Override
    public NDList split(int axis, boolean squeezeAxis) {
        MxOpParams params = new MxOpParams();
        params.addParam("num_outputs", size(axis));
        params.addParam("axis", axis);
        params.addParam("squeeze_axis", squeezeAxis);
        return new NDList(factory.invoke("split", new NDArray[] {this}, null, params));
    }

    /** {@inheritDoc} */
    @Override
    public NDList split(int axis, int numOutputs) throws IllegalArgumentException {
        MxOpParams params = new MxOpParams();
        params.addParam("num_outputs", numOutputs);
        params.addParam("axis", axis);
        return new NDList(factory.invoke("split", new NDArray[] {this}, null, params));
    }

    /** {@inheritDoc} */
    @Override
    public NDArray add(Number n) {
        PairList<String, String> pairList = new PairList<>();
        pairList.add("scalar", n.toString());
        return factory.invoke("_plus_scalar", this, pairList);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray addi(Number n) {
        PairList<String, String> pairList = new PairList<>();
        pairList.add("scalar", n.toString());
        return factory.invoke(
                        "_plus_scalar", new MxNDArray[] {this}, new MxNDArray[] {this}, pairList)[
                0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray add(NDArray other) {
        return factory.invoke(
                        "_plus", new MxNDArray[] {this, (MxNDArray) other}, null, new PairList<>())[
                0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray addi(NDArray other) {
        return factory.invoke(
                        "_plus",
                        new MxNDArray[] {this, (MxNDArray) other},
                        new MxNDArray[] {this},
                        new PairList<>())[0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray zerosLike() {
        return factory.invoke("zeros_like", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray onesLike() {
        return factory.invoke("ones_like", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isSparse() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray cumsumi(int dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray cumsum(int dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray assign(NDArray arr) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray assignIf(NDArray arr, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray replaceWhere(NDArray arr, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putScalar(long value, long... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putScalar(double value, long... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putScalar(float value, long... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putScalar(int value, long... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray eps(Number other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray eps(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray eq(Number other) {
        MxNDArray numbers = (MxNDArray) factory.zeros(new DataDesc(getShape()));
        numbers = (MxNDArray) numbers.add(other.floatValue());
        return this.eq(numbers);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray eq(NDArray other) {
        PairList<String, String> pairList = new PairList<>();
        MxNDArray[] src = new MxNDArray[] {this, (MxNDArray) other};
        return factory.invoke("_equal", src, null, pairList)[0];
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(NDArray other) {
        MxNDArray result = (MxNDArray) this.eq(other);
        if (result.nonzero() == result.size()){
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Number number) {
        MxNDArray result = (MxNDArray) this.eq(number);
        if (result.nonzero() == result.size()){
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray gt(Number other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray neq(Number other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray neq(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray gt(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray gte(Number other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray lte(Number other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray lt(Number other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray lt(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray isInfinite() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray isNaN() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray neg() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray negi() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdiv(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdivi(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsub(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsubi(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray div(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray divi(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mul(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray muli(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sub(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray subi(Number n) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdiv(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdivi(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsub(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsubi(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray div(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray divi(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mul(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray muli(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sub(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray subi(Number n, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray match(NDArray comp, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray match(Number comp, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray getWhere(NDArray comp, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray getWhere(Number comp, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putWhere(NDArray comp, NDArray put, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putWhere(Number comp, NDArray put, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putWhereWithMask(NDArray mask, NDArray put) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putWhereWithMask(NDArray mask, Number put) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putWhere(Number comp, Number put, Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray get(NDArray indices) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray get(List<List<Integer>> indices) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdiv(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdivi(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdiv(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rdivi(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsub(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsub(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsubi(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray rsubi(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray assign(Number value) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray putSlice(int slice, NDArray put) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray cond(Condition condition) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray repmat(int... shape) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray repeat(int dimension, long... repeats) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray getScalar(long i) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public double squaredDistance(NDArray other) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double distance2(NDArray other) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double distance1(NDArray other) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray put(List<List<Integer>> indices, NDArray element) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray put(NDArray indices, NDArray element) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray put(NDArray element, int... indices) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray put(int i, NDArray element) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mmul(NDArray other) {
        return null;
    }

    public FunctionInfo genericNDArrayFunctionInvoke(String opName, Map<String, Object> args) {
        FunctionInfo func = JnaUtils.op(opName);
        if (func == null) {
            throw new UnsupportedOperationException("Unsupported operation: " + opName);
        }

        return func;
    }

    /** {@inheritDoc} */
    @Override
    public float[] toFloatArray() {
        if (getDataType() != DataType.FLOAT32) {
            throw new IllegalStateException(
                    "DataType mismatch, Required float" + " Actual " + getDataType());
        }
        FloatBuffer fb = toByteBuffer().asFloatBuffer();
        float[] ret = new float[fb.remaining()];
        fb.get(ret);
        return ret;
    }

    public byte[] toByteArray() {
        ByteBuffer bb = toByteBuffer();
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        return buf;
    }

    /** {@inheritDoc} */
    @Override
    public int[] toIntArray() {
        if (getDataType() != DataType.INT32) {
            throw new IllegalStateException(
                    "DataType mismatch, Required int" + " Actual " + getDataType());
        }
        IntBuffer ib = toByteBuffer().asIntBuffer();
        int[] ret = new int[ib.remaining()];
        ib.get(ret);
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    public long[] toLongArray() {
        if (getDataType() != DataType.INT64) {
            throw new IllegalStateException(
                    "DataType mismatch, Required long" + " Actual " + getDataType());
        }
        LongBuffer lb = toByteBuffer().asLongBuffer();
        long[] ret = new long[lb.remaining()];
        lb.get(ret);
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    public double[] toDoubleArray() {
        if (getDataType() != DataType.FLOAT64) {
            throw new IllegalStateException(
                    "DataType mismatch, Required double" + " Actual " + getDataType());
        }
        DoubleBuffer db = toByteBuffer().asDoubleBuffer();
        double[] ret = new double[db.remaining()];
        db.get(ret);
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mmul(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray div(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray div(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mul(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mul(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sub(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sub(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mmuli(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mmuli(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray divi(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray divi(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray muli(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray muli(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray subi(NDArray other) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray subi(NDArray other, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray normmax(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number normmaxNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray norm2(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number norm2Number() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray norm1(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number norm1Number() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray std(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number stdNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray std(boolean biasCorrected, int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number stdNumber(boolean biasCorrected) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mean(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray mean(NDArray result, int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray amean(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number meanNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number ameanNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray var(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray var(boolean biasCorrected, int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number varNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray max(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray amax(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number maxNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number amaxNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray min(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray amin(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number minNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number aminNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sum(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sum(boolean keepDims, int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sum(NDArray result, int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number sumNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number entropyNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number shannonEntropyNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number logEntropyNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray entropy(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray shannonEntropy(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray logEntropy(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray getScalar(int... indices) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray getScalar(long... indices) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public long getLong(int... indices) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public long getLong(long... indices) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double getDouble(int... indices) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double getDouble(long... indices) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public float getFloat(int... indices) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public float getFloat(long... indices) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray dup() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray ravel() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray ravel(char order) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray slice(long i, int dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray slice(long i) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray reshape(char order, long... newShape) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray reshape(char order, int... newShape) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray reshape(long... newShape) {
        Pointer pointer = JnaUtils.reshape(getHandle(), newShape, false);
        return factory.create(pointer);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray reshape(int[] shape) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray swapAxes(int dimension, int with) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray transpose(int... dimensions) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray transposei(int... dimensions) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray broadcast(long... shape) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray broadcast(NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Object element() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equalsWithEps(Object o, double eps) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equalShapes(NDArray other) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray remainder(NDArray denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray remainder(NDArray denominator, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray remainder(Number denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray remainder(Number denominator, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray remainderi(NDArray denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray remainderi(Number denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray fmod(NDArray denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray fmod(NDArray denominator, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray fmod(Number denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray fmod(Number denominator, NDArray result) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray fmodi(NDArray denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray fmodi(Number denominator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray argMax(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number percentileNumber(Number percentile) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Number medianNumber() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray median(int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray percentile(Number percentile, int... dimension) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray toDense() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public int nonzero() {
        MxNDArray zeros = (MxNDArray) this.eq(0);
        MxNDArray sum =
                (MxNDArray)
                        factory.invoke(
                                "sum", (MxNDArray) this.eq(zeros).eq(zeros), new PairList<>());
        return (int) sum.toFloatArray()[0];
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray castTo(DataType dataType) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Matrix asMatrix() {
        if (!shape.isMatrix()) {
            throw new IllegalStateException("NDArray is not a matrix");
        }
        return new MxMatrix(this);
    }

    /** {@inheritDoc} */
    @Override
    public boolean all() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean any() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean none() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray like() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray ulike() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArrayEx getNDArrayInternal() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray abs() {
        return factory.invoke("_np_abs", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray cbrt() {
        return factory.invoke("_np_cbrt", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray floor() {
        return factory.invoke("_np_floor", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray ceil() {
        return factory.invoke("_np_ceil", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray round() {
        return factory.invoke("_np_round", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray trunc() {
        return factory.invoke("_np_trunc", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray exp() {
        return factory.invoke("_np_exp", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray log() {
        return factory.invoke("_np_log", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray log10() {
        return factory.invoke("_np_log10", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray log2() {
        return factory.invoke("_np_log2", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sin() {
        return factory.invoke("_np_sin", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray cos() {
        return factory.invoke("_np_cos", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray tan() {
        return factory.invoke("_np_tan", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray asin() {
        return factory.invoke("_np_arcsin", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray acos() {
        return factory.invoke("_np_arccos", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray atan() {
        return factory.invoke("_np_arctan", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray toDegrees() {
        return factory.invoke("_np_degrees", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray toRadians() {
        return factory.invoke("_np_radians", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray sinh() {
        return factory.invoke("_np_sinh", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray cosh() {
        return factory.invoke("_np_cosh", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray tanh() {
        return factory.invoke("_np_tanh", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray asinh() {
        return factory.invoke("_np_arcsinh", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray acosh() {
        return factory.invoke("_np_arccosh", this, null);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray atanh() {
        return factory.invoke("_np_arctanh", this, null);
    }

    private ByteBuffer toByteBuffer() {
        Shape sh = getShape();
        DataType dType = getDataType();
        int product = sh.size();
        int len = dType.getNumOfBytes() * product;
        ByteBuffer bb = ByteBuffer.allocateDirect(len);
        Pointer pointer = Native.getDirectBufferPointer(bb);
        JnaUtils.syncCopyToCPU(getHandle(), pointer, product);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb;
    }

    private void dump(StringBuilder sb, int depth) {
        Utils.pad(sb, ' ', depth);
        sb.append('[');
        int len = getShape().head();
        if (getShape().dimension() == 1) {
            int limit = Math.min(getShape().head(), MAX_PRINT_ITEMS);
            ByteBuffer buf = toByteBuffer().slice();
            buf.limit(limit * getDataType().getNumOfBytes());
            buf.order(ByteOrder.LITTLE_ENDIAN);
            sb.append(Utils.toCharSequence(buf, getDataType()));
            int remaining = getShape().head() - limit;
            if (remaining > 0) {
                sb.append(", ... ").append(remaining).append(" more");
            }
        } else {
            sb.append(LF);
            int limit = Math.min(len, MAX_PRINT_ROWS);
            for (int i = 0; i < limit; ++i) {
                try (MxNDArray nd = at(i)) {
                    nd.dump(sb, depth + 1);
                }
            }
            int remaining = len - limit;
            if (remaining > 0) {
                Utils.pad(sb, ' ', depth + 1);
                sb.append("... ").append(remaining).append(" more");
            }
            Utils.pad(sb, ' ', depth);
        }
        sb.append("],").append(LF);
    }

    public String dump() {
        StringBuilder sb = new StringBuilder(200);
        sb.append("ND: ")
                .append(getShape())
                .append(' ')
                .append(getContext())
                .append(' ')
                .append(getDataType())
                .append(LF);
        if (getShape().dimension() < MAX_DEPTH) {
            dump(sb, 0);
        } else {
            sb.append("[ Exceed max print dimension ]");
        }
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        if (Utils.DEBUG) {
            return dump();
        }
        return super.toString();
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        Pointer pointer = handle.getAndSet(null);
        if (pointer != null) {
            JnaUtils.freeNdArray(pointer);
            detach();
            factory = null;
        }
    }
}
