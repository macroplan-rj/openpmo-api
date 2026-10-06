package br.gov.es.openpmo.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Separador das opcoes da propriedade Selecao (SD #10628).
 * <p>
 * Possiveis valores, valor padrao e valores marcados em Selecao multipla sao gravados numa unica
 * string. O separador era ',' e impedia opcao com virgula no texto; passou a ser ';'.
 * Selecao dinamica NAO usa este separador (codigos unidos por ',').
 */
public final class SelectionValues {

  public static final String SEPARATOR = ";";

  private SelectionValues() {
  }

  /** Quebra a string gravada nas opcoes: separa por ';', apara as pontas e descarta vazios. */
  public static List<String> split(final String value) {
    if (value == null || value.isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(value.split(SEPARATOR))
      .map(String::trim)
      .filter(item -> !item.isEmpty())
      .collect(Collectors.toList());
  }

  /** Monta a lista "'a','b'" usada nos parametros Selection dos relatorios Jasper; null se nao houver opcao. */
  public static String toQuotedList(final String value) {
    final List<String> values = split(value);
    if (values.isEmpty()) {
      return null;
    }
    final StringJoiner joiner = new StringJoiner("','", "'", "'");
    values.forEach(joiner::add);
    return joiner.toString();
  }

}
