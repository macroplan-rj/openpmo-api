package br.gov.es.openpmo.dto.schedule;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Corpo do PUT /schedules/{id}. Valores nulos (start, actualWork e custos) mantem o valor atual.
 * O inicio so pode mudar em cronograma sem linha de base.
 */
public class ScheduleUpdateParamDto {

  private LocalDate start;

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

  public LocalDate getStart() {
    return this.start;
  }

  public void setStart(final LocalDate start) {
    this.start = start;
  }

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
