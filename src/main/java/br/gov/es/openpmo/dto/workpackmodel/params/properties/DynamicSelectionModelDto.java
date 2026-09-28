package br.gov.es.openpmo.dto.workpackmodel.params.properties;

import br.gov.es.openpmo.model.properties.models.DynamicSelectionModel;
import br.gov.es.openpmo.model.properties.models.PropertyModel;

import javax.validation.constraints.NotBlank;

public class DynamicSelectionModelDto extends PropertyModelDto {

  @NotBlank
  private String providerKey;
  private String dependsOn;
  private boolean multipleSelection;
  private String defaultValue;

  public static DynamicSelectionModelDto of(final PropertyModel propertyModel) {
    final DynamicSelectionModelDto instance =
      (DynamicSelectionModelDto) PropertyModelDto.of(propertyModel, DynamicSelectionModelDto::new);
    final DynamicSelectionModel model = (DynamicSelectionModel) propertyModel;
    instance.setProviderKey(model.getProviderKey());
    instance.setDependsOn(model.getDependsOn());
    instance.setMultipleSelection(model.isMultipleSelection());
    instance.setDefaultValue(model.getDefaultValue());
    return instance;
  }

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
