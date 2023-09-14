package model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Pessoa(UUID id, String apelido, String nome, LocalDate nascimento, List<String> stack) {
}
