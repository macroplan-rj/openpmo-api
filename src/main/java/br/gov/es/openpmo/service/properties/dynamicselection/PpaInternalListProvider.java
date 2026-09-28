package br.gov.es.openpmo.service.properties.dynamicselection;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SPIKE — provedor "lista interna" do PPA. Le a lista mestre como texto no formato que o seed
 * atual grava em SelectionModel.possibleValues ("Programa 5001 - ...", "Indicador 5001 - ...",
 * "Acao 1640 - 5001 - ...") e devolve opcoes com codigo, rotulo e codigo do pai. Na versao
 * real a fonte e um no DynamicSelectionSource carregado por seed; o parser fica igual.
 */
public class PpaInternalListProvider implements DynamicSelectionProvider {

  public enum Nivel {
    PROGRAMA("ppa.programa", "PPA — Programas", null, Pattern.compile("^Programa\\s+(\\d+)\\b")),
    INDICADOR("ppa.indicador", "PPA — Indicadores", "ppa.programa", Pattern.compile("^Indicador\\s+(\\d+)\\b")),
    ACAO("ppa.acao", "PPA — Ações", "ppa.programa", Pattern.compile("^Acao\\s+(\\d+)\\s*-\\s*(\\d+)\\b"));

    final String key;
    final String label;
    final String dependsOn;
    final Pattern pattern;

    Nivel(final String key, final String label, final String dependsOn, final Pattern pattern) {
      this.key = key;
      this.label = label;
      this.dependsOn = dependsOn;
      this.pattern = pattern;
    }
  }

  private final Nivel nivel;
  private final Supplier<List<String>> listaMestre;

  public PpaInternalListProvider(final Nivel nivel, final Supplier<List<String>> listaMestre) {
    this.nivel = nivel;
    this.listaMestre = listaMestre;
  }

  @Override public String key() { return this.nivel.key; }
  @Override public String label() { return this.nivel.label; }
  @Override public Optional<String> dependsOnKey() { return Optional.ofNullable(this.nivel.dependsOn); }

  @Override
  public List<Option> options(final String parentCode) {
    return this.todas().stream()
      .filter(o -> parentCode == null || parentCode.equals(o.getParentCode()))
      .collect(Collectors.toList());
  }

  @Override
  public Optional<Option> resolve(final String code) {
    return this.todas().stream().filter(o -> o.getCode().equals(code)).findFirst();
  }

  private List<Option> todas() {
    final List<String> itens = this.listaMestre.get();
    if (itens == null) {
      return Collections.emptyList();
    }
    return itens.stream()
      .map(this::parse)
      .filter(java.util.Objects::nonNull)
      .collect(Collectors.toList());
  }

  private Option parse(final String label) {
    final Matcher m = this.nivel.pattern.matcher(label);
    if (!m.find()) {
      return null;
    }
    switch (this.nivel) {
      case PROGRAMA:
        return new Option(m.group(1), label, null);
      case INDICADOR:
        // O codigo do indicador e o do programa; o que o distingue e o rotulo inteiro.
        return new Option(label, label, m.group(1));
      case ACAO:
        return new Option(m.group(1), label, m.group(2));
      default:
        return null;
    }
  }
}
