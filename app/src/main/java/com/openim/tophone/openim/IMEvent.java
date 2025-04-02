package com.openim.tophone.openim;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.openim.tophone.utils.L;
import java.util.ArrayList;
import java.util.List;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnAdvanceMsgListener;
import io.openim.android.sdk.listener.OnConnListener;
import io.openim.android.sdk.listener.OnConversationListener;
import io.openim.android.sdk.listener.OnFriendshipListener;
import io.openim.android.sdk.listener.OnGroupListener;
import io.openim.android.sdk.listener.OnUserListener;
import io.openim.android.sdk.models.BlacklistInfo;
import io.openim.android.sdk.models.C2CReadReceiptInfo;
import io.openim.android.sdk.models.FriendApplicationInfo;
import io.openim.android.sdk.models.FriendInfo;
import io.openim.android.sdk.models.GroupApplicationInfo;
import io.openim.android.sdk.models.GroupInfo;
import io.openim.android.sdk.models.GroupMembersInfo;
import io.openim.android.sdk.models.GroupMessageReceipt;
import io.openim.android.sdk.models.KeyValue;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.RevokedInfo;

///im事件 统一处理
public class IMEvent {
    private static final String TAG = "IMEvent";
    private static IMEvent listener = null;
    private List<OnConnListener> connListeners;
    private List<OnUserListener> userListeners;
    private List<OnAdvanceMsgListener> advanceMsgListeners;
    private List<OnConversationListener> conversationListeners;
    private List<OnGroupListener> groupListeners;
    private List<OnFriendshipListener> friendshipListeners;

    public void init() {
        connListeners = new ArrayList<>();

        advanceMsgListeners = new ArrayList<>();
        friendshipListeners = new ArrayList<>();
        groupListeners = new ArrayList<>();
        //监听消息
        advanceMsgListener();
        //自动同意添加好友
        friendshipListener();
        //群组监听 更换甲方
        groupListeners();
    }

    public static synchronized IMEvent getInstance() {
        if (null == listener) listener = new IMEvent();
        return listener;
    }

    //连接事件
    public void addConnListener(OnConnListener onConnListener) {
        if (!connListeners.contains(onConnListener)) {
            connListeners.add(onConnListener);
        }
    }

    public void removeConnListener(OnConnListener onConnListener) {
        connListeners.remove(onConnListener);
    }



    // 收到新消息，已读回执，消息撤回监听。
    public void addAdvanceMsgListener(OnAdvanceMsgListener onAdvanceMsgListener) {
        if (!advanceMsgListeners.contains(onAdvanceMsgListener)) {
            advanceMsgListeners.add(onAdvanceMsgListener);
        }
    }

    public void removeAdvanceMsgListener(OnAdvanceMsgListener onAdvanceMsgListener) {
        advanceMsgListeners.remove(onAdvanceMsgListener);
    }

    // 群组关系发生改变监听
    public void addGroupListener(OnGroupListener onGroupListener) {
        if (!groupListeners.contains(onGroupListener)) {
            groupListeners.add(onGroupListener);
        }
    }

    public void removeGroupListener(OnGroupListener onGroupListener) {
        groupListeners.remove(onGroupListener);
    }

    // 好友关系发生改变监听
    public void addFriendListener(OnFriendshipListener onFriendshipListener) {
        if (!friendshipListeners.contains(onFriendshipListener)) {
            friendshipListeners.add(onFriendshipListener);
        }
    }

    public void removeFriendListener(OnFriendshipListener onFriendshipListener) {
        friendshipListeners.remove(onFriendshipListener);
    }


    //连接事件
    public OnConnListener connListener = new OnConnListener() {

        @Override
        public void onConnectFailed(int code, String error) {
            // 连接服务器失败，可以提示用户当前网络连接不可用
            L.d(TAG, "连接服务器失败(" + error + ")");
            for (OnConnListener onConnListener : connListeners) {
                onConnListener.onConnectFailed(code, error);
            }
        }

        @Override
        public void onConnectSuccess() {
            // 已经成功连接到服务器
            L.d(TAG, "已经成功连接到服务器");
            for (OnConnListener onConnListener : connListeners) {
                onConnListener.onConnectSuccess();
            }
        }

        @Override
        public void onConnecting() {
            // 正在连接到服务器，适合在 UI 上展示“正在连接”状态。
            L.d(TAG, "正在连接到服务器...");
            for (OnConnListener onConnListener : connListeners) {
                onConnListener.onConnecting();
            }
        }

        @Override
        public void onKickedOffline() {
            // 当前用户被踢下线，此时可以 UI 提示用户“您已经在其他端登录了当前账号，是否重新登录？”
            L.d(TAG, "当前用户被踢下线");
//            Toast.makeText(BaseApp.inst(),
//                    BaseApp.inst().getString(io.openim.android.ouicore.R.string.kicked_offline_tips),
//                    Toast.LENGTH_SHORT).show();
            for (OnConnListener onConnListener : connListeners) {
                onConnListener.onKickedOffline();
            }
        }

        @Override
        public void onUserTokenExpired() {
            // 登录票据已经过期，请使用新签发的 UserSig 进行登录。
            L.d(TAG, "登录票据已经过期");
//            Toast.makeText(BaseApp.inst(),
//                    BaseApp.inst().getString(io.openim.android.ouicore.R.string.token_expired),
//                    Toast.LENGTH_SHORT).show();
            for (OnConnListener onConnListener : connListeners) {
                onConnListener.onUserTokenExpired();
            }
        }

        @Override
        public void onUserTokenInvalid(String reason) {
//            Toast.makeText(BaseApp.inst(),
//                    BaseApp.inst().getString(R.string.token_invalid),
//                    Toast.LENGTH_SHORT).show();
            for (OnConnListener onConnListener : connListeners) {
                onConnListener.onUserTokenInvalid(reason);
            }
        }
    };


    // 群组关系发生改变监听
    private void groupListeners() {
        OpenIMClient.getInstance().groupManager.setOnGroupListener(new OnGroupListener() {
            @Override
            public void onGroupApplicationAccepted(GroupApplicationInfo info) {
                // 发出或收到的组申请被接受
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupApplicationAccepted(info);
                }
            }

            @Override
            public void onGroupApplicationAdded(GroupApplicationInfo info) {
                // 发出或收到的组申请有新增
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupApplicationAdded(info);
                }
            }

            @Override
            public void onGroupApplicationDeleted(GroupApplicationInfo info) {
                // 发出或收到的组申请被删除
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupApplicationDeleted(info);
                }
            }

            @Override
            public void onGroupApplicationRejected(GroupApplicationInfo info) {
                // 发出或收到的组申请被拒绝
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupApplicationRejected(info);
                }
            }

            @Override
            public void onGroupDismissed(GroupInfo info) {
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupDismissed(info);
                }
            }

            @Override
            public void onGroupInfoChanged(GroupInfo info) {
                // 组资料变更
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupInfoChanged(info);
                }
            }

            @Override
            public void onGroupMemberAdded(GroupMembersInfo info) {
                // 组成员进入
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupMemberAdded(info);
                }
            }

            @Override
            public void onGroupMemberDeleted(GroupMembersInfo info) {
                // 组成员退出
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupMemberDeleted(info);
                }
            }

            @Override
            public void onGroupMemberInfoChanged(GroupMembersInfo info) {
                // 组成员信息发生变化
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onGroupMemberInfoChanged(info);
                }
            }

            @Override
            public void onJoinedGroupAdded(GroupInfo info) {
                // 创建群： 初始成员收到；邀请进群：被邀请者收到
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onJoinedGroupAdded(info);
                }
            }

            @Override
            public void onJoinedGroupDeleted(GroupInfo info) {
                // 退出群：退出者收到；踢出群：被踢者收到
                for (OnGroupListener onGroupListener : groupListeners) {
                    onGroupListener.onJoinedGroupDeleted(info);
                }
            }
        });
    }



    // 好关系发生变化监听
    private void friendshipListener() {
        OpenIMClient.getInstance().friendshipManager.setOnFriendshipListener(new OnFriendshipListener() {
            @Override
            public void onBlacklistAdded(BlacklistInfo u) {
                // 拉入黑名单
            }

            @Override
            public void onBlacklistDeleted(BlacklistInfo u) {
                // 从黑名单删除
            }

            @Override
            public void onFriendApplicationAccepted(FriendApplicationInfo u) {
                // 发出或收到的好友申请已同意
                for (OnFriendshipListener friendshipListener : friendshipListeners) {
                    friendshipListener.onFriendApplicationAccepted(u);
                }
            }

            @Override
            public void onFriendApplicationAdded(FriendApplicationInfo u) {
                // 发出或收到的好友申请被添加
                for (OnFriendshipListener friendshipListener : friendshipListeners) {
                    friendshipListener.onFriendApplicationAdded(u);
                }
            }

            @Override
            public void onFriendApplicationDeleted(FriendApplicationInfo u) {
                // 发出或收到的好友申请被删除
                for (OnFriendshipListener friendshipListener : friendshipListeners) {
                    friendshipListener.onFriendApplicationDeleted(u);
                }
            }

            @Override
            public void onFriendApplicationRejected(FriendApplicationInfo u) {
                // 发出或收到的好友申请被拒绝
                for (OnFriendshipListener friendshipListener : friendshipListeners) {
                    friendshipListener.onFriendApplicationRejected(u);
                }
            }

            @Override
            public void onFriendInfoChanged(FriendInfo u) {
                // 朋友的资料发生变化
                for (OnFriendshipListener friendshipListener : friendshipListeners) {
                    friendshipListener.onFriendInfoChanged(u);
                }
            }

            @Override
            public void onFriendAdded(FriendInfo u) {
                // 好友被添加
            }

            @Override
            public void onFriendDeleted(FriendInfo u) {
                // 好友被删除
            }
        });
    }

    // 收到新消息，已读回执，消息撤回监听。
    private void advanceMsgListener() {
        OpenIMClient.getInstance().messageManager.setAdvancedMsgListener(new OnAdvanceMsgListener() {
            @Override
            public void onRecvNewMessage(Message msg) {
                Log.d(TAG, "onRecvNewMessage, advanceMsgListeners:" + advanceMsgListeners);
                //取消播放音乐和推送消息
                // 收到新消息，界面添加新消息
                //TODO 自定义指令
//                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
//                    onAdvanceMsgListener.onRecvNewMessage(msg);
//                }
            }

            @Override
            public void onRecvC2CReadReceipt(List<C2CReadReceiptInfo> list) {
                // 消息被阅读回执，将消息标记为已读
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvC2CReadReceipt(list);
                }
            }

            @Override
            public void onRecvMessageRevokedV2(RevokedInfo info) {
                // 消息成功撤回，从界面移除消息
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvMessageRevokedV2(info);
                }
            }

            @Override
            public void onRecvMessageExtensionsChanged(String msgID, List<KeyValue> list) {
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvMessageExtensionsChanged(msgID, list);
                }
            }

            @Override
            public void onRecvMessageExtensionsDeleted(String msgID, List<String> list) {
                for (OnAdvanceMsgListener onAdvanceMsgListener : advanceMsgListeners) {
                    onAdvanceMsgListener.onRecvMessageExtensionsDeleted(msgID, list);
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

    /**
     * 用户状态变化
     *
     * @param onUserListener
     */
    public void removeUserListener(OnUserListener onUserListener) {
        userListeners.remove(onUserListener);
    }

    public void addUserListener(OnUserListener onUserListener) {
        if (!userListeners.contains(onUserListener)) userListeners.add(onUserListener);
    }
}


