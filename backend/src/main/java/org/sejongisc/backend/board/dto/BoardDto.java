package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class BoardDto {
    private UUID id;
    private String name;
    private boolean isPrivate;
}
