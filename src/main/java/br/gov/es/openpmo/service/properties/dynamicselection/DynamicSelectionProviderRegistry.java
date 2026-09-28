package br.gov.es.openpmo.service.properties.dynamicselection;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import br.gov.es.openpmo.repository.DynamicSelectionListRepository;
import br.gov.es.openpmo.repository.DynamicSelectionSourceRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Descobre os provedores de "Selecao dinamica": todo bean que implementa
 * {@link DynamicSelectionProvider} (plugin no classpath) mais uma {@link InternalListProvider}
 * por {@link DynamicSelectionList} gravada no banco. Chave duplicada e erro de configuracao —
 * a mesma chave em dois provedores deixaria a ficha ambigua.
 */
@Component
public class DynamicSelectionProviderRegistry {

  static final String PROVIDER_NOT_FOUND = "dynamicSelection.provider.notFound";
  static final String DUPLICATED_KEY = "dynamicSelection.provider.duplicatedKey";

  private final List<DynamicSelectionProvider> beans;
  private final DynamicSelectionListRepository listRepository;
  private final DynamicSelectionSourceRepository sourceRepository;

  public DynamicSelectionProviderRegistry(
    final Optional<List<DynamicSelectionProvider>> beans,
    final DynamicSelectionListRepository listRepository,
    final DynamicSelectionSourceRepository sourceRepository
  ) {
    this.beans = beans.orElse(Collections.emptyList());
    this.listRepository = listRepository;
    this.sourceRepository = sourceRepository;
  }

  /** Todos os provedores, por chave, em ordem estavel. As listas internas sao relidas a cada chamada. */
  public Map<String, DynamicSelectionProvider> all() {
    final Map<String, DynamicSelectionProvider> porChave = new LinkedHashMap<>();
    final List<DynamicSelectionProvider> todos = new ArrayList<>(this.beans);
    for (final DynamicSelectionList list : this.listRepository.findAllOrdered()) {
      todos.add(new InternalListProvider(list, this.sourceRepository));
    }
    for (final DynamicSelectionProvider provider : todos) {
      if (porChave.putIfAbsent(provider.key(), provider) != null) {
        throw new NegocioException(DUPLICATED_KEY + "$" + provider.key());
      }
    }
    return porChave;
  }

  public DynamicSelectionProvider get(final String key) {
    final DynamicSelectionProvider provider = this.all().get(key);
    if (provider == null) {
      throw new NegocioException(PROVIDER_NOT_FOUND + "$" + key);
    }
    return provider;
  }

}
