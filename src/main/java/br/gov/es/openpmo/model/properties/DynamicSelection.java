package br.gov.es.openpmo.model.properties;

import br.gov.es.openpmo.enumerator.CategoryEnum;
import br.gov.es.openpmo.model.properties.models.DynamicSelectionModel;
import br.gov.es.openpmo.model.properties.models.PropertyModel;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.Objects;

/**
 * Valor de uma propriedade "Selecao dinamica" na ficha (SD #10548).
 *
 * {@code value} guarda o(s) CODIGO(S) escolhido(s), separados por virgula quando a selecao e
 * multipla — e o que permite a cascata (o filho filtra pelo codigo do pai). {@code label} guarda
 * o(s) rotulo(s) correspondente(s) no momento da gravacao, para que exibicao e relatorios nao
 * dependam do provedor estar disponivel.
 */
@NodeEntity
public class DynamicSelection extends Property<DynamicSelection, String> {

  private String value;

  private String label;

  private CategoryEnum category;

  @Relationship("IS_DRIVEN_BY")
  private DynamicSelectionModel driver;

  public DynamicSelection() {
  }

  @Override
  public DynamicSelection snapshot() {
    final DynamicSelection snapshot = new DynamicSelection();
    snapshot.setValue(this.value);
    snapshot.setLabel(this.label);
    return snapshot;
  }

  @Override
  public CategoryEnum getCategory() {
    return this.category;
  }

  @Override
  public void setCategory(final CategoryEnum category) {
    this.category = category;
  }

  @Override
  public boolean hasChanges(final DynamicSelection other) {
    return (this.value != null || other.value != null)
           && (this.value == null || !this.value.equals(other.value));
  }

  @Override
  public String getValue() {
    return this.value;
  }

  @Override
  public void setValue(final String value) {
    this.value = value;
  }

  public String getLabel() {
    return this.label;
  }

  public void setLabel(final String label) {
    this.label = label;
  }

  @Override
  public PropertyModel getPropertyModel() {
    return this.getDriver();
  }

  public DynamicSelectionModel getDriver() {
    return this.driver;
  }

  public void setDriver(final DynamicSelectionModel driver) {
    this.driver = driver;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), this.driver);
  }

  @Override
  public boolean equals(final Object o) {
    if(this == o) {
      return true;
    }
    if(o == null || this.getClass() != o.getClass()) {
      return false;
    }
    if(!super.equals(o)) {
      return false;
    }
    final DynamicSelection that = (DynamicSelection) o;
    return Objects.equals(this.driver, that.driver);
  }

}
