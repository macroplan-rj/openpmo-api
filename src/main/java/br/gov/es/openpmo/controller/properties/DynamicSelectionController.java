package br.gov.es.openpmo.controller.properties;

import br.gov.es.openpmo.dto.ResponseBase;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProvider;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Dado de referencia, somente leitura; exige apenas usuario autenticado, como as demais rotas.
 */
@RestController
@RequestMapping("/dynamic-selection")
public class DynamicSelectionController {

  private final DynamicSelectionProviderRegistry registry;

  public DynamicSelectionController(final DynamicSelectionProviderRegistry registry) {
    this.registry = registry;
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
