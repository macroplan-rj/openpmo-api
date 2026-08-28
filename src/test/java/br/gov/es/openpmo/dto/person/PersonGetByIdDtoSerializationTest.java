package br.gov.es.openpmo.dto.person;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * SA-639 / US-006 — diagnostico: com que nome a flag de membro do CCB sai no JSON.
 *
 * O menu do front esconde o acesso ao comite com
 * {@code *ngIf="currentUserInfo?.isCcbMember"} (nav-menu.component.html:28 e
 * mobile-menu.component.html:42), lendo a resposta de GET /persons/{key}, que devolve
 * {@link PersonGetByIdDto}.
 *
 * No DTO o campo e anotado {@code @JsonProperty("isCcbMember")}, mas o getter chama-se
 * {@code getCcbMember()} — nome implicito "ccbMember". Se o Jackson emitir a propriedade
 * pelo getter, o JSON traz "ccbMember" e o template le "isCcbMember", que chega undefined
 * e esconde o item do menu sem erro nenhum. Este teste mostra qual nome sai de fato.
 */
public class PersonGetByIdDtoSerializationTest {

  private ObjectMapper objectMapper;
  private PersonGetByIdDto dto;

  @Before
  public void setUp() {
    // Mesma configuracao que o Spring Boot usa no MVC.
    this.objectMapper = Jackson2ObjectMapperBuilder.json().build();
    this.dto = new PersonGetByIdDto();
    this.dto.setCcbMember(Boolean.TRUE);
  }

  @Test
  public void membroDoCcbDeveSairComONomeQueOMenuLe() throws Exception {
    final String json = this.objectMapper.writeValueAsString(this.dto);

    System.out.println("### JSON de PersonGetByIdDto com ccbMember=true:");
    System.out.println(json);

    assertTrue(
      "o menu le 'isCcbMember'; JSON gerado: " + json,
      json.contains("\"isCcbMember\":true")
    );
  }

  @Test
  public void naoDeveEmitirODuploNome() throws Exception {
    final String json = this.objectMapper.writeValueAsString(this.dto);

    final boolean temIsCcb = json.contains("\"isCcbMember\"");
    final boolean temCcb = json.contains("\"ccbMember\"");

    assertTrue(
      "JSON traz as duas chaves ao mesmo tempo, o que denuncia campo e getter "
        + "com nomes divergentes: " + json,
      !(temIsCcb && temCcb)
    );
  }
}
