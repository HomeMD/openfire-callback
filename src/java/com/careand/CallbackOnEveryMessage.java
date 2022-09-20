package com.careand;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Message.Type;
import org.xmpp.packet.Packet;
import org.dom4j.Element;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.Future;

public class CallbackOnEveryMessage implements Plugin, PacketInterceptor {

    private static final Logger Log = LoggerFactory.getLogger(CallbackOnEveryMessage.class);

    private static final String PROPERTY_DEBUG = "plugin.callback_on_every_message.debug";
    private static final String PROPERTY_URL = "plugin.callback_on_every_message.url";
    private static final String PROPERTY_TOKEN = "plugin.callback_on_every_message.token";
    private static final String PROPERTY_SEND_BODY = "plugin.callback_on_every_message.send_body";

    private boolean debug;
    private boolean sendBody;

    private String url;
    private String token;
    private InterceptorManager interceptorManager;
    private Client client;

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        debug = JiveGlobals.getBooleanProperty(PROPERTY_DEBUG, false);
        sendBody = JiveGlobals.getBooleanProperty(PROPERTY_SEND_BODY, true);

        url = getProperty(PROPERTY_URL, "http://localhost:8080/user/callback/url");
        token = getProperty(PROPERTY_TOKEN, UUID.randomUUID().toString());

        if (debug) {
            Log.debug("initialize CallbackOnEveryMessage plugin. Start.");
            Log.debug("Loaded properties: \nurl={}, \ntoken={}, \nsendBody={}", new Object[]{url, token, sendBody});
        }

        interceptorManager = InterceptorManager.getInstance();
        client = ClientBuilder.newClient();

        // register with interceptor manager
        interceptorManager.addInterceptor(this);

        if (debug) {
            Log.debug("initialize CallbackOnEveryMessage plugin. Finish.");
        }
    }

    private String getProperty(String code, String defaultSetValue) {
        String value = JiveGlobals.getProperty(code, null);
        if (value == null || value.length() == 0) {
            JiveGlobals.setProperty(code, defaultSetValue);
            value = defaultSetValue;
        }

        return value;
    }

    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
        if (debug) {
            Log.debug("destroy CallbackOnEveryMessage plugin.");
        }
    }


    public void interceptPacket(Packet packet, Session session, boolean incoming,
                                boolean processed) throws PacketRejectedException {

        if (processed
                && incoming
                && packet instanceof Message
                && packet.getTo() != null) {

            Message msg = (Message) packet;
            JID to = packet.getTo();
            Element delay = msg.getChildElement("delay", "urn:xmpp:delay");
            JID from = packet.getFrom();
            String body = sendBody ? msg.getBody() : null;

            if (
            body == null ||
            delay != null ||
            (msg.getType() != Message.Type.chat && msg.getType() != Message.Type.groupchat) ||
            from.toBareJID().equals(to.toBareJID())
            ) {
                return;
            }

            if (debug) {
                Log.debug("intercepted message from {} to {}", new Object[]{packet.getFrom().toBareJID(), to.toBareJID()});
            }

            WebTarget target = client.target(url);

            if (debug) {
                Log.debug("sending request to url='{}'", target);
            }


            MessageData data = new MessageData(token, from.toBareJID(), to.toBareJID(), body);

            Future<Response> responseFuture = target
                    .request()
                    .async()
                    .post(Entity.json(data));

            if (debug) {
                try {
                    Response response = responseFuture.get();
                    Log.debug("got response status url='{}' status='{}'", target, response.getStatus());
                } catch (Exception e) {
                    Log.debug("can't get response status url='{}'", target, e);
                }
            }
        }
    }

}
