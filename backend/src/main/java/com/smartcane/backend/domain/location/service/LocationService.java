package com.smartcane.backend.domain.location.service;

import com.smartcane.backend.config.RedisConfig;
import com.smartcane.backend.domain.location.dto.LocationUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class LocationService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final StringRedisTemplate redisTemplate;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    public LocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            List<SseEmitter> dead = new java.util.ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    dead.add(emitter);
                }
            }
            emitters.removeAll(dead);
        }, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopHeartbeat() {
        heartbeatScheduler.shutdown();
    }

    public void publish(LocationUpdateRequest req) {
        String json = """
                {"deviceId":"%s","lat":%f,"lng":%f}
                """.formatted(req.getDeviceId(), req.getLat(), req.getLng()).strip();
        redisTemplate.convertAndSend(RedisConfig.LOCATION_CHANNEL, json);
    }

    public void publishStop(String deviceId) {
        String json = "{\"deviceId\":\"%s\",\"stop\":true}".formatted(deviceId);
        redisTemplate.convertAndSend(RedisConfig.LOCATION_CHANNEL, json);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody());
        boolean isStop = json.contains("\"stop\":true");
        String eventName = isStop ? "location-stop" : "location";

        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
