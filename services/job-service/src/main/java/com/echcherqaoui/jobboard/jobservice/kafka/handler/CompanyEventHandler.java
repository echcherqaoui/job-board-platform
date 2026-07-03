package com.echcherqaoui.jobboard.jobservice.kafka.handler;

import com.google.protobuf.Message;

public interface CompanyEventHandler {
    String getDescriptorFullName();

    void handle(Message payload);
}