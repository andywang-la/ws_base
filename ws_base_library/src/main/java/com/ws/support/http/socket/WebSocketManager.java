package com.ws.support.http.socket;

import android.os.Handler;
import android.util.Log;
import com.orhanobut.logger.Logger;
import com.ws.support.utils.StringUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * 描述信息
 *
 * @author ws
 * 2020/9/18 17:02
 * 修改人：ws
 */
// An highlighted block
public final class WebSocketManager {
    //private final static String TAG = WebSocketManager.class.getSimpleName();
    private final static int MAX_NUM = 5;       // 最大重连数
    private final static int MILLIS = 5000;     // 重连间隔时间，毫秒
    private static WebSocketManager mInstance = null;

    private OkHttpClient    client;
    private Request         request;
    private IReceiveMessage receiveMessage;
    private WebSocket       mWebSocket;

    private boolean isConnect = false;
    private int connectNum = 0;
    //心跳包发送时间计时
    private              long    sendTime        = 0L;
    // 发送心跳包
    private              Handler mHandler        = new Handler();
    // 每隔40秒发送一次心跳包，检测连接没有断开
    private static final long    HEART_BEAT_RATE = 40 * 1000;

    private String WSURL = "你的Websocket地址";

    private WebSocketManager() {
    }

    public static WebSocketManager getInstance() {
        if (null == mInstance) {
            synchronized (WebSocketManager.class) {
                if (mInstance == null) {
                    mInstance = new WebSocketManager();
                }
            }
        }
        return mInstance;
    }

    /**
     * 释放单例, 及其所引用的资源
     */
    public static void release() {
        try {
            if (mInstance != null) {
                mInstance = null;
            }
        } catch (Exception e) {
            Log.e("WebSocketManager", "release : " + e.toString());
        }
    }

    public void init(IReceiveMessage message) {
        client = new OkHttpClient.Builder()
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .build();
        request = new Request.Builder().url(WSURL).build();
        receiveMessage = message;
        connect();
    }

    /**
     * 连接
     */
    public void connect() {
        if (isConnect()) {
            Logger.i("WebSocket 已经连接！");
            return;
        }
        client.newWebSocket(request, createListener());
    }

    /**
     * 重连
     */
    public void reconnect() {
        if (connectNum <= MAX_NUM) {
            try {
                Thread.sleep(MILLIS);
                connect();
                connectNum++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Logger.i( "reconnect over " + MAX_NUM + ",please check url or network");
        }
    }

    /**
     * 是否连接
     */
    public boolean isConnect() {
        return mWebSocket != null && isConnect;
    }


    // 发送心跳包
    private Runnable heartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - sendTime >= HEART_BEAT_RATE) {
                sendTime = System.currentTimeMillis();
                //boolean isSend = sendMessage(App.getInstance().getWSHeart());
                //LogUtils.i("心跳是否发送成功"+isSend);
            }
            mHandler.postDelayed(this, HEART_BEAT_RATE); //每隔一定的时间，对长连接进行一次心跳检测
        }
    };

    /**
     * 发送消息
     *
     * @param text 字符串
     * @return boolean
     */
    public boolean sendMessage(String text) {
        if (!isConnect()) return false;
        return mWebSocket.send(text);
    }

    /**
     * 发送消息
     *
     * @param byteString 字符集
     * @return boolean
     */
    public boolean sendMessage(ByteString byteString) {
        if (!isConnect()) return false;
        return mWebSocket.send(byteString);
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (isConnect()) {
            mWebSocket.cancel();
            mWebSocket.close(1001, "客户端主动关闭连接");
        }

        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    private WebSocketListener createListener() {
        return new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                Logger.i("WebSocket 打开:" + response.toString());
                mWebSocket = webSocket;
                isConnect = response.code() == 101;
                if (!isConnect) {
                    reconnect();
                } else {
                    Logger.i( "WebSocket 连接成功");
                    if (receiveMessage != null) {
                        receiveMessage.onConnectSuccess();
                    }
//                    if (sendMessage(App.getInstance().getWSLogin())) {
//                        mHandler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);
//                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                if (receiveMessage != null) {
                    receiveMessage.onMessage(text);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
                if (receiveMessage != null) {
                    receiveMessage.onMessage(bytes.base64());
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
                mWebSocket = null;
                isConnect = false;
                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                }
                if (receiveMessage != null) {
                    receiveMessage.onClose();
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                mWebSocket = null;
                isConnect = false;
                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                }
                if (receiveMessage != null) {
                    receiveMessage.onClose();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                if (response != null) {
                    Logger.i("WebSocket 连接失败：" + response.message());
                }
                Logger.i( "WebSocket 连接失败异常原因：" + t.getMessage());
                isConnect = false;
                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                }
                if (receiveMessage != null) {
                    receiveMessage.onConnectFailed();
                }
                if (!StringUtils.isEmpty(t.getMessage()) && !t.getMessage().equals("Socket closed")){
                    reconnect();
                }

            }
        };
    }
}


