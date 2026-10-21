package br.gov.es.openpmo.service.properties.dynamicselection;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import br.gov.es.openpmo.repository.DynamicSelectionListRepository;
import br.gov.es.openpmo.repository.DynamicSelectionSourceRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cadastro dos itens das listas internas da "Selecao dinamica" (SD #10548 / #10607): a tela do menu
 * do escritorio onde o PPA e o PELP sao mantidos, no padrao de Organizacoes. As listas em si (chave e
 * dependencia) continuam vindo de seed/migracao; aqui so se mantem os itens.
 *
 * O codigo e imutavel depois de criado, porque as fichas gravam o codigo; na edicao mudam rotulo e pai.
 */
@Service
public class DynamicSelectionListService {

  static final String LIST_NOT_FOUND = "dynamicSelection.list.notFound";
  static final String ITEM_NOT_FOUND = "dynamicSelection.item.notFound";
  static final String CODE_REQUIRED = "dynamicSelection.item.codeRequired";
  static final String LABEL_REQUIRED = "dynamicSelection.item.labelRequired";
  static final String DUPLICATED_CODE = "dynamicSelection.item.duplicatedCode";
  static final String PARENT_REQUIRED = "dynamicSelection.item.parentRequired";
  static final String PARENT_NOT_FOUND = "dynamicSelection.item.parentNotFound";
  static final String PARENT_NOT_ALLOWED = "dynamicSelection.item.parentNotAllowed";
  static final String ITEM_IS_PARENT = "dynamicSelection.item.isParent";
  static final String ITEM_IN_USE = "dynamicSelection.item.inUse";

  private final DynamicSelectionListRepository listRepository;
  private final DynamicSelectionSourceRepository sourceRepository;

  public DynamicSelectionListService(
    final DynamicSelectionListRepository listRepository,
    final DynamicSelectionSourceRepository sourceRepository
  ) {
    this.listRepository = listRepository;
    this.sourceRepository = sourceRepository;
  }

  public List<ListSummary> lists() {
    return this.listRepository.findAllOrdered().stream()
      .map(l -> new ListSummary(l.getKey(), l.getLabel(), l.getDependsOnKey(), this.sourceRepository.countByListKey(l.getKey())))
      .collect(Collectors.toList());
  }

  public List<DynamicSelectionSource> items(final String listKey) {
    this.list(listKey);
    return this.sourceRepository.findOptions(listKey, Collections.emptyList());
  }

  public DynamicSelectionSource create(final String listKey, final ItemRequest request) {
    final DynamicSelectionList list = this.list(listKey);
    final String code = trim(request.getCode());
    if (code == null) {
      throw new NegocioException(CODE_REQUIRED);
    }
    if (this.sourceRepository.findByCode(listKey, code).isPresent()) {
      throw new NegocioException(DUPLICATED_CODE + ";" + code);
    }
    final DynamicSelectionSource source = new DynamicSelectionSource();
    source.setListKey(listKey);
    source.setCode(code);
    this.fill(list, source, request);
    return this.sourceRepository.save(source);
  }

  public DynamicSelectionSource update(final String listKey, final String code, final ItemRequest request) {
    final DynamicSelectionList list = this.list(listKey);
    final DynamicSelectionSource source = this.item(listKey, code);
    this.fill(list, source, request);
    return this.sourceRepository.save(source);
  }

  public void delete(final String listKey, final String code) {
    this.list(listKey);
    final DynamicSelectionSource source = this.item(listKey, code);
    if (this.sourceRepository.isParentOfAny(listKey, code)) {
      throw new NegocioException(ITEM_IS_PARENT + ";" + code);
    }
    if (this.sourceRepository.isUsedInAnyProperty(listKey, code)) {
      throw new NegocioException(ITEM_IN_USE + ";" + code);
    }
    this.sourceRepository.delete(source);
  }

  private void fill(final DynamicSelectionList list, final DynamicSelectionSource source, final ItemRequest request) {
    final String label = trim(request.getLabel());
    if (label == null) {
      throw new NegocioException(LABEL_REQUIRED);
    }
    final String parentCode = trim(request.getParentCode());
    if (list.getDependsOnKey() == null) {
      if (parentCode != null) {
        throw new NegocioException(PARENT_NOT_ALLOWED);
      }
    } else {
      if (parentCode == null) {
        throw new NegocioException(PARENT_REQUIRED);
      }
      if (!this.sourceRepository.findByCode(list.getDependsOnKey(), parentCode).isPresent()) {
        throw new NegocioException(PARENT_NOT_FOUND + ";" + parentCode);
      }
    }
    source.setLabel(label);
    source.setParentCode(parentCode);
  }

  private DynamicSelectionList list(final String listKey) {
    return this.listRepository.findByKey(listKey)
      .orElseThrow(() -> new NegocioException(LIST_NOT_FOUND + ";" + listKey));
  }

  private DynamicSelectionSource item(final String listKey, final String code) {
    return this.sourceRepository.findByCode(listKey, code)
      .orElseThrow(() -> new NegocioException(ITEM_NOT_FOUND + ";" + code));
  }

  private static String trim(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }

  public static final class ItemRequest {
    private String code;
    private String label;
    private String parentCode;

    public String getCode() { return this.code; }
    public void setCode(final String code) { this.code = code; }
    public String getLabel() { return this.label; }
    public void setLabel(final String label) { this.label = label; }
    public String getParentCode() { return this.parentCode; }
    public void setParentCode(final String parentCode) { this.parentCode = parentCode; }
  }

  public static final class ListSummary {
    private final String key;
    private final String label;
    private final String dependsOnKey;
    private final long items;

    public ListSummary(final String key, final String label, final String dependsOnKey, final long items) {
      this.key = key;
      this.label = label;
      this.dependsOnKey = dependsOnKey;
      this.items = items;
    }

    public String getKey() { return this.key; }
    public String getLabel() { return this.label; }
    public String getDependsOnKey() { return this.dependsOnKey; }
    public long getItems() { return this.items; }
  }

}
