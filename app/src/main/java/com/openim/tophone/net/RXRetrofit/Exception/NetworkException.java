package com.openim.tophone.net.RXRetrofit.Exception;

import com.openim.tophone.R;
import com.openim.tophone.base.BaseApp;

public class NetworkException extends Exception {

    private static final long serialVersionUID = 114946L;


    public NetworkException() {
        super(BaseApp.inst().getString(R.string.network_unavailable_tips));
    }

    public NetworkException(String message) {
        super(message);
    }


    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }

    public NetworkException(Throwable cause) {
        super(cause);
    }


}
