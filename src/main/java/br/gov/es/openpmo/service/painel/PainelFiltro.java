package br.gov.es.openpmo.service.painel;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Filtros cumulativos do Painel Estrategico; listas vem separadas por virgula. */
public final class PainelFiltro {

  private final Set<String> areas;
  private final Set<String> objetivos;
  private final Set<String> projetos;
  private final Set<String> instituicoes;
  private final boolean total;
  private final LocalDate posicao;
  private final LocalDate periodoInicio;
  private final LocalDate periodoFim;
  private final String modo;

  private PainelFiltro(final Set<String> areas, final Set<String> objetivos, final Set<String> projetos,
                       final Set<String> instituicoes, final boolean total, final LocalDate posicao,
                       final LocalDate periodoInicio, final LocalDate periodoFim, final String modo) {
    this.areas = areas;
    this.objetivos = objetivos;
    this.projetos = projetos;
    this.instituicoes = instituicoes.stream().map(i -> i.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    this.total = total;
    this.posicao = posicao;
    this.periodoInicio = periodoInicio;
    this.periodoFim = periodoFim;
    this.modo = modo;
  }

  public static PainelFiltro de(final String area, final String objetivo, final String projeto, final String instituicao,
                                final String horizonte, final LocalDate dataPosicao, final LocalDate periodoInicio,
                                final LocalDate periodoFim, final String modo) {
    return new PainelFiltro(
      PainelService.conjunto(area), PainelService.conjunto(objetivo), PainelService.conjunto(projeto),
      PainelService.conjunto(instituicao), "TOTAL".equalsIgnoreCase(horizonte),
      dataPosicao == null ? LocalDate.now() : dataPosicao, periodoInicio, periodoFim, modo);
  }

  static PainelFiltro semFiltro(final LocalDate posicao) {
    return new PainelFiltro(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                            Collections.emptySet(), false, posicao, null, null, null);
  }

  /** O realizado e o estimado "ate hoje" da tabela de Onde atuar nao dependem do seletor. */
  PainelFiltro comHorizonteAteHoje() {
    return new PainelFiltro(this.areas, this.objetivos, this.projetos, this.instituicoes, false, this.posicao,
                            this.periodoInicio, this.periodoFim, this.modo);
  }

  boolean aceita(final PainelService.Projeto p) {
    return (this.areas.isEmpty() || (p.areaId != null && this.areas.contains(String.valueOf(p.areaId))))
           && (this.objetivos.isEmpty() || p.objetivos.stream().anyMatch(this.objetivos::contains))
           && (this.projetos.isEmpty() || this.projetos.contains(String.valueOf(p.id)))
           && (this.instituicoes.isEmpty()
               || (p.instituicao != null && this.instituicoes.contains(p.instituicao.toLowerCase(Locale.ROOT))));
  }

  boolean noPeriodo(final LocalDate data) {
    if (data == null) {
      return false;
    }
    return (this.periodoInicio == null || !data.isBefore(this.periodoInicio))
           && (this.periodoFim == null || !data.isAfter(this.periodoFim));
  }

  boolean total() {
    return this.total;
  }

  LocalDate posicao() {
    return this.posicao;
  }

  String modo() {
    return this.modo;
  }

}
