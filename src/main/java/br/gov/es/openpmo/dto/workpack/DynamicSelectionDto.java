package br.gov.es.openpmo.dto.workpack;

import br.gov.es.openpmo.model.properties.DynamicSelection;
import br.gov.es.openpmo.model.properties.Property;

public class DynamicSelectionDto extends PropertyDto {

  private String value;
  private String label;

  public static DynamicSelectionDto of(final Property property) {
    final DynamicSelectionDto dto = new DynamicSelectionDto();
    dto.setId(property.getId());
    dto.setIdPropertyModel(property.getPropertyModelId());
    dto.setValue(((DynamicSelection) property).getValue());
    dto.setLabel(((DynamicSelection) property).getLabel());
    return dto;
  }

  public String getValue() {
    return this.value;
  }

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
  public String getType() {
    return "DynamicSelection";
  }

  @Override
  public void setType(final String type) {
    this.type = type;
  }

}
