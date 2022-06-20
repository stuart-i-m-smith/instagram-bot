package com.client;

import com.tick.TickManager;
import com.model.Tick;
import org.apache.commons.math3.util.Precision;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FtxClient implements Client {

    private final String currency;
    private final TickManager tickManager;
    private final TickManager futuresTickManager;

    public FtxClient(String currency, TickManager tickManager, TickManager futuresTickManager){
        this.currency = currency;
        this.tickManager = tickManager;
        this.futuresTickManager = futuresTickManager;
    }

    @Override
    public void connect() {
        try {
            WebSocketClient client = new WebSocketClient(new URI("wss://ftx.com/ws/")) {

                private volatile double lastBid = 0;
                private volatile double lastAsk = 0;

                @Override
                public void onMessage(String message) {
                    System.out.println(message);

                    JSONObject json = new JSONObject(message);

                    if(!json.has("channel")){
                        return;
                    }

                    String channel = json.getString("channel");

                    if ("orderbook".equals(channel)) {

                        if(json.has("data")) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray bids = data.getJSONArray("bids");
                            JSONArray asks = data.getJSONArray("asks");

                            double bid = bids.length() == 0 ? lastBid : bids.getJSONArray(0).getDouble(0);
                            double ask = asks.length() == 0 ? lastAsk : asks.getJSONArray(0).getDouble(0);

                            if(!Precision.equals(lastBid, bid) ||
                                !Precision.equals(lastAsk, ask)) {

                                Tick tick = new Tick.Builder()
                                        .exchange("ftx")
                                        .timestamp(Instant.ofEpochMilli(((Number) data.getDouble("time")).longValue() * 1000))
                                        .bid(bid)
                                        .ask(ask)
                                        .build();

                                lastBid = bid;
                                lastAsk = ask;

                                tickManager.offer(tick);
                            }
                        }
                    }
                }

                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    System.out.println("Connected to FTX.");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("closed connection");
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

            client.connectBlocking(10, TimeUnit.SECONDS);

            JSONObject subscribeMessage = new JSONObject();
            subscribeMessage.put("op", "subscribe");
            subscribeMessage.put("channel", "orderbook");

            subscribeMessage.put("market", currency+"/USD");
            client.send(subscribeMessage.toString());

//            subscribeMessage.put("market", currency+"-PERP");
//            client.send(subscribeMessage.toString());

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                    () -> client.send("{'op': 'ping'}"),
                    15, 15, TimeUnit.SECONDS);

        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}