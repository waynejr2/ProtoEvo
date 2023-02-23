package com.protoevo.biology.nn;

import com.protoevo.utils.SerializableFunction;
import com.protoevo.utils.Utils;

import java.io.Serializable;
import java.util.function.Function;

public class ActivationFn implements Serializable, Function<Float, Float> {
    public static final long serialVersionUID = 1L;

    public static final ActivationFn SIGMOID = new ActivationFn(z -> 1 / (1 + (float) Math.exp(-z)), "Sigmoid");
    public static final ActivationFn LINEAR = new ActivationFn(z -> z, "Linear");
    public static final ActivationFn TANH = new ActivationFn(z -> (float) Math.tanh(z), "Tanh");
    public static final ActivationFn STEP = new ActivationFn(z -> z > 0 ? 1f : 0f, "Step");
    public static final ActivationFn RELU = new ActivationFn(z -> z > 0 ? z : 0f, "ReLu");

    public static ActivationFn getOutputMapper(float min, float max) {
        return new ActivationFn(z -> Utils.linearRemap(z, -1, 1, min, max));
    }

    public static ActivationFn getInputMapper(float min, float max) {
        return new ActivationFn(z -> Utils.linearRemap(z, min, max, -1, 1));
    }

    public static ActivationFn getBooleanInputMapper() {
        return new ActivationFn(z -> z > 0 ? 1f : -1f);
    }

    public static ActivationFn[] activationFunctions = new ActivationFn[]{
            SIGMOID, LINEAR, TANH, STEP, RELU
    };

    private final SerializableFunction<Float, Float> function;
    private String name;

    public ActivationFn(SerializableFunction<Float, Float> function) {
        this.function = function;
    }

    public ActivationFn(SerializableFunction<Float, Float> function, String name) {
        this.function = function;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ActivationFn)
            return name.equals(((ActivationFn) obj).name);
        return false;
    }

    public static ActivationFn randomActivation() {
        return activationFunctions[(int) (Math.random() * activationFunctions.length)];
    }

    @Override
    public Float apply(Float z) {
        return function.apply(z);
    }
}
