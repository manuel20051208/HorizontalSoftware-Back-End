package unisimon.demo.security;

/** Access token (JWT) + refresh token (JWT) recien emitidos. {@code expiresIn} en segundos. */
public record ParTokens(String accessToken, String refreshToken, long expiresIn, String tokenType) {
}
