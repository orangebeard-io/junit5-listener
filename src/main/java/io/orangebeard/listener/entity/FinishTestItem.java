package io.orangebeard.listener.entity;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@NoArgsConstructor
public class FinishTestItem {
    private Boolean retry = false;
    @JsonProperty("launchUuid")
    private UUID testRunUUID;

    @JsonSerialize(using = DateSerializer.class)
    private LocalDateTime endTime;
    private Status status;
    private String description;
    private Set<Attribute> attributes;

    public FinishTestItem(UUID testRunUUID, Status status, String description, Set<Attribute> attributes) {
        this.testRunUUID = testRunUUID;
        this.status = status;
        this.description = description;
        this.endTime = LocalDateTime.now();
        this.attributes = attributes;
    }
}
