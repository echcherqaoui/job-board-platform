package com.echcherqaoui.jobboard.sharedutils.kafka;

import com.google.protobuf.Message;

public interface EventHandler {
    String getDescriptorFullName();
    void handle(Message event);
}