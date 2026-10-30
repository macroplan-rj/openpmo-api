package br.gov.es.openpmo.controller.properties;

import br.gov.es.openpmo.configuration.Authorization;
import br.gov.es.openpmo.dto.ResponseBase;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import br.gov.es.openpmo.service.permissions.canaccess.ICanAccessService;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionListService;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProvider;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rotas da propriedade "Selecao dinamica" (SD #10548):
 *   GET /dynamic-selection/providers                         — para a administracao do modelo
 *   GET /dynamic-selection/options?provider=&parent=5001,5002 — para o formulario (cascata)
 *   GET /dynamic-selection/resolve?provider=&codes=1496,1640  — codigo -> rotulo
 *   GET /dynamic-selection/lists[/{key}/items]               — tela de cadastro das listas
 *   POST /dynamic-selection/lists?id-office=                 — nova lista (nome e lista de que depende)
 *   POST|PUT|DELETE /dynamic-selection/lists/{key}/items[/{code}]?id-office= — itens (SD #10548 / #10607)
 * Leitura exige apenas usuario autenticado, como as demais rotas; escrita exige permissao de edicao
 * no escritorio de onde a tela e aberta, como Organizacoes.
 */
@RestController
@RequestMapping("/dynamic-selection")
public class DynamicSelectionController {

  private final DynamicSelectionProviderRegistry registry;
  private final DynamicSelectionListService listService;
  private final ICanAccessService canAccessService;

  public DynamicSelectionController(
    final DynamicSelectionProviderRegistry registry,
    final DynamicSelectionListService listService,
    final ICanAccessService canAccessService
  ) {
    this.registry = registry;
    this.listService = listService;
    this.canAccessService = canAccessService;
  }

  @GetMapping("/providers")
  public ResponseEntity<ResponseBase<List<ProviderResponse>>> providers() {
    final List<ProviderResponse> response = this.registry.all().values().stream()
      .map(p -> new ProviderResponse(p.key(), p.label(), p.dependsOnKey().orElse(null)))
      .collect(Collectors.toList());
    return ResponseEntity.ok(ResponseBase.of(response));
  }

  @GetMapping("/options")
  public ResponseEntity<ResponseBase<List<DynamicSelectionProvider.Option>>> options(
    @RequestParam("provider") final String provider,
    @RequestParam(value = "parent", required = false) final String parent
  ) {
    return ResponseEntity.ok(ResponseBase.of(this.registry.get(provider).options(split(parent))));
  }

  @GetMapping("/resolve")
  public ResponseEntity<ResponseBase<List<DynamicSelectionProvider.Option>>> resolve(
    @RequestParam("provider") final String provider,
    @RequestParam("codes") final String codes
  ) {
    return ResponseEntity.ok(ResponseBase.of(this.registry.get(provider).resolve(split(codes))));
  }

  @GetMapping("/lists")
  public ResponseEntity<ResponseBase<List<DynamicSelectionListService.ListSummary>>> lists() {
    return ResponseEntity.ok(ResponseBase.of(this.listService.lists()));
  }

  @PostMapping("/lists")
  public ResponseEntity<ResponseBase<DynamicSelectionListService.ListSummary>> createList(
    @RequestParam("id-office") final Long idOffice,
    @RequestBody final DynamicSelectionListService.ListRequest request,
    @Authorization final String authorization
  ) {
    this.canAccessService.ensureCanEditResource(idOffice, authorization);
    return ResponseEntity.ok(ResponseBase.of(this.listService.createList(request)));
  }

  @GetMapping("/lists/{key}/items")
  public ResponseEntity<ResponseBase<List<DynamicSelectionSource>>> items(@PathVariable("key") final String key) {
    return ResponseEntity.ok(ResponseBase.of(this.listService.items(key)));
  }

  @PostMapping("/lists/{key}/items")
  public ResponseEntity<ResponseBase<DynamicSelectionSource>> createItem(
    @PathVariable("key") final String key,
    @RequestParam("id-office") final Long idOffice,
    @RequestBody final DynamicSelectionListService.ItemRequest request,
    @Authorization final String authorization
  ) {
    this.canAccessService.ensureCanEditResource(idOffice, authorization);
    return ResponseEntity.ok(ResponseBase.of(this.listService.create(key, request)));
  }

  @PutMapping("/lists/{key}/items/{code}")
  public ResponseEntity<ResponseBase<DynamicSelectionSource>> updateItem(
    @PathVariable("key") final String key,
    @PathVariable("code") final String code,
    @RequestParam("id-office") final Long idOffice,
    @RequestBody final DynamicSelectionListService.ItemRequest request,
    @Authorization final String authorization
  ) {
    this.canAccessService.ensureCanEditResource(idOffice, authorization);
    return ResponseEntity.ok(ResponseBase.of(this.listService.update(key, code, request)));
  }

  @DeleteMapping("/lists/{key}/items/{code}")
  public ResponseEntity<Void> deleteItem(
    @PathVariable("key") final String key,
    @PathVariable("code") final String code,
    @RequestParam("id-office") final Long idOffice,
    @Authorization final String authorization
  ) {
    this.canAccessService.ensureCanEditResource(idOffice, authorization);
    this.listService.delete(key, code);
    return ResponseEntity.ok().build();
  }

  static List<String> split(final String csv) {
    if (csv == null || csv.trim().isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(csv.split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toList());
  }

  public static final class ProviderResponse {
    private final String key;
    private final String label;
    private final String dependsOnKey;

    public ProviderResponse(final String key, final String label, final String dependsOnKey) {
      this.key = key;
      this.label = label;
      this.dependsOnKey = dependsOnKey;
    }

    public String getKey() { return this.key; }
    public String getLabel() { return this.label; }
    public String getDependsOnKey() { return this.dependsOnKey; }
  }

}
