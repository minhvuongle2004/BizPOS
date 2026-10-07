package com.bizpos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests cho JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "dGVzdF9qd3Rfc2VjcmV0X2tleV9mb3JfZGV2ZWxvcG1lbnRfcHVycG9zZXNfb25seV8yNTZiaXRzX2xvbmc=";
    private static final long EXPIRATION_MS = 3600000L; // 1 giờ

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Tạo JWT token hợp lệ thành công")
    void generateToken_success() {
        String token = jwtTokenProvider.generateToken("admin", "ADMIN");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        // JWT gồm 3 phần phân cách bởi dấu chấm: header.payload.signature
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Parse lấy username chính xác từ JWT token hợp lệ")
    void getUsernameFromToken_success() {
        String token = jwtTokenProvider.generateToken("user_test", "STAFF");

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertEquals("user_test", username);
    }

    @Test
    @DisplayName("Parse lấy role chính xác từ JWT token hợp lệ")
    void getRoleFromToken_success() {
        String token = jwtTokenProvider.generateToken("admin_boss", "ADMIN");

        String role = jwtTokenProvider.getRoleFromToken(token);

        assertEquals("ADMIN", role);
    }

    @Test
    @DisplayName("Validate token hợp lệ trả về true")
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateToken("staff1", "STAFF");

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Token hết hạn bị từ chối (validate trả về false)")
    void validateToken_expiredToken_returnsFalse() {
        // Khởi tạo provider với thời gian hết hạn âm (-1000ms) để token sinh ra đã hết hạn ngay
        JwtTokenProvider expiredProvider = new JwtTokenProvider(TEST_SECRET, -1000L);
        String expiredToken = expiredProvider.generateToken("expired_user", "STAFF");

        boolean isValid = jwtTokenProvider.validateToken(expiredToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Token sai định dạng (malformed) bị từ chối")
    void validateToken_malformedToken_returnsFalse() {
        String malformedToken = "not.a.valid.jwt.token.string";

        boolean isValid = jwtTokenProvider.validateToken(malformedToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Token rỗng hoặc null bị từ chối")
    void validateToken_emptyOrNullToken_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
        assertFalse(jwtTokenProvider.validateToken("   "));
    }

    @Test
    @DisplayName("Token bị can thiệp/chỉnh sửa chữ ký (tampered) bị từ chối")
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtTokenProvider.generateToken("admin", "ADMIN");
        // Thay đổi ký tự cuối cùng của token (phần signature)
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        boolean isValid = jwtTokenProvider.validateToken(tamperedToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Token ký bằng secret khác bị từ chối khi validate")
    void validateToken_differentSecret_returnsFalse() {
        String otherSecret = "516E635266556A586E3272357538782F413F4428472B4B6250645367566B5971";
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherSecret, EXPIRATION_MS);
        String tokenFromOther = otherProvider.generateToken("admin", "ADMIN");

        boolean isValid = jwtTokenProvider.validateToken(tokenFromOther);

        assertFalse(isValid);
    }
}
