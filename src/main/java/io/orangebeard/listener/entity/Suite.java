package io.orangebeard.listener.entity;

import java.util.UUID;
import lombok.Getter;

@Getter
public class Suite {
    private UUID uuid;
    private Status status = Status.PASSED;

    public Suite(UUID uuid) {
        this.uuid = uuid;
    }

    public Suite setStatus(Status status) {
        this.status = status;
        return this;
    }
}
