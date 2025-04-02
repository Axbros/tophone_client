package com.openim.tophone.openim;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.LoginStatus;

public class IMUtil {

    /**
     * 已登录或登录中
     *
     * @return
     */
    public static boolean isLogged() {
        long status = OpenIMClient.getInstance().getLoginStatus();
        return status == LoginStatus.Logging || status == LoginStatus.Logged;
    }


}
