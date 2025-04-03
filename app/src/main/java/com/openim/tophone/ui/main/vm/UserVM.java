package com.openim.tophone.ui.main.vm;

import com.openim.tophone.base.BaseViewModel;
import com.openim.tophone.base.vm.State;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.net.RXRetrofit.NetObserver;
import com.openim.tophone.net.RXRetrofit.Parameter;
import com.openim.tophone.repository.OneselfService;
import com.openim.tophone.repository.OpenIMService;
import com.openim.tophone.utils.L;

import java.util.HashMap;

public class UserVM extends BaseViewModel {


    public State<Integer> getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(State<Integer> accountStatus) {
        this.accountStatus = accountStatus;
    }

    public State<String> getAccountID() {
        return accountID;
    }

    public void setAccountID(State<String> accountID) {
        this.accountID = accountID;
    }

    public State<String> accountID = new State<>("");
    public State<Integer> accountStatus = new State<>(0);

    public State<Boolean> getPhonePermissions() {
        return phonePermissions;
    }

    public void setPhonePermissions(State<Boolean> phonePermissions) {
        this.phonePermissions = phonePermissions;
    }

    public State<Boolean> getSmsPermissions() {
        return smsPermissions;
    }

    public void setSmsPermissions(State<Boolean> smsPermissions) {
        this.smsPermissions = smsPermissions;
    }


    public State<Boolean> getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(State<Boolean> connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public State<Boolean> phonePermissions = new State<>(false);
    public State<Boolean> smsPermissions = new State<>(false);
    public State<Boolean> connectionStatus = new State<>(false);

    public State<Boolean> isLoading = new State<>(false);


    private String TAG="UserVM";

    public void connect() {
        isLoading.setValue(true);
        connectionStatus.setValue(true);
    }

    public void checkIfUserExists(String email) {
        phonePermissions.setValue(true);

        Parameter parameter = OneselfService.buildPagination(1, 1);
        parameter.add("keyword", email).add("normal", 1);

        N.API(OneselfService.class)
                .searchUser(parameter.buildJsonBody())
                .map(OpenIMService.turn(HashMap.class)).compose(N.IOMain()).subscribe(new NetObserver<>(getContext()) {
                    @Override
                    public void onSuccess(HashMap hashMap) {
                        L.d(TAG, "current account: " + email + " Total number:" + hashMap.get("total"));
                        Integer total = (Integer) hashMap.get("total");
                        accountStatus.setValue(total);
                        if (total == 0) {
                            L.d(TAG,"prepare to register account: "+email+"!");
                            //如果没有账号那就注册！
                            Parameter registerParameter = new Parameter();
                            HashMap user =new HashMap();
                            user.put("nickname","ToP");
                            user.put("areaCode","+886");
                            user.put("email",email);
                            user.put("password","516f00c9229200d6ce526991cdfdd959");
                            user.put("birth",00);
                            registerParameter.add("user",user).add("platform",10).add("verifyCode","666666");

                            N.API(OpenIMService.class)
                                    .register(registerParameter.buildJsonBody())
                                    .map(OpenIMService.turn(HashMap.class)).compose(N.IOMain()).subscribe(new NetObserver<>(getContext()) {
                                                                                                              @Override
                                                                                                              public void onSuccess(HashMap o) {
                                                                                                                  L.d(TAG, "register success" + o.toString());
                                                                                                                  accountStatus.setValue(1);
                                                                                                              }
                                                                                                          }
                                    );
                        }
                    }
                });
    }
}
