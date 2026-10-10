package com.bizpos.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý Rate Limiting đăng nhập thất bại:
 * 1. Chống brute-force mật khẩu theo từng tài khoản (tối đa 5 lần sai trong 5 phút).
 * 2. Chống spam làm phình to bảng audit_logs từ một IP (tối đa 50 lần sai từ IP ngoài trong 5 phút).
 * 3. Miễn trừ chặn toàn IP đối với địa chỉ loopback/localhost để tránh nghẽn test và POS quầy.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    public static final int MAX_FAILED_ATTEMPTS_PER_USER = 5;
    public static final int MAX_FAILED_ATTEMPTS_PER_IP = 50;
    public static final long LOCK_DURATION_MS = 5 * 60 * 1000L; // 5 phút

    private static class AttemptTracker {
        int failedCount;
        long lockedUntilMs;
        long lastAttemptMs;

        AttemptTracker(long now) {
            this.failedCount = 1;
            this.lockedUntilMs = 0;
            this.lastAttemptMs = now;
        }
    }

    private final Map<String, AttemptTracker> userAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptTracker> ipAttempts = new ConcurrentHashMap<>();

    private boolean isLoopbackIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equalsIgnoreCase(ip);
    }

    private boolean isTrackerBlocked(Map<String, AttemptTracker> map, String key) {
        if (key == null || key.isBlank()) return false;
        AttemptTracker tracker = map.get(key);
        if (tracker == null) return false;

        long now = System.currentTimeMillis();
        if (tracker.lockedUntilMs > now) {
            return true;
        }
        if (tracker.lockedUntilMs > 0 && tracker.lockedUntilMs <= now) {
            map.remove(key);
            return false;
        }
        return false;
    }

    public boolean isUserBlocked(String username) {
        return isTrackerBlocked(userAttempts, username);
    }

    public boolean isIpBlocked(String ip) {
        if (isLoopbackIp(ip)) return false;
        return isTrackerBlocked(ipAttempts, ip);
    }

    public boolean isBlocked(String username, String ip) {
        return isUserBlocked(username) || isIpBlocked(ip);
    }

    /**
     * Ghi nhận 1 lần đăng nhập thất bại cho user và IP.
     * @return true nếu lần này tài khoản hoặc IP BẮT ĐẦU bị khóa (chạm ngưỡng)
     */
    public boolean recordFailure(String username, String ip) {
        long now = System.currentTimeMillis();
        boolean userJustLocked = false;
        boolean ipJustLocked = false;

        if (username != null && !username.isBlank()) {
            AttemptTracker tracker = userAttempts.compute(username, (k, existing) -> {
                if (existing == null || (now - existing.lastAttemptMs > LOCK_DURATION_MS)) {
                    return new AttemptTracker(now);
                }
                existing.failedCount++;
                existing.lastAttemptMs = now;
                if (existing.failedCount >= MAX_FAILED_ATTEMPTS_PER_USER && existing.lockedUntilMs == 0) {
                    existing.lockedUntilMs = now + LOCK_DURATION_MS;
                }
                return existing;
            });
            if (tracker.failedCount == MAX_FAILED_ATTEMPTS_PER_USER) {
                userJustLocked = true;
            }
        }

        if (!isLoopbackIp(ip)) {
            AttemptTracker tracker = ipAttempts.compute(ip, (k, existing) -> {
                if (existing == null || (now - existing.lastAttemptMs > LOCK_DURATION_MS)) {
                    return new AttemptTracker(now);
                }
                existing.failedCount++;
                existing.lastAttemptMs = now;
                if (existing.failedCount >= MAX_FAILED_ATTEMPTS_PER_IP && existing.lockedUntilMs == 0) {
                    existing.lockedUntilMs = now + LOCK_DURATION_MS;
                }
                return existing;
            });
            if (tracker.failedCount == MAX_FAILED_ATTEMPTS_PER_IP) {
                ipJustLocked = true;
            }
        }

        return userJustLocked || ipJustLocked;
    }

    /**
     * Xóa sạch lịch sử thử sai của user và IP khi đăng nhập thành công.
     */
    public void reset(String username, String ip) {
        if (username != null) {
            userAttempts.remove(username);
        }
        if (ip != null) {
            ipAttempts.remove(ip);
        }
    }

    /**
     * Lấy thời gian còn bị khóa tính bằng giây.
     */
    public long getRemainingLockSeconds(String username, String ip) {
        long now = System.currentTimeMillis();
        long userRemaining = 0;
        if (username != null) {
            AttemptTracker t = userAttempts.get(username);
            if (t != null && t.lockedUntilMs > now) {
                userRemaining = (t.lockedUntilMs - now) / 1000;
            }
        }
        long ipRemaining = 0;
        if (!isLoopbackIp(ip)) {
            AttemptTracker t = ipAttempts.get(ip);
            if (t != null && t.lockedUntilMs > now) {
                ipRemaining = (t.lockedUntilMs - now) / 1000;
            }
        }
        return Math.max(userRemaining, ipRemaining);
    }

    /**
     * Xóa toàn bộ bộ nhớ cache (phục vụ test).
     */
    public void clear() {
        userAttempts.clear();
        ipAttempts.clear();
    }
}
