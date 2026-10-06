package br.gov.es.openpmo.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Le o {@code possibleValues} de um SelectionModel como lista, na ordem cadastrada no modelo.
 *
 * O campo e uma string unica. Historicamente as opcoes sao separadas por ',' e ha uma
 * migracao em curso para ';' (SD-10628), porque ha opcoes com virgula no texto. Enquanto
 * as duas formas convivem: se houver ';' ele e o separador; senao, ','.
 */
public final class SelectionPossibleValues {

  private SelectionPossibleValues() {
  }

  public static List<String> split(final String possibleValues) {
    if (possibleValues == null || possibleValues.trim().isEmpty()) {
      return Collections.emptyList();
    }
    final String separator = possibleValues.contains(";") ? ";" : ",";
    return Arrays.stream(possibleValues.split(Pattern.quote(separator)))
      .map(String::trim)
      .filter(value -> !value.isEmpty())
      .collect(Collectors.toList());
  }
}
