package io.orangebeard.listener.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Response {
    private UUID id;
    private int number;
}
