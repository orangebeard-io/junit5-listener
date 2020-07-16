package io.orangebeard.listener.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Log {
    private UUID itemUuid;
    @JsonProperty("launchUuid")
    private UUID testRunUUID;

    @JsonSerialize(using = DateSerializer.class)
    private LocalDateTime time;
    private String message;
    @JsonProperty("level")
    private LogLevel logLevel;

    public Log(UUID testRunUUID, UUID testItemUUID, LogLevel logLevel, String message) {
        this.itemUuid = testItemUUID;
        this.testRunUUID = testRunUUID;
        this.logLevel = logLevel;
        this.time = LocalDateTime.now();
        this.message = message;
    }
}
