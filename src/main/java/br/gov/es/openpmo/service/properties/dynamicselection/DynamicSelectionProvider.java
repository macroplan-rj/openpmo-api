package br.gov.es.openpmo.service.properties.dynamicselection;

import java.util.List;
import java.util.Optional;

/**
 * Contrato do provedor de opcoes da propriedade "Selecao dinamica" (SD #10548 / SA-610).
 *
 * O OpenPMO conhece so esta interface. Quem sabe de PPA, Unidade Orcamentaria ou Plano
 * Estrategico e a implementacao: um bean Spring no classpath (um "plugin") ou uma lista interna
 * carregada por seed ({@link InternalListProvider}). O registro e por descoberta, em
 * {@link DynamicSelectionProviderRegistry}.
 */
public interface DynamicSelectionProvider {

  /** Chave estavel gravada no modelo, ex.: "ppa.indicador". */
  String key();

  /** Rotulo mostrado na administracao do modelo, ex.: "PPA — Indicadores". */
  String label();

  /** Chave do provedor-pai quando esta lista depende de outra (cascata). */
  Optional<String> dependsOnKey();

  /** Opcoes; com codigos do pai, filtra pelos itens desses pais. Lista vazia ou null = todas. */
  List<Option> options(List<String> parentCodes);

  /** Codigos -> opcoes (rotulos), para exibicao e gravacao do rotulo junto com o codigo. */
  List<Option> resolve(List<String> codes);

  final class Option {
    private final String code;
    private final String label;
    private final String parentCode;

    public Option(final String code, final String label, final String parentCode) {
      this.code = code;
      this.label = label;
      this.parentCode = parentCode;
    }

    public String getCode() { return this.code; }
    public String getLabel() { return this.label; }
    public String getParentCode() { return this.parentCode; }
  }
}
