package br.gov.es.openpmo.model.properties.dynamicselection;

import br.gov.es.openpmo.model.Entity;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * Definicao de uma lista interna de opcoes para a propriedade "Selecao dinamica" (SD #10548).
 * Cada lista vira um provedor: {@code key} e a chave usada no modelo (ex.: "ppa.indicador"),
 * {@code dependsOnKey} diz de qual lista vem o valor-pai (ex.: "ppa.programa").
 * Carregada por seed; nao ha tela de cadastro nesta versao.
 */
@NodeEntity
public class DynamicSelectionList extends Entity {

  private String key;

  private String label;

  private String dependsOnKey;

  public String getKey() { return this.key; }

  public void setKey(final String key) { this.key = key; }

  public String getLabel() { return this.label; }

  public void setLabel(final String label) { this.label = label; }

  public String getDependsOnKey() { return this.dependsOnKey; }

  public void setDependsOnKey(final String dependsOnKey) { this.dependsOnKey = dependsOnKey; }

}
