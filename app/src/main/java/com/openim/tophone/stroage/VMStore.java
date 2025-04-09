package com.openim.tophone.stroage;

import com.openim.tophone.ui.main.vm.UserVM;

public class VMStore {
    private static UserVM userVM;

    // 初始化 ViewModel
    public static void init(UserVM vm) {
        userVM = vm;
    }

    // 获取全局 ViewModel
    public static UserVM get() {
        if (userVM == null) {
            throw new IllegalStateException("VMStore is not initialized yet.");
        }
        return userVM;
    }
}
