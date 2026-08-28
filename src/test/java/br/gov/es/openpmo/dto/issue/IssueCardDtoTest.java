package br.gov.es.openpmo.dto.issue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import br.gov.es.openpmo.model.issue.Issue;
import br.gov.es.openpmo.model.issue.NatureOfIssue;
import br.gov.es.openpmo.model.issue.StatusOfIssue;
import br.gov.es.openpmo.model.risk.Importance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * SA-673 / US-005 — a listagem de ocorrencias precisa expor a natureza.
 *
 * O campo sempre existiu em {@link Issue}, mas nao era exposto neste DTO. O front recebia
 * `nature` undefined, e o resultado foi um bug que passou pelo deploy: todo card exibia o
 * icone de problema, inclusive os beneficios — icone errado parecendo certo.
 *
 * Estes testes travam o campo no contrato. Se alguem remover `nature` do IssueCardDto, o
 * icone volta a mentir; aqui a suite quebra antes.
 */
public class IssueCardDtoTest {

  private ObjectMapper objectMapper;

  @Before
  public void setUp() {
    this.objectMapper = Jackson2ObjectMapperBuilder.json().build();
  }

  private static Issue issue(final NatureOfIssue nature) {
    final Issue issue = new Issue();
    issue.setId(625L);
    issue.setName("Demora na emissao do licenciamento ambiental");
    issue.setImportance(Importance.HIGH);
    issue.setStatus(StatusOfIssue.OPEN);
    issue.setNature(nature);
    return issue;
  }

  @Test
  public void deveCarregarANaturezaDoProblema() {
    final IssueCardDto dto = IssueCardDto.of(issue(NatureOfIssue.PROBLEM));

    assertEquals(NatureOfIssue.PROBLEM, dto.getNature());
  }

  @Test
  public void deveCarregarANaturezaDoBeneficio() {
    final IssueCardDto dto = IssueCardDto.of(issue(NatureOfIssue.BENEFIT));

    assertEquals(NatureOfIssue.BENEFIT, dto.getNature());
  }

  @Test
  public void aNaturezaDeveSairNoJsonDaListagem() throws Exception {
    final String json = this.objectMapper.writeValueAsString(
      IssueCardDto.of(issue(NatureOfIssue.BENEFIT))
    );

    assertTrue(
      "o front le `nature` para escolher o icone; JSON gerado: " + json,
      json.contains("\"nature\":\"BENEFIT\"")
    );
  }

  @Test
  public void naoDevePerderOsCamposQueJaExistiam() throws Exception {
    final String json = this.objectMapper.writeValueAsString(
      IssueCardDto.of(issue(NatureOfIssue.PROBLEM))
    );

    assertTrue(json.contains("\"id\":625"));
    assertTrue(json.contains("\"name\":"));
    assertTrue(json.contains("\"importance\":\"HIGH\""));
    assertTrue(json.contains("\"status\":\"OPEN\""));
  }

  @Test
  public void naturezaNulaNaoQuebraAListagem() throws Exception {
    final IssueCardDto dto = IssueCardDto.of(issue(null));

    assertNotNull(this.objectMapper.writeValueAsString(dto));
  }
}
