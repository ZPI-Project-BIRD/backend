package zpi.project_bird.backend.user;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class RandomPasswordGenerator {
    private static final int PASSWORD_BYTES = 24;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[PASSWORD_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
