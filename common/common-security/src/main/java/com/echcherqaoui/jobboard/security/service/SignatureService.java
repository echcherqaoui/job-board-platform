package com.echcherqaoui.jobboard.security.service;


/**
 * Contract for event signature signing and verification across microservices.
 */
public interface SignatureService {

    /**
     * Signs an event by computing a signature over its identifying fields.
     *
     * @param eventId        unique event ID
     * @param aggregateId    the ID of the domain entity (e.g., User ID)
     * @param epochSeconds   event timestamp in epoch seconds
     * @return hex-encoded signature string
     */
    String sign(String eventId,
                String aggregateId,
                String epochSeconds);

    /**
     * Verifies that a received signature matches the expected one.
     */
    boolean verify(String eventId,
                   String aggregateId,
                   String epochSeconds,
                   String signature);
}