package com.im.agentic.infra.agent;

public interface Agent<I, O> {
    String getName();
    O execute(I input);
}
