package com.pureqml.android.runtime;

public interface IExecutionEnvironment {

    Element getElementById(long id);

    void putElement(long id, Element element);

    void removeElement(long id);
}
