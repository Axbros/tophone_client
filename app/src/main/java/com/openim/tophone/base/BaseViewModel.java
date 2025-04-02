package com.openim.tophone.base;


import android.content.Context;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import com.openim.tophone.base.vm.injection.BaseVM;

@Deprecated
public class BaseViewModel<T extends IView> extends BaseVM {
    public WeakReference<Context> context;
    public WeakReference<T> IView;
    protected boolean isDestroy;
    public Context getContext() {
        return context.get();
    }

    public void setContext(Context context) {
        if (null != this.context) {
            this.context.clear();
            this.context = null;
        }
        this.context = new WeakReference<>(context);
    }

    public void setIView(T iView) {
        this.IView = new WeakReference<T>(iView);
    }



    //视图销毁时
    protected void releaseRes() {
        isDestroy = true;
    }

    //视图已构建
    protected void viewCreate() {
        isDestroy = false;
    }



    protected void viewResume() {
        isDestroy = false;
    }

}
