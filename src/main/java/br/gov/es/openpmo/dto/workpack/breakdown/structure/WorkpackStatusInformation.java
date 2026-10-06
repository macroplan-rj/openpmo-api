package br.gov.es.openpmo.dto.workpack.breakdown.structure;

import org.springframework.data.neo4j.annotation.QueryResult;

/**
 * Situacao atual de um workpack na EAP: valor da propriedade de situacao, opcoes do
 * SelectionModel que a define (string crua, ver SelectionPossibleValues) e se ha linha de
 * base proposta aguardando aprovacao.
 */
@QueryResult
public class WorkpackStatusInformation {
  private String status;

  private String possibleValues;

  private boolean pendingBaseline;

  public String getStatus() {
    return status;
  }
  public void setStatus(String status) {
    this.status = status;
  }

  public String getPossibleValues() {
    return possibleValues;
  }
  public void setPossibleValues(String possibleValues) {
    this.possibleValues = possibleValues;
  }

  public boolean isPendingBaseline() {
    return pendingBaseline;
  }
  public void setPendingBaseline(boolean pendingBaseline) {
    this.pendingBaseline = pendingBaseline;
  }
}
