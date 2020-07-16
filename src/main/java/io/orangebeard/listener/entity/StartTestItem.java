package io.orangebeard.listener.entity;

import java.time.LocalDateTime;
import java.util.Set;
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
public class StartTestItem {
    private String mode = "DEFAULT";
    private boolean rerun = false;
    private String rerunOf = null;
    @JsonProperty("launchUuid")
    private UUID testRunUUID;
    private TestItemType type;

    private String name;
    private String codeRef;
    private String description;
    @JsonSerialize(using = DateSerializer.class)
    private LocalDateTime startTime;
    private Set<Attribute> attributes;

    public StartTestItem(UUID testRunUUID, String name, TestItemType type, String codeRef, Set<Attribute> attributes) {
        this.testRunUUID = testRunUUID;
        this.name = name;
        this.type = type;
        this.codeRef = codeRef;
        this.description = codeRef;
        this.startTime = LocalDateTime.now();
        this.attributes = attributes;
    }
}
