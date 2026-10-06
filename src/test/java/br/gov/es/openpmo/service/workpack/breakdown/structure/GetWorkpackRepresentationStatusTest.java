package br.gov.es.openpmo.service.workpack.breakdown.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import br.gov.es.openpmo.dto.workpack.breakdown.structure.WorkpackRepresentation;
import br.gov.es.openpmo.dto.workpack.breakdown.structure.WorkpackStatusInformation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.Test;

/** SD-10604 — situacao, opcoes e baseline pendente na representacao da EAP. */
public class GetWorkpackRepresentationStatusTest {

  private static WorkpackStatusInformation info(String status, String possibleValues, boolean pending) {
    final WorkpackStatusInformation info = new WorkpackStatusInformation();
    info.setStatus(status);
    info.setPossibleValues(possibleValues);
    info.setPendingBaseline(pending);
    return info;
  }

  @Test
  public void deveUsarAPropriedadeCertaPorTipo() {
    assertEquals("Situação", GetWorkpackRepresentation.statusPropertyModelName("Deliverable"));
    assertEquals("Status", GetWorkpackRepresentation.statusPropertyModelName("Project"));
  }

  @Test
  public void deveMapearEntregaComOpcoesEBaselinePendente() {
    final WorkpackRepresentation representation = new WorkpackRepresentation();

    GetWorkpackRepresentation.applyStatusInformation(
      representation,
      "Deliverable",
      info("Em execução", "Cancelada,Concluída,Em execução,Paralisada,Em planejamento", true)
    );

    assertEquals("Em execução", representation.getDeliverableStatus());
    assertNull(representation.getProjectStatus());
    assertEquals(
      Arrays.asList("Cancelada", "Concluída", "Em execução", "Paralisada", "Em planejamento"),
      representation.getStatusOptions()
    );
    assertTrue(representation.getPendingBaseline());
  }

  @Test
  public void deveMapearProjetoNoCampoDoProjeto() {
    final WorkpackRepresentation representation = new WorkpackRepresentation();

    GetWorkpackRepresentation.applyStatusInformation(
      representation, "Project", info("Execução", "Estruturação;Execução;Concluído", false));

    assertEquals("Execução", representation.getProjectStatus());
    assertNull(representation.getDeliverableStatus());
    assertEquals(Arrays.asList("Estruturação", "Execução", "Concluído"), representation.getStatusOptions());
    assertFalse(representation.getPendingBaseline());
  }

  @Test
  public void naoDeveSerializarCamposQuandoNaoHaInformacao() throws Exception {
    final WorkpackRepresentation representation = new WorkpackRepresentation();
    GetWorkpackRepresentation.applyStatusInformation(representation, "Deliverable", null);

    final String json = new ObjectMapper().writeValueAsString(representation);

    assertFalse(json, json.contains("statusOptions"));
    assertFalse(json, json.contains("pendingBaseline"));
  }
}
