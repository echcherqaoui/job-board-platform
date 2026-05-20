package com.echcherqaoui.jobboard.commonoutbox.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventId implements Serializable {
    
    private UUID id;
    private OffsetDateTime createdAt;
}