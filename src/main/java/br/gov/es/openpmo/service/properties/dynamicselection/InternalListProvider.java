package br.gov.es.openpmo.service.properties.dynamicselection;

import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import br.gov.es.openpmo.repository.DynamicSelectionSourceRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provedor "lista interna": le os itens de {@link DynamicSelectionSource} de uma
 * {@link DynamicSelectionList}. E o provedor do PPA na Paraiba, que nao tem API do PPA — a
 * fonte e a planilha oficial, carregada por seed (tools/generate_dynamic_selection_seed.py).
 */
public class InternalListProvider implements DynamicSelectionProvider {

  private final DynamicSelectionList list;
  private final DynamicSelectionSourceRepository repository;

  public InternalListProvider(final DynamicSelectionList list, final DynamicSelectionSourceRepository repository) {
    this.list = list;
    this.repository = repository;
  }

  @Override public String key() { return this.list.getKey(); }
  @Override public String label() { return this.list.getLabel(); }
  @Override public Optional<String> dependsOnKey() { return Optional.ofNullable(this.list.getDependsOnKey()); }

  @Override
  public List<Option> options(final List<String> parentCodes) {
    final List<String> pais = this.list.getDependsOnKey() == null ? Collections.emptyList() : parentCodes;
    return map(this.repository.findOptions(this.list.getKey(), pais));
  }

  @Override
  public List<Option> resolve(final List<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return Collections.emptyList();
    }
    return map(this.repository.findByCodes(this.list.getKey(), codes));
  }

  private static List<Option> map(final List<DynamicSelectionSource> sources) {
    if (sources == null) {
      return Collections.emptyList();
    }
    return sources.stream()
      .map(s -> new Option(s.getCode(), s.getLabel(), s.getParentCode()))
      .collect(Collectors.toList());
  }

}
