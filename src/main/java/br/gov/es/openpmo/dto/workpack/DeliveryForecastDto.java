package br.gov.es.openpmo.dto.workpack;

import org.springframework.data.neo4j.annotation.QueryResult;

/**
 * Previsao de entrega de um workpack, para exibicao nos cards e tiras da EAP (US-003).
 *
 * <p>Projecao de consulta em lote: uma unica ida ao banco resolve a listagem inteira de
 * {@code GET /workpacks/parent}, sem consulta por card.
 *
 * <p>A data trafega como String no formato {@code yyyy-MM-dd} porque e assim que
 * {@code Schedule.end} e {@code Milestone.date} estao gravados no Neo4j.
 */
@QueryResult
public class DeliveryForecastDto {

  private Long idWorkpack;

  private String forecastDate;

  public DeliveryForecastDto() {
  }

  public DeliveryForecastDto(final Long idWorkpack, final String forecastDate) {
    this.idWorkpack = idWorkpack;
    this.forecastDate = forecastDate;
  }

  public Long getIdWorkpack() {
    return this.idWorkpack;
  }

  public void setIdWorkpack(final Long idWorkpack) {
    this.idWorkpack = idWorkpack;
  }

  public String getForecastDate() {
    return this.forecastDate;
  }

  public void setForecastDate(final String forecastDate) {
    this.forecastDate = forecastDate;
  }
}
