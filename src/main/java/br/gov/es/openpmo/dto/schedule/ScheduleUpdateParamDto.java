package br.gov.es.openpmo.dto.schedule;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Corpo do PUT /schedules/{id}. O inicio do cronograma nao e editavel; valores nulos (actualWork e custos) mantem o total atual.
 */
public class ScheduleUpdateParamDto {

  @NotNull
  private LocalDate end;

  @NotNull
  @Min(value = 0)
  private BigDecimal plannedWork;

  @Min(value = 0)
  private BigDecimal actualWork;

  @NotNull
  private DistributionStrategy distribution;

  @Valid
  private Set<CostSchedule> costs;

  public LocalDate getEnd() {
    return this.end;
  }

  public void setEnd(final LocalDate end) {
    this.end = end;
  }

  public BigDecimal getPlannedWork() {
    return this.plannedWork;
  }

  public void setPlannedWork(final BigDecimal plannedWork) {
    this.plannedWork = plannedWork;
  }

  public BigDecimal getActualWork() {
    return this.actualWork;
  }

  public void setActualWork(final BigDecimal actualWork) {
    this.actualWork = actualWork;
  }

  public DistributionStrategy getDistribution() {
    return this.distribution;
  }

  public void setDistribution(final DistributionStrategy distribution) {
    this.distribution = distribution;
  }

  public Set<CostSchedule> getCosts() {
    return this.costs;
  }

  public void setCosts(final Set<CostSchedule> costs) {
    this.costs = costs;
  }

}
