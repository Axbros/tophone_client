package com.openim.tophone.utils;


import android.annotation.SuppressLint;

import com.openim.tophone.base.BaseApp;
import com.openim.tophone.openim.IM;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.GroupInfo;
import io.openim.android.sdk.models.PublicUserInfo;

public class OpenIMUtils {
    public final static String TAG = "OpenIMUtils";
    public static void updateGroupInfo(){
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        OpenIMClient.getInstance().groupManager.getJoinedGroupList(new OnBase<List<GroupInfo>>() {
            @Override
            public void onError(int code, String error) {
                OnBase.super.onError(code, error);
                L.e(TAG,"getJoinedGroupList error!");
            }

            @Override
            public void onSuccess(List<GroupInfo> data) {

                OnBase.super.onSuccess(data);
                if(data.size()==1){
                    GroupInfo groupInfo = data.get(0);
                    String groupName=groupInfo.getGroupName();
                    String ownerUserId=groupInfo.getOwnerUserID();
                    List<String> userIdList = new ArrayList<>();
                    userIdList.add(ownerUserId);
                    OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
                        @Override
                        public void onError(int code, String error) {
                            // 处理错误信息，这里简单打印错误信息
                            L.d(TAG,"获取用户信息失败，错误码: " + code + ", 错误信息: " + error);

                        }
                        @SuppressLint("CommitPrefEdits")
                        @Override
                        public void onSuccess(List<PublicUserInfo> data) {
                            // 请求成功，将获取到的用户信息赋值给 result
                            MainActivity.sp.edit().putString(Constants.getGroupOwnerKey(),ownerUserId).apply();
                            String groupOwner = data.get(0).getNickname();
                            VMStore.get().groupInfoLabel.setValue(groupName+"("+groupOwner+")");
                            L.d(TAG,"Get Group Info Success！Group Name:"+groupName+" Group Owner:"+groupOwner+" Group Owner Id:"+ownerUserId);
                        }
                    }, userIdList);


                }else{
                    VMStore.get().groupInfoLabel.setValue("Group Joined is not equal 1 ! the size is : "+data.size());
                }
                L.d(TAG,"getJoinedGroupList Success!");
            }

        });
    }


}
