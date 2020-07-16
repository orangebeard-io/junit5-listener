package io.orangebeard.listener.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class FinishTestRun {
    @JsonSerialize(using = DateSerializer.class)
    private LocalDateTime endTime;
    private Status status;

    public FinishTestRun(Status status) {
        this.status = status;
        this.endTime = LocalDateTime.now();
    }
}
