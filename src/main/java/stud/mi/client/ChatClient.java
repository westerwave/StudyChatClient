package stud.mi.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.java_websocket.WebSocket.READYSTATE;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import stud.mi.message.Message;
import stud.mi.message.MessageType;
import stud.mi.message.MessageUtil;
import stud.mi.util.ChannelUpdateListener;
import stud.mi.util.ChatMessageListener;

public class ChatClient extends WebSocketClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatClient.class);
    private static final long HEARTBEAT_RATE = 10 * 1000L;
    public static final int PROTOCOL_VERSION = 1;

    private long userID = -1L;
    private String channel = "";
    private ChatMessageListener chatMessageListener;

    public final Set<String> channelList = new HashSet<>();
    private ChannelUpdateListener onChannelListUpdateListener;

    public final Set<String> userList = new HashSet<>();
    private ChannelUpdateListener onUserListUpdateListener;

    private ChannelUpdateListener onChannelJoinListener;

    private Timer heartBeatTimer;

    public ChatClient(final URI serverURI)
    {
        super(serverURI);
        LOGGER.info("Created ChatClient with Server  URI {}.", serverURI);
        this.setupHeartBeat();
    }

    public ChatClient(final URI serverURI, final ChatClient oldClient)
    {
        super(serverURI);
        this.setupHeartBeat();
        this.chatMessageListener = oldClient.chatMessageListener;
        this.onChannelListUpdateListener = oldClient.onChannelListUpdateListener;
        this.onChannelJoinListener = oldClient.onChannelJoinListener;
        this.onUserListUpdateListener = oldClient.onUserListUpdateListener;
        LOGGER.info("Created ChatClient with Server  URI {}.", serverURI);
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote)
    {
        LOGGER.debug("Close Connection: Code {}, Reason {}, IsRemote {}", code, reason, remote);
        this.userID = -1L;
        this.channel = "";
        this.userJoinedChannel(new ArrayList<>());
        this.receiveChannelNames(new ArrayList<>());
    }

    @Override
    public void onError(final Exception ex)
    {
        LOGGER.error("Error", ex);
    }

    @Override
    public void onMessage(final String message)
    {
        LOGGER.info("Received Message: {}", message);
        this.parseMessage(message);
    }

    @Override
    public void onOpen(final ServerHandshake handshakedata)
    {
        LOGGER.debug("Open Connection");
    }

    private void parseMessage(final String message)
    {
        final Message msg = new Message(message);
        switch (msg.getType())
        {
        case MessageType.USER_JOIN:
            if (msg.getUserID() > 0)
            {
                this.userID = msg.getUserID();
            }
            break;
        case MessageType.ACK_CHANNEL_JOIN:
            this.channel = msg.getChannelName();
            break;
        case MessageType.CHANNEL_MESSAGE:
            this.addMessage(msg);
            break;
        case MessageType.CHANNEL_HISTORY:
            this.addHistory(msg);
            break;
        case MessageType.CHANNEL_USER_CHANGE:
            this.userJoinedChannel(msg.getChannelUserNames());
            break;
        case MessageType.CHANNEL_CHANGE:
            this.receiveChannelNames(msg.getChannelNames());
            break;
        default:
            LOGGER.error("Message Type unknown: {}", msg.getType());
        }
    }

    private void addHistory(final Message msg)
    {
        final JsonArray msgArray = msg.getContent().get("channelHistory").getAsJsonArray();
        LOGGER.debug("Adding {} History messages", msgArray.size());
        for (final JsonElement element : msgArray)
        {
            this.addMessage(new Message(element.getAsJsonObject()));
        }
    }

    private void receiveChannelNames(final List<String> channelNames)
    {
        this.channelList.clear();
        this.channelList.addAll(channelNames);
        this.onChannelListUpdateListener.onUpdate();
        LOGGER.debug("Received {} Channels.", channelNames.size());
    }

    public void stopTimer()
    {
        this.heartBeatTimer.cancel();
        LOGGER.debug("Stopped Heartbeat Timer.");
    }

    private void userJoinedChannel(final List<String> channelUserNames)
    {
        this.userList.clear();
        this.userList.addAll(channelUserNames);
        this.onUserListUpdateListener.onUpdate();
        LOGGER.debug("Received {} Users.", channelUserNames.size());
    }

    private void addMessage(final Message msg)
    {
        if (this.chatMessageListener != null)
        {
            this.chatMessageListener.onMessage(msg);
            LOGGER.debug("Add Message {}", msg.toJson());
        }
    }

    public void disconnect()
    {
        if (this.isConnected())
        {
            try
            {
                this.onChannelJoinListener.onUpdate();
                this.closeBlocking();
                LOGGER.debug("Closed Connection to Server.");
            }
            catch (final InterruptedException e)
            {
                LOGGER.error("Could not close Connection!", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setChatMessageListener(final ChatMessageListener listener)
    {
        this.chatMessageListener = listener;
    }

    private void setupHeartBeat()
    {
        this.heartBeatTimer = new Timer(true);
        this.heartBeatTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                LOGGER.trace("Running Heartbeat.");
                ChatClient.this.send(MessageUtil.buildHeartbeatMessage(ChatClient.this.userID).toJson());
            }
        }, 1000L, HEARTBEAT_RATE);
    }

    public void setChannelJoinListener(final ChannelUpdateListener listener)
    {
        this.onChannelJoinListener = listener;
    }

    public String getChannel()
    {
        return this.channel;
    }

    public long getUserID()
    {
        return this.userID;
    }

    public boolean isConnected()
    {
        return this.getReadyState() == READYSTATE.OPEN;
    }

    public boolean isRegistered()
    {
        return this.userID != -1L;
    }

    public boolean isConnectedToChannel()
    {
        return this.isConnected() && !this.channel.isEmpty();
    }

    public void setOnChannelListUpdateListener(final ChannelUpdateListener listener)
    {
        this.onChannelListUpdateListener = listener;
    }

    public void setOnUserListUpdateListener(final ChannelUpdateListener listener)
    {
        this.onUserListUpdateListener = listener;
    }

    @Override
    public void send(final String msg)
    {
        if (this.isConnected())
        {
            super.send(msg);
            LOGGER.trace("Send Message to Server: '{}'", msg);
        }
    }

    public void changeChannel(final String newChannel)
    {
        if (this.isRegistered())
        {
            final String msg = MessageUtil.buildChannelJoinMessage(newChannel, this.getUserID()).toJson();
            this.send(msg);
            this.onChannelJoinListener.onUpdate();
            LOGGER.debug("Change Channel to '{}'", newChannel);
        }
    }

    public void sendMessage(final String message)
    {
        if (this.isConnectedToChannel())
        {
            this.send(MessageUtil.buildSendMessage(message, this.getUserID()).toJson());
            LOGGER.debug("Send Message '{}' to current Channel.", message);
        }
    }

    public boolean connectToServer(final String userName)
    {
        if (!userName.isEmpty())
        {
            try
            {
                final boolean wasConnectionSuccessful = this.connectBlocking();
                if (wasConnectionSuccessful)
                {
                    LOGGER.debug("Connected successfully to Server.");
                    this.send(MessageUtil.buildUserJoinMessage(userName).toJson());
                    LOGGER.debug("Try to register Username '{}'.", userName);
                }
                return wasConnectionSuccessful;
            }
            catch (final InterruptedException e)
            {
                LOGGER.error("Connecting was interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

}
