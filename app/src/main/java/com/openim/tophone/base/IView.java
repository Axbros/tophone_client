package com.openim.tophone.base;

/**
 * View
 */
public interface IView {
    void onError(String error);

    void onSuccess(Object body);

    void toast(String tips);

    void close();
}
