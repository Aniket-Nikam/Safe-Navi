package com.safenavi.app.safety.model;

/** A provider instruction anchored to this exact route's geometry. */
public final class NavigationStep {
    public final int shapeIndex;
    public final String instruction;
    public NavigationStep(int shapeIndex,String instruction) {
        this.shapeIndex=shapeIndex;
        this.instruction=instruction;
    }
}
