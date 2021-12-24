package de.fernflower.main.providers;

import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;

public interface IJavadocProvider {
    String PROPERTY_NAME = "jds";

    String getClassDoc(StructClass structClass);
    String getMethodDoc(StructClass structClass, StructMethod structMethod);
    String getFieldDoc(StructClass structClass, StructField structField);
}
