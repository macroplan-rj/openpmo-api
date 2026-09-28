package br.gov.es.openpmo.model.properties.models;

import org.neo4j.ogm.annotation.NodeEntity;

/**
 * Propriedade "Selecao dinamica" (SD #10548 / SA-610): as opcoes nao ficam no modelo, vem de um
 * provedor registrado pela chave {@link #providerKey} (ex.: "ppa.programa"). Opcionalmente
 * depende de outra propriedade do mesmo modelo, apontada pelo NOME em {@link #dependsOn}: o
 * formulario recarrega as opcoes com o valor escolhido na propriedade-pai.
 *
 * O vinculo com o pai e pelo nome, e nao por relacionamento, porque o modelo e criado de uma vez
 * com todas as propriedades e nenhuma tem id ainda nesse momento.
 */
@NodeEntity
public class DynamicSelectionModel extends PropertyModel {

  private String providerKey;

  private String dependsOn;

  private boolean multipleSelection;

  private String defaultValue;

  public String getProviderKey() {
    return this.providerKey;
  }

  public void setProviderKey(final String providerKey) {
    this.providerKey = providerKey;
  }

  public String getDependsOn() {
    return this.dependsOn;
  }

  public void setDependsOn(final String dependsOn) {
    this.dependsOn = dependsOn;
  }

  public boolean isMultipleSelection() {
    return this.multipleSelection;
  }

  public void setMultipleSelection(final boolean multipleSelection) {
    this.multipleSelection = multipleSelection;
  }

  public String getDefaultValue() {
    return this.defaultValue;
  }

  public void setDefaultValue(final String defaultValue) {
    this.defaultValue = defaultValue;
  }

}
