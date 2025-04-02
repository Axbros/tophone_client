package com.openim.tophone.ui.main.vm;

import com.openim.tophone.base.BaseViewModel;
import com.openim.tophone.base.vm.State;
import com.openim.tophone.net.RXRetrofit.N;
import com.openim.tophone.net.RXRetrofit.NetObserver;
import com.openim.tophone.net.RXRetrofit.Parameter;
import com.openim.tophone.repository.OneselfService;
import com.openim.tophone.repository.OpenIMService;

import java.util.HashMap;

public class UserVM extends BaseViewModel {


    public State<String> getAccountID() {
        return accountID;
    }

    public void setAccountID(State<String> accountID) {
        this.accountID = accountID;
    }

    public State<String> getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(State<String> accountStatus) {
        this.accountStatus = accountStatus;
    }

    public State<String> accountID = new State<>("");
    public State<String> accountStatus=new State<>("");

    public void checkIfUserExists(String email){

        Parameter parameter=OneselfService.buildPagination(1,1);
        parameter.add("keyword",email).add("normal",1);

        N.API(OneselfService.class)
                .searchUser(parameter.buildJsonBody())
                .map(OpenIMService.turn(HashMap.class)).compose(N.IOMain()).subscribe(new NetObserver<HashMap>(getContext()){
                    @Override
                    public void onSuccess(HashMap hashMap) {
                        if(hashMap.get("total")!=null && (int)hashMap.get("total")>0){
                            accountStatus.postValue("Registered");
                        }else{
                            accountStatus.postValue("Not Registered");
                        }
                    }


                });
    }
}
