package br.gov.es.openpmo.dto;

import br.gov.es.openpmo.controller.properties.DynamicSelectionController;
import br.gov.es.openpmo.dto.workpack.DynamicSelectionDto;
import br.gov.es.openpmo.dto.workpack.PropertyDto;
import br.gov.es.openpmo.dto.workpackmodel.params.properties.DynamicSelectionModelDto;
import br.gov.es.openpmo.dto.workpackmodel.params.properties.PropertyModelDto;
import br.gov.es.openpmo.model.properties.DynamicSelection;
import br.gov.es.openpmo.model.properties.models.DynamicSelectionModel;
import br.gov.es.openpmo.utils.PropertyInstanceType;
import br.gov.es.openpmo.utils.PropertyModelInstanceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** US-021 — o tipo novo atravessa JSON e os registros de tipo como os demais. */
public class DynamicSelectionSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void modeloDesserializaPeloType() throws Exception {
    final PropertyModelDto dto = this.mapper.readValue(
      "{\"type\":\"DynamicSelectionModel\",\"name\":\"indicadores\",\"label\":\"Indicadores\",\"sortIndex\":1," +
      "\"providerKey\":\"ppa.indicador\",\"dependsOn\":\"programa\",\"multipleSelection\":true}",
      PropertyModelDto.class);
    assertTrue(dto instanceof DynamicSelectionModelDto);
    final DynamicSelectionModelDto m = (DynamicSelectionModelDto) dto;
    assertEquals("ppa.indicador", m.getProviderKey());
    assertEquals("programa", m.getDependsOn());
    assertTrue(m.isMultipleSelection());
  }

  @Test
  public void instanciaDesserializaPeloType() throws Exception {
    final PropertyDto dto = this.mapper.readValue(
      "{\"type\":\"DynamicSelection\",\"idPropertyModel\":7,\"value\":\"5001,5002\",\"label\":\"Programa 5001 - A; Programa 5002 - B\"}",
      PropertyDto.class);
    assertTrue(dto instanceof DynamicSelectionDto);
    assertEquals("5001,5002", ((DynamicSelectionDto) dto).getValue());
    assertEquals("DynamicSelection", dto.getType());
  }

  @Test
  public void registrosDeTipoMapeiamEntidadeParaDto() {
    final DynamicSelectionModel model = new DynamicSelectionModel();
    model.setName("programa");
    model.setLabel("Programa");
    model.setSortIndex(0L);
    model.setProviderKey("ppa.programa");
    final PropertyModelDto modelDto = PropertyModelInstanceType.map(model);
    assertTrue(modelDto instanceof DynamicSelectionModelDto);
    assertEquals("ppa.programa", ((DynamicSelectionModelDto) modelDto).getProviderKey());

    final DynamicSelection valor = new DynamicSelection();
    valor.setDriver(model);
    valor.setValue("5001");
    valor.setLabel("Programa 5001 - Gestao Dinamica e Eficiente");
    final DynamicSelectionDto valorDto = PropertyInstanceType.createFrom(valor);
    assertEquals("5001", valorDto.getValue());
    assertEquals("Programa 5001 - Gestao Dinamica e Eficiente", valorDto.getLabel());
  }

  @Test
  public void snapshotLevaCodigoERotulo() {
    final DynamicSelection valor = new DynamicSelection();
    valor.setValue("1496");
    valor.setLabel("Acao 1496 - 5001 - Radio e TV Tabajara");
    final DynamicSelection copia = valor.snapshot();
    assertEquals("1496", copia.getValue());
    assertEquals("Acao 1496 - 5001 - Radio e TV Tabajara", copia.getLabel());
    assertFalse(valor.hasChanges(copia));
  }

  @Test
  public void splitDoControllerIgnoraVaziosEEspacos() throws Exception {
    final java.lang.reflect.Method split = DynamicSelectionController.class.getDeclaredMethod("split", String.class);
    split.setAccessible(true);
    assertEquals(Arrays.asList("5001", "5002"), split.invoke(null, " 5001, ,5002 "));
    assertEquals(Collections.emptyList(), split.invoke(null, (Object) null));
  }

}
