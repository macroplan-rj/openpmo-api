package br.gov.es.openpmo.service.properties.dynamicselection;

import java.util.List;
import java.util.Optional;

/**
 * SPIKE (US-020 / SA-610) — contrato do provedor de opcoes da propriedade "Selecao dinamica".
 * O OpenPMO conhece so esta interface; quem sabe de PPA, UO ou Plano Estrategico e a implementacao
 * (bean no classpath, plugin, chamada HTTP...). Descartavel: existe para validar o desenho com o
 * Vagner, nao para ir a producao neste formato.
 */
public interface DynamicSelectionProvider {

  /** Chave estavel gravada no PropertyModel, ex.: "ppa.indicador". */
  String key();

  /** Rotulo mostrado na administracao do modelo, ex.: "PPA — Indicadores". */
  String label();

  /** Chave do provedor-pai quando esta lista depende de outra (cascata). */
  Optional<String> dependsOnKey();

  /** Opcoes; com parentCode filtra pelo valor escolhido na propriedade-pai. */
  List<Option> options(String parentCode);

  /** Codigo -> opcao (rotulo), para leitura da ficha e relatorios. */
  Optional<Option> resolve(String code);

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
