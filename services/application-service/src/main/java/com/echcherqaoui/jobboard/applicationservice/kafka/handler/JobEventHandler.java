package com.echcherqaoui.jobboard.applicationservice.kafka.handler;

import com.google.protobuf.Message;

public interface JobEventHandler {
    String getDescriptorFullName();

    void handle(Message payload);
}