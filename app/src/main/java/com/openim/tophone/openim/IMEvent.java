package com.openim.tophone.openim;

import com.openim.tophone.openim.entity.LoginCertificate;
import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.utils.Constants;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.OpenIMUtils;
import com.openim.tophone.utils.ToPhone;

import java.util.ArrayList;
import java.util.List;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnConnListener;
import io.openim.android.sdk.listener.OnFriendshipListener;
import io.openim.android.sdk.listener.OnGroupListener;
import io.openim.android.sdk.models.BlacklistInfo;
import io.openim.android.sdk.models.ConversationInfo;
import io.openim.android.sdk.models.FriendApplicationInfo;
import io.openim.android.sdk.models.FriendInfo;

import io.openim.android.sdk.models.Message;


/// im事件 统一处理
public class IMEvent {
    private static final String TAG = "IMEvent";

    private static ToPhone toPhone = new ToPhone();

    private static IMEvent listener = null;
    private List<OnAdvanceMsgListener> advanceMsgListeners;
    private List<OnFriendshipListener> friendshipListeners;

    public void init() {
        advanceMsgListeners = new ArrayList<>();
        friendshipListeners = new ArrayList<>();
        //监听消息
        friendshipListener();
        advanceMsgListener();
    }


    public static synchronized IMEvent getInstance() {
        if (null == listener) listener = new IMEvent();
        return listener;
    }


    //连接事件
    public OnConnListener connListener = new OnConnListener() {

        @Override
        public void onConnectFailed(int code, String error) {
            // 连接服务器失败，可以提示用户当前网络连接不可用
            L.d(TAG, "连接服务器失败(" + error + ")");
            VMStore.get().connectionStatus.setValue(false);
        }

        @Override
        public void onConnectSuccess() {
            // 已经成功连接到服务器
            L.d(TAG, "已经成功连接到服务器");
            VMStore.get().isLoading.setValue(false);
            VMStore.get().connectionStatus.setValue(true);
        }

        @Override
        public void onConnecting() {
            // 正在连接到服务器，适合在 UI 上展示“正在连接”状态。
            L.d(TAG, "正在连接到服务器：" + Constants.getAppAuthUrl());
            VMStore.get().isLoading.setValue(true);
            VMStore.get().connectionStatus.setValue(false);
        }

        @Override
        public void onKickedOffline() {
            // 当前用户被踢下线，此时可以 UI 提示用户“您已经在其他端登录了当前账号，是否重新登录？”
            L.d(TAG, "当前用户被踢下线");
            LoginCertificate.clear();
            VMStore.get().isLoading.setValue(false);
            VMStore.get().connectionStatus.setValue(false);

        }

        @Override
        public void onUserTokenExpired() {
            // 登录票据已经过期，请使用新签发的 UserSig 进行登录。
            L.d(TAG, "登录票据已经过期");
            LoginCertificate.clear();
            VMStore.get().connectionStatus.setValue(false);
        }

        @Override
        public void onUserTokenInvalid(String reason) {
            L.d(TAG, "user token invalid");
            LoginCertificate.clear();
            VMStore.get().connectionStatus.setValue(false);
        }
    };


    private void friendshipListener() {
        OpenIMClient.getInstance().friendshipManager.setOnFriendshipListener(new OnFriendshipListener() {
            @Override
            public void onBlacklistAdded(BlacklistInfo u) {
                OnFriendshipListener.super.onBlacklistAdded(u);
                L.d(TAG, "friendshipListener2");
            }

            @Override
            public void onBlacklistDeleted(BlacklistInfo u) {
                OnFriendshipListener.super.onBlacklistDeleted(u);
                L.d(TAG, "friendshipListener3");
            }

            @Override
            public void onFriendApplicationAccepted(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationAccepted(u);
                L.d(TAG, "friendshipListener4");
            }

            @Override
            public void onFriendApplicationAdded(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationAdded(u);
                OpenIMClient.getInstance().friendshipManager.acceptFriendApplication(new OnBase<String>() {
                    @Override
                    public void onError(int code, String error) {
                        OnBase.super.onError(code, error);
                    }

                    @Override
                    public void onSuccess(String data) {
                        OnBase.super.onSuccess(data);
                    }
                }, u.getFromUserID(), "");
                L.d(TAG, "friendshipListener5");
            }

            @Override
            public void onFriendApplicationDeleted(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationDeleted(u);
                L.d(TAG, "friendshipListener6");
            }

            @Override
            public void onFriendApplicationRejected(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationRejected(u);
                L.d(TAG, "friendshipListener7");
            }

            @Override
            public void onFriendInfoChanged(FriendInfo u) {
                OnFriendshipListener.super.onFriendInfoChanged(u);
                L.d(TAG, "friendshipListener8");
            }

            @Override
            public void onFriendAdded(FriendInfo u) {
                OnFriendshipListener.super.onFriendAdded(u);
                L.d(TAG, "friendshipListener9");
            }

            @Override
            public void onFriendDeleted(FriendInfo u) {
                OnFriendshipListener.super.onFriendDeleted(u);
                L.d(TAG, "friendshipListener10");
            }
        });
    }

    // 收到新消息，已读回执，消息撤回监听。

    private void advanceMsgListener() {
        OpenIMClient.getInstance().messageManager.setAdvancedMsgListener(new OnAdvanceMsgListener() {
            @Override
            public void onRecvNewMessage(Message msg) {
                //1、先处理数据 处理完成了再 已读
//                msgContentType:1507	群主更换通知
                Integer msgContentType = msg.getContentType();
                L.d(TAG,"received the message,message content type is : "+msgContentType);
                switch (msgContentType) {
                    case 1507:
                        L.d(TAG, "Group Owner Changed,Group ID:" + msg.getGroupID());
                        OpenIMUtils.updateGroupInfo();
                        break;
                    case 1520:
                        L.d(TAG, "Group Name Changed,Group ID:" + msg.getGroupID());
                        OpenIMUtils.updateGroupInfo();
                        break;
                    case 101:
                        String message = msg.getTextElem().getContent();
                        L.d(TAG, "Receive the message : " + message);
                        toPhone.handleMessage(message);
                        OpenIMClient.getInstance().conversationManager.getOneConversation(new OnBase<ConversationInfo>() {
                            @Override
                            public void onSuccess(ConversationInfo data) {
                                OpenIMClient.getInstance().messageManager.markMessageAsReadByConID(null, data.getConversationID());
                            }
                        }, msg.getSendID(), ConversationType.SINGLE_CHAT);
                        break;
                }
            }
        });
    }


    // 群组关系发生改变监听

}


