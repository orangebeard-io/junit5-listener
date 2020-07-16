package io.orangebeard.listener.entity;

import java.time.LocalDateTime;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonInclude(Include.NON_NULL)
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestRun {
    private String mode = "DEFAULT";
    private boolean rerun = false;
    private String rerunOf = null;
    private String uuid = null;

    private String name;
    private String description;
    @JsonSerialize(using = DateSerializer.class)
    private LocalDateTime startTime;
    private Set<Attribute> attributes;

    public TestRun(String name, String description, Set<Attribute> attributes) {
        this.name = name;
        this.description = description;
        this.startTime = LocalDateTime.now();
        this.attributes = attributes;
    }
}
