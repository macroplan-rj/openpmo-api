package br.gov.es.openpmo.dto.issue;

import br.gov.es.openpmo.model.issue.Issue;
import br.gov.es.openpmo.model.issue.NatureOfIssue;
import br.gov.es.openpmo.model.issue.StatusOfIssue;
import br.gov.es.openpmo.model.risk.Importance;
import org.springframework.data.neo4j.annotation.QueryResult;

@QueryResult
public class IssueCardDto {

  private final Long id;
  private final String name;
  private final Importance importance;
  private final StatusOfIssue status;

  /**
   * Natureza da ocorrencia — problema ou beneficio (US-005 / SA-673).
   *
   * O campo sempre existiu em {@link Issue}, mas nao era exposto nesta listagem, entao o
   * card nao tinha como distinguir as duas naturezas. Sem ele o front nao consegue
   * escolher o icone.
   */
  private final NatureOfIssue nature;

  public IssueCardDto(
    final Long id,
    final String name,
    final Importance importance,
    final StatusOfIssue status,
    final NatureOfIssue nature
  ) {
    this.id = id;
    this.name = name;
    this.importance = importance;
    this.status = status;
    this.nature = nature;
  }

  public static IssueCardDto of(final Issue issue) {
    return new IssueCardDto(
      issue.getId(),
      issue.getName(),
      issue.getImportance(),
      issue.getStatus(),
      issue.getNature()
    );
  }

  public Long getId() {
    return this.id;
  }

  public String getName() {
    return this.name;
  }

  public Importance getImportance() {
    return this.importance;
  }

  public StatusOfIssue getStatus() {
    return this.status;
  }

  public NatureOfIssue getNature() {
    return this.nature;
  }

}
