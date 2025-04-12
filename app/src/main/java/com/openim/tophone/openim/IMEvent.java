package com.openim.tophone.openim;


import android.util.Log;

import com.openim.tophone.stroage.VMStore;
import com.openim.tophone.utils.L;
import com.openim.tophone.utils.ToPhone;

import java.util.ArrayList;
import java.util.List;

import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnConnListener;
import io.openim.android.sdk.listener.OnConversationListener;
import io.openim.android.sdk.listener.OnFriendshipListener;
import io.openim.android.sdk.models.BlacklistInfo;
import io.openim.android.sdk.models.C2CReadReceiptInfo;
import io.openim.android.sdk.models.ConversationInfo;
import io.openim.android.sdk.models.FriendApplicationInfo;
import io.openim.android.sdk.models.FriendInfo;

import io.openim.android.sdk.models.GroupMessageReceipt;
import io.openim.android.sdk.models.KeyValue;
import io.openim.android.sdk.models.Message;

/// im事件 统一处理
public class IMEvent {
    private static final String TAG = "IMEvent";

    private static ToPhone toPhone = new ToPhone();

    private static IMEvent listener = null;
    private List<OnAdvanceMsgListener> advanceMsgListeners;
    private List<OnConversationListener> conversationListeners;
    private List<OnFriendshipListener> friendshipListeners;

    public void init() {
        advanceMsgListeners = new ArrayList<>();
        friendshipListeners = new ArrayList<>();
        conversationListeners = new ArrayList<>();
        //监听消息
        friendshipListener();
        advanceMsgListener();
        conversationListener();
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
            VMStore.get().connectionStatus.set(false);
        }

        @Override
        public void onConnectSuccess() {
            // 已经成功连接到服务器
            L.d(TAG, "已经成功连接到服务器");
            VMStore.get().isLoading.set(false);
            VMStore.get().connectionStatus.set(true);
        }

        @Override
        public void onConnecting() {
            // 正在连接到服务器，适合在 UI 上展示“正在连接”状态。
            L.d(TAG, "正在连接到服务器...");
            VMStore.get().isLoading.set(true);
            VMStore.get().connectionStatus.set(false);
        }

        @Override
        public void onKickedOffline() {
            // 当前用户被踢下线，此时可以 UI 提示用户“您已经在其他端登录了当前账号，是否重新登录？”
            L.d(TAG, "当前用户被踢下线");
            VMStore.get().isLoading.set(false);
            VMStore.get().connectionStatus.set(false);

        }

        @Override
        public void onUserTokenExpired() {
            // 登录票据已经过期，请使用新签发的 UserSig 进行登录。
            L.d(TAG, "登录票据已经过期");
            VMStore.get().connectionStatus.set(false);
        }

        @Override
        public void onUserTokenInvalid(String reason) {
            L.d(TAG, "user token invalid");
            VMStore.get().connectionStatus.set(false);
        }
    };


    private void friendshipListener() {
        OpenIMClient.getInstance().friendshipManager.setOnFriendshipListener(new OnFriendshipListener() {
            @Override
            public void onBlacklistAdded(BlacklistInfo u) {
                OnFriendshipListener.super.onBlacklistAdded(u);
                L.d(TAG,"friendshipListener2");
            }

            @Override
            public void onBlacklistDeleted(BlacklistInfo u) {
                OnFriendshipListener.super.onBlacklistDeleted(u);
                L.d(TAG,"friendshipListener3");
            }

            @Override
            public void onFriendApplicationAccepted(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationAccepted(u);
                L.d(TAG,"friendshipListener4");
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
                },u.getFromUserID(),"");
                L.d(TAG,"friendshipListener5");
            }

            @Override
            public void onFriendApplicationDeleted(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationDeleted(u);
                L.d(TAG,"friendshipListener6");
            }

            @Override
            public void onFriendApplicationRejected(FriendApplicationInfo u) {
                OnFriendshipListener.super.onFriendApplicationRejected(u);
                L.d(TAG,"friendshipListener7");
            }

            @Override
            public void onFriendInfoChanged(FriendInfo u) {
                OnFriendshipListener.super.onFriendInfoChanged(u);
                L.d(TAG,"friendshipListener8");
            }

            @Override
            public void onFriendAdded(FriendInfo u) {
                OnFriendshipListener.super.onFriendAdded(u);
                L.d(TAG,"friendshipListener9");
            }

            @Override
            public void onFriendDeleted(FriendInfo u) {
                OnFriendshipListener.super.onFriendDeleted(u);
                L.d(TAG,"friendshipListener10");
            }
        });
    }

    // 收到新消息，已读回执，消息撤回监听。

    private void advanceMsgListener() {
        OpenIMClient.getInstance().messageManager.setAdvancedMsgListener(new OnAdvanceMsgListener() {
            @Override
            public void onRecvNewMessage(Message msg) {
                //1、先处理数据 处理完成了再 已读
                String message = msg.getTextElem().getContent();
                L.d(TAG, "Receive the message : " + message);
                toPhone.handleMessage(message);

                OpenIMClient.getInstance().conversationManager.getOneConversation(new OnBase<ConversationInfo>() {
                    @Override
                    public void onSuccess(ConversationInfo data) {
                        OpenIMClient.getInstance().messageManager.markMessageAsReadByConID(null, data.getConversationID());
                    }
                }, msg.getSendID(), ConversationType.SINGLE_CHAT);

            }

            @Override
            public void onRecvC2CReadReceipt(List<C2CReadReceiptInfo> list) {
                // 消息被阅读回执，将消息标记为已读
                System.out.println("消息被阅读回执，将消息标记为已读");
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvC2CReadReceipt(list);
                }
            }

            @Override
            public void onRecvMessageExtensionsAdded(String msgID, List<KeyValue> list) {
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvMessageExtensionsAdded(msgID, list);
                }
            }

            @Override
            public void onMsgDeleted(Message message) {
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onMsgDeleted(message);
                }
            }

            @Override
            public void onRecvOfflineNewMessage(List<Message> msg) {
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvOfflineNewMessage(msg);
                }
            }

            @Override
            public void onRecvGroupMessageReadReceipt(GroupMessageReceipt groupMessageReceipt) {
                // 消息被阅读回执，将消息标记为已读
                Log.d(TAG, "onRecvGroupMessageReadReceipt, advanceMsgListeners:" + advanceMsgListeners);
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvGroupMessageReadReceipt(groupMessageReceipt);
                }
            }

            @Override
            public void onRecvOnlineOnlyMessage(String s) {
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvOnlineOnlyMessage(s);
                }
            }
        });
    }

    // 会话新增或改变监听
    private void conversationListener() {
        OpenIMClient.getInstance().conversationManager.setOnConversationListener(new OnConversationListener() {
            @Override
            public void onConversationChanged(List<ConversationInfo> list) {

                // 已添加的会话发生改变
                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onConversationChanged(list);
                }
            }

            @Override
            public void onNewConversation(List<ConversationInfo> list) {
                // 新增会话
                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onNewConversation(list);
                }
            }

            @Override
            public void onSyncServerFailed(boolean reinstalled) {
                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onSyncServerFailed(reinstalled);
                }
            }

            @Override
            public void onSyncServerFinish(boolean reinstalled) {
                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onSyncServerFinish(reinstalled);
                }
                Log.d(TAG, "onSyncServerFinish reinstalled:" + reinstalled);

            }

            @Override
            public void onSyncServerProgress(long progress) {
                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onSyncServerProgress(progress);
                }
            }

            @Override
            public void onSyncServerStart(boolean reinstalled) {
                Log.d(TAG, "onSyncServerStart reinstalled:" + reinstalled);
                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onSyncServerStart(reinstalled);
                }
            }

            @Override
            public void onTotalUnreadMessageCountChanged(int i) {
                // 未读消息数发送变化

                for (OnConversationListener onConversationListener : conversationListeners) {
                    onConversationListener.onTotalUnreadMessageCountChanged(i);
                }
            }
        });
    }
}


