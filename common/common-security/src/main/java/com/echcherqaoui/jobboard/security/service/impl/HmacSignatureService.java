package com.echcherqaoui.jobboard.security.service.impl;

import com.echcherqaoui.jobboard.security.service.SignatureService;
import org.springframework.lang.NonNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HmacSignatureService implements SignatureService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DELIMITER = "|";

    private final byte[] secretKey;

    public HmacSignatureService(@NonNull String secret) {
        if (secret.isBlank())
            throw new IllegalArgumentException("HMAC secret must not be blank");
        this.secretKey = secret.getBytes(UTF_8);
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, ALGORITHM));
            byte[] hmacBytes = mac.doFinal(data.getBytes(UTF_8));

            return Base64.getUrlEncoder()
                  .withoutPadding()
                  .encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    // MessageDigest.isEqual is constant-time and null-safe
    private boolean constantTimeEquals(String expected, String provided) {
        if (provided == null || expected == null) return false;
        return MessageDigest.isEqual(
              expected.getBytes(UTF_8),
              provided.getBytes(UTF_8)
        );
    }

    @Override
    public String sign(String eventId,
                       String aggregateId,
                       String epochSeconds) {
        return computeHmac(
              String.join(
                    DELIMITER,
                    eventId,
                    aggregateId,
                    epochSeconds
              )
        );
    }

    @Override
    public boolean verify(String eventId,
                          String aggregateId,
                          String epochSeconds,
                          String signature) {
        String expected = sign(
              eventId,
              aggregateId,
              epochSeconds
        );

        return constantTimeEquals(expected, signature);
    }
}