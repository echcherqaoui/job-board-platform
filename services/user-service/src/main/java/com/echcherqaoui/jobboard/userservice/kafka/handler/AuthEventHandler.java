package com.echcherqaoui.jobboard.userservice.kafka.handler;

import com.google.protobuf.Message;

public interface AuthEventHandler {
    String getDescriptorFullName();

    void handle(Message payload);
}