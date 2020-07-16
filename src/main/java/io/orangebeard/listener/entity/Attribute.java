package io.orangebeard.listener.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode
public class Attribute {
    private String key;
    private String value;

    public Attribute(String value) {
        this.value = value;
    }
}


