package br.gov.es.openpmo.dto.workpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * US-003 — contrato do campo deliveryForecastDate no payload de /workpacks/parent.
 *
 * O front formata com o DatePipe do Angular, que so parseia ISO date-only ('yyyy-MM-dd')
 * como horario local. Se a serializacao mudar para o array [2026,12,1] do Jackson (o
 * padrao para LocalDate sem @JsonFormat) ou para um instante com fuso, a data no card
 * quebra ou desloca um dia. Este teste fixa o formato.
 */
public class WorkpackDetailParentDtoSerializationTest {

  private ObjectMapper objectMapper;

  /** Subtipo concreto: WorkpackDetailParentDto e abstrata. */
  private DeliverableDetailParentDto deliverable;

  @Before
  public void setUp() {
    // Jackson2ObjectMapperBuilder e o que o Spring Boot usa para montar o mapper do MVC:
    // registra o JavaTimeModule e desliga WRITE_DATES_AS_TIMESTAMPS. Um `new
    // ObjectMapper()` pelado NAO faz isso e serializa LocalDate como bean
    // ({"year":2026,"month":"AUGUST",...}), que nao e o que a API devolve de verdade.
    this.objectMapper = Jackson2ObjectMapperBuilder.json().build();
    this.deliverable = new DeliverableDetailParentDto();
    this.deliverable.setId(1467L);
    this.deliverable.setName("Abertura de pontos municipais");
  }

  @Test
  public void deveSerializarAPrevisaoComoIsoDateOnly() throws Exception {
    this.deliverable.setDeliveryForecastDate(LocalDate.of(2026, 8, 14));

    final String json = this.objectMapper.writeValueAsString(this.deliverable);

    assertTrue(
      "esperava \"deliveryForecastDate\":\"2026-08-14\" mas veio: " + json,
      json.contains("\"deliveryForecastDate\":\"2026-08-14\"")
    );
  }

  @Test
  public void naoDeveSerializarComoArrayDeInteiros() throws Exception {
    this.deliverable.setDeliveryForecastDate(LocalDate.of(2026, 12, 1));

    final String json = this.objectMapper.writeValueAsString(this.deliverable);

    // Sem @JsonFormat o Jackson escreveria [2026,12,1], que o DatePipe nao entende.
    assertTrue("serializou como array: " + json, !json.contains("[2026,12,1]"));
    assertTrue(json.contains("\"deliveryForecastDate\":\"2026-12-01\""));
  }

  @Test
  public void devePreservarODiaSemDeslocarPorFuso() throws Exception {
    // 01/12 nao pode virar 30/11 na ida para o JSON.
    this.deliverable.setDeliveryForecastDate(LocalDate.of(2026, 12, 1));

    final String json = this.objectMapper.writeValueAsString(this.deliverable);

    assertTrue(json.contains("2026-12-01"));
    assertTrue("deslocou um dia: " + json, !json.contains("2026-11-30"));
  }

  @Test
  public void semPrevisaoOCampoVaiNuloENaoQuebraOPayload() throws Exception {
    this.deliverable.setDeliveryForecastDate(null);

    final String json = this.objectMapper.writeValueAsString(this.deliverable);

    assertTrue(
      "esperava o campo nulo, veio: " + json,
      json.contains("\"deliveryForecastDate\":null") || !json.contains("deliveryForecastDate")
    );
  }

  @Test
  public void oGetterDevolveOQueFoiSetado() {
    final LocalDate previsao = LocalDate.of(2027, 6, 30);
    this.deliverable.setDeliveryForecastDate(previsao);

    assertEquals(previsao, this.deliverable.getDeliveryForecastDate());
  }
}
