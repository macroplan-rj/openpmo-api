package br.gov.es.openpmo.service.painel;

import br.gov.es.openpmo.exception.RegistroNaoEncontradoException;
import br.gov.es.openpmo.repository.painel.PainelRepository;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProvider;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProviderRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Painel Estrategico (SD #10795 gerencial, #10796 executivo/entregas, #10797 executivo/onde atuar, #10798 visao por
 * projeto). Busca os fatos da carteira com {@link PainelRepository} e calcula cada tela aqui. Regras e contrato:
 * docs/API_PAINEL_ESTRATEGICO.md na raiz do OpenPMO.
 */
@Service
public class PainelService {

  static final List<String> TIPOS_ENTREGA = List.of(
    "Obra", "Equipamento", "Plano/estudo/pesquisa", "Serviço", "Serviços complementares", "Normatização");
  static final List<String> SITUACOES_ENTREGA = List.of(
    "Planejamento", "Em execução", "Paralisada", "Cancelada", "Concluída");
  static final List<String> SITUACOES_MARCO = List.of("No prazo", "Concluído", "Concluído atrasado", "Atrasado");
  static final List<String> TIPOS_MARCO = List.of(
    "Convênio", "Licenciamento ambiental", "Projeto de engenharia", "Obra", "Licitação", "PPP", "PMI",
    "Sistema de informação", "Normatização");

  private static final List<String> NOMES_INSTITUICAO = List.of(
    "unidade gestora", "instituição responsável", "instituicao responsavel", "órgão responsável", "orgao responsavel");
  private static final String PAPEL_INSTITUICAO = "instituição responsável";
  private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final PainelRepository repository;
  private final DynamicSelectionProviderRegistry dynamicSelection;

  public PainelService(final PainelRepository repository, final DynamicSelectionProviderRegistry dynamicSelection) {
    this.repository = repository;
    this.dynamicSelection = dynamicSelection;
  }

  // ===================================================================================== endpoints

  public Map<String, Object> filtros(final PainelFiltro filtro) {
    final Carteira c = this.carregar(PainelFiltro.semFiltro(filtro.posicao()), false);
    final Map<String, Object> r = cabecalho(filtro);
    r.put("areas", c.projetos.stream()
      .filter(p -> p.areaId != null)
      .collect(Collectors.toMap(p -> p.areaId, p -> p.area, (a, b) -> a, TreeMap::new))
      .entrySet().stream().map(e -> mapa("id", e.getKey(), "nome", e.getValue())).collect(Collectors.toList()));
    final Set<String> codigos = c.projetos.stream().flatMap(p -> p.objetivos.stream()).collect(Collectors.toCollection(LinkedHashSet::new));
    final Map<String, String> nomes = this.nomesObjetivos(codigos);
    r.put("objetivos", codigos.stream().sorted()
      .map(cod -> mapa("codigo", cod, "nome", nomes.getOrDefault(cod, cod))).collect(Collectors.toList()));
    r.put("projetos", c.projetos.stream().sorted(Comparator.comparing(p -> p.nome))
      .map(p -> mapa("id", p.id, "nome", p.nome, "areaId", p.areaId)).collect(Collectors.toList()));
    r.put("instituicoes", c.projetos.stream().map(p -> p.instituicao).filter(Objects::nonNull).distinct().sorted()
      .map(i -> mapa("nome", i)).collect(Collectors.toList()));
    return r;
  }

  public Map<String, Object> carteira(final PainelFiltro filtro) {
    final Carteira c = this.carregar(filtro, true);
    final LocalDate pos = filtro.posicao();
    final Map<String, Object> r = cabecalho(filtro);
    r.put("projetosNaCarteira", projetosNaCarteira(c));
    final List<Entrega> entregas = c.entregas();
    r.put("entregas", mapa(
      "total", entregas.size(),
      "concluidas", entregas.stream().filter(Entrega::concluida).count(),
      "atrasadas", entregas.stream().filter(e -> e.atrasada(pos)).count()));
    final List<Marco> marcos = c.marcos();
    r.put("marcosCriticos", mapa(
      "total", marcos.size(),
      "concluidos", marcos.stream().filter(m -> m.concluido).count(),
      "atrasados", marcos.stream().filter(m -> "Atrasado".equals(m.situacao(pos))).count()));
    r.put("financeiro", financeiro(entregas, filtro));
    r.put("composicaoEntregas", mapa(
      "porTipo", distribuicao(entregas, e -> e.tipo, TIPOS_ENTREGA, "tipo"),
      "porSituacao", distribuicao(entregas, e -> e.situacao, SITUACOES_ENTREGA, "situacao")));
    r.put("marcosCriticosComposicao", mapa(
      "porSituacao", distribuicao(marcos, m -> m.situacao(pos), SITUACOES_MARCO, "situacao"),
      "porTipo", distribuicao(marcos, m -> m.tipo, TIPOS_MARCO, "tipo")));
    r.put("projetosPorInstituicao", projetosPorInstituicao(c));
    r.put("territorio", territorio(entregas));
    r.put("valorPorUnidadeOrcamentaria", valorPorUo(entregas, filtro));
    r.put("projetos", c.projetos.stream().map(p -> {
      final List<Marco> ms = c.marcosDe(p.id);
      final long atrasados = ms.stream().filter(m -> "Atrasado".equals(m.situacao(pos))).count();
      final Map<String, Object> f = financeiro(c.entregasDe(p.id), filtro);
      final List<Map<String, Object>> atrasos = new ArrayList<>();
      c.entregasDe(p.id).stream().filter(e -> e.atrasada(pos)).forEach(e -> atrasos.add(mapa(
        "tipo", "ENTREGA", "id", e.id, "nome", e.nome, "dataPrevista", e.termino, "diasAtraso", e.diasAtraso(pos))));
      ms.stream().filter(m -> "Atrasado".equals(m.situacao(pos))).forEach(m -> atrasos.add(mapa(
        "tipo", "MARCO", "id", m.id, "nome", m.nome, "entrega", c.nomeEntrega(m.entregaId),
        "dataPrevista", m.data, "diasAtraso", m.diasAtraso(pos))));
      return mapa(
        "id", p.id, "nome", p.nome, "area", p.area,
        "estimado", f.get("estimado"), "pagoAteHoje", f.get("realizado"),
        "marcosAtrasados", atrasados, "marcosTotal", ms.size(), "percentualMarcosAtrasados", percentual(atrasados, ms.size()),
        "atrasos", atrasos);
    }).collect(Collectors.toList()));
    return r;
  }

  public Map<String, Object> executivoEntregas(final PainelFiltro filtro) {
    final Carteira c = this.carregar(filtro, true);
    final LocalDate pos = filtro.posicao();
    final Map<String, Object> r = cabecalho(filtro);
    r.put("projetosNaCarteira", projetosNaCarteira(c));
    final List<Entrega> entregas = c.entregas();
    final long concluidas = entregas.stream().filter(Entrega::concluida).count();
    r.put("concluidasNaCarteira", mapa(
      "concluidas", concluidas, "registradas", entregas.size(), "percentual", percentual(concluidas, entregas.size())));
    final List<Entrega> noPeriodo = entregas.stream().filter(e -> e.concluida() && filtro.noPeriodo(e.dataConclusao()))
      .collect(Collectors.toList());
    r.put("concluidasNoPeriodo", mapa(
      "concluidas", noPeriodo.size(), "registradas", entregas.size(), "percentual", percentual(noPeriodo.size(), entregas.size())));
    r.put("territorio", territorio(noPeriodo));
    final boolean proximos = "PROXIMOS_DIAS".equalsIgnoreCase(filtro.modo());
    final LocalDate limite = pos.plusDays(60);
    final List<Map<String, Object>> projetos = new ArrayList<>();
    for (final Projeto p : c.projetos) {
      final List<Entrega> todas = c.entregasDe(p.id);
      final List<Entrega> concl = todas.stream().filter(e -> e.concluida() && filtro.noPeriodo(e.dataConclusao()))
        .collect(Collectors.toList());
      final List<Entrega> prox = todas.stream()
        .filter(e -> !e.concluida() && !e.cancelada() && e.termino != null && e.termino.isAfter(pos) && !e.termino.isAfter(limite))
        .sorted(Comparator.comparing(e -> e.termino)).collect(Collectors.toList());
      final List<Entrega> lista = proximos ? prox : concl;
      if (lista.isEmpty()) {
        continue;
      }
      projetos.add(mapa(
        "id", p.id, "nome", p.nome, "instituicao", p.instituicao,
        "concluidas", concl.size(), "percentualConclusao", percentual(concl.size(), todas.size()),
        "proximasEntregas", prox.size(), "dataProximaEntrega", prox.isEmpty() ? null : prox.get(0).termino,
        "entregas", lista.stream().map(e -> mapa(
          "id", e.id, "nome", e.nome, "tipo", e.tipo, "local", e.local,
          "data", proximos ? e.termino : e.dataConclusao())).collect(Collectors.toList())));
    }
    if (proximos) {
      projetos.sort(Comparator.comparing(m -> (LocalDate) m.get("dataProximaEntrega")));
    } else {
      projetos.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("percentualConclusao")).reversed());
    }
    r.put("projetos", projetos);
    return r;
  }

  public Map<String, Object> executivoOndeAtuar(final PainelFiltro filtro) {
    final Carteira c = this.carregar(filtro, true);
    final LocalDate pos = filtro.posicao();
    final Map<String, Object> r = cabecalho(filtro);
    r.put("projetosNaCarteira", projetosNaCarteira(c));
    r.put("financeiro", financeiro(c.entregas(), filtro));
    final Map<String, Long> faixas = new LinkedHashMap<>();
    faixas.put("noPrazo", 0L);
    faixas.put("atencao", 0L);
    faixas.put("critico", 0L);
    faixas.put("foraDoUniverso", 0L);
    final Map<Long, Indice> indices = new HashMap<>();
    for (final Projeto p : c.projetos) {
      final Indice i = Indice.de(c.marcosDe(p.id), pos);
      indices.put(p.id, i);
      faixas.merge(i.chaveContagem(), 1L, Long::sum);
    }
    r.put("indiceCumprimento", faixas);
    final Map<Long, Map<String, Object>> municipios = new LinkedHashMap<>();
    final Map<Long, Set<Long>> projetosPorMunicipio = new HashMap<>();
    for (final Marco m : c.marcos()) {
      final Entrega e = c.entrega(m.entregaId);
      if (e == null || e.localId == null) {
        continue;
      }
      municipios.computeIfAbsent(e.localId, id -> mapa("id", id, "nome", e.local));
      projetosPorMunicipio.computeIfAbsent(e.localId, id -> new LinkedHashSet<>()).add(m.projetoId);
    }
    r.put("territorio", mapa("municipios", municipios.values().stream().map(mun -> {
      final Set<Long> ps = projetosPorMunicipio.get((Long) mun.get("id"));
      final long criticos = ps.stream().filter(id -> indices.get(id).critico()).count();
      final Map<String, Object> out = new LinkedHashMap<>(mun);
      out.put("projetos", ps.size());
      out.put("projetosCriticos", criticos);
      out.put("percentualCriticos", percentual(criticos, ps.size()));
      return out;
    }).collect(Collectors.toList())));
    r.put("projetos", c.projetos.stream()
      .filter(p -> indices.get(p.id).noUniverso())
      .sorted(Comparator.comparing((Projeto p) -> indices.get(p.id).indice))
      .map(p -> {
        final Indice i = indices.get(p.id);
        final Map<String, Object> f = financeiro(c.entregasDe(p.id), filtro.comHorizonteAteHoje());
        final Map<String, Object> out = mapa(
          "id", p.id, "nome", p.nome, "dataConclusao", c.dataTermino(p), "instituicao", p.instituicao,
          "estimado", f.get("estimado"), "realizadoAteHoje", f.get("realizado"));
        out.putAll(i.comoMapa());
        out.put("marcosAtrasados", c.marcosDe(p.id).stream().filter(m -> "Atrasado".equals(m.situacao(pos)))
          .map(m -> mapa("id", m.id, "nome", m.nome, "entrega", c.nomeEntrega(m.entregaId),
            "dataPrevista", m.data, "diasAtraso", m.diasAtraso(pos))).collect(Collectors.toList()));
        return out;
      }).collect(Collectors.toList()));
    return r;
  }

  public Map<String, Object> projeto(final Long id, final PainelFiltro filtro) {
    final Carteira c = this.carregar(filtro, true);
    final List<Projeto> ordenados = c.projetos.stream().sorted(Comparator.comparing(p -> p.nome)).collect(Collectors.toList());
    final Projeto p = ordenados.stream().filter(x -> x.id.equals(id)).findFirst()
      .orElseThrow(() -> new RegistroNaoEncontradoException("painel.projeto.notFound"));
    final LocalDate pos = filtro.posicao();
    final List<Entrega> entregas = c.entregasDe(p.id);
    final Map<String, Object> r = cabecalho(filtro);
    r.put("posicaoNaCarteira", mapa("indice", ordenados.indexOf(p) + 1, "total", ordenados.size()));
    r.put("projeto", mapa(
      "id", p.id, "nome", p.nome, "status", p.status, "area", p.area,
      "dataInicio", p.dataInicio, "dataPrevistaTermino", c.dataTermino(p),
      "gerente", p.gerente, "instituicao", p.instituicao));
    r.put("indiceCumprimento", Indice.de(c.marcosDe(p.id), pos).comoMapa());
    r.put("entregas", mapa(
      "concluidas", entregas.stream().filter(Entrega::concluida).count(), "total", entregas.size(),
      "porSituacao", distribuicao(entregas, e -> e.situacao, SITUACOES_ENTREGA, "situacao")));
    r.put("financeiro", financeiro(entregas, filtro));
    final Map<String, Object> terr = territorio(entregas);
    r.put("municipios", mapa("total", terr.get("totalMunicipios"),
      "entregas", entregas.stream().filter(e -> e.localId != null).count(), "lista", terr.get("municipios")));
    final Map<String, String> nomes = this.nomesObjetivos(p.objetivos);
    r.put("alinhamento", mapa(
      "objetivos", p.objetivos.stream().map(cod -> mapa("codigo", cod, "nome", nomes.getOrDefault(cod, cod))).collect(Collectors.toList()),
      "estrategias", p.estrategias, "metas", p.metas, "ods", p.ods));
    r.put("indicadores", this.indicadores(p.id, pos));
    r.put("entregasEMarcos", entregas.stream().map(e -> {
      final Map<String, Object> fis = e.fisico();
      final Map<String, Object> fin = e.financeiro(filtro);
      return mapa(
        "id", e.id, "nome", e.nome, "situacao", e.situacao, "responsavel", e.gerente,
        "previsaoConclusao", e.termino, "atrasada", e.atrasada(pos), "local", e.local,
        "fisico", fis, "financeiro", fin, "avisos", avisos(fis, fin),
        "marcos", c.marcosDe(p.id).stream().filter(m -> e.id.equals(m.entregaId)).map(m -> mapa(
          "id", m.id, "nome", m.nome, "dataPrevista", m.data, "situacao", m.situacao(pos),
          "diasAtraso", "Atrasado".equals(m.situacao(pos)) ? m.diasAtraso(pos) : null)).collect(Collectors.toList()));
    }).collect(Collectors.toList()));
    r.put("riscos", this.riscos(p.id));
    r.put("ocorrencias", this.ocorrencias(p.id));
    return r;
  }

  // ===================================================================================== carga

  private Carteira carregar(final PainelFiltro filtro, final boolean comDetalhe) {
    final List<Projeto> todos = this.repository.projetos().stream().map(Projeto::de).collect(Collectors.toList());
    final List<Projeto> projetos = todos.stream().filter(filtro::aceita).collect(Collectors.toList());
    final Carteira c = new Carteira(projetos);
    if (!comDetalhe || projetos.isEmpty()) {
      return c;
    }
    final List<Long> ids = projetos.stream().map(p -> p.id).collect(Collectors.toList());
    final Map<Long, List<Map<String, Object>>> meses = this.repository.custos(ids).stream()
      .collect(Collectors.groupingBy(m -> numero(m.get("entregaId")).longValue()));
    for (final Map<String, Object> linha : this.repository.entregas(ids)) {
      final Entrega e = Entrega.de(linha, meses.getOrDefault(numero(linha.get("id")).longValue(), List.of()));
      c.adicionar(e);
    }
    for (final Map<String, Object> linha : this.repository.marcos(ids)) {
      final Marco m = Marco.de(linha);
      if (!m.cancelado) {
        c.adicionar(m);
      }
    }
    return c;
  }

  private Map<String, String> nomesObjetivos(final Collection<String> codigos) {
    if (codigos.isEmpty()) {
      return Map.of();
    }
    try {
      return this.dynamicSelection.get("pelp.objetivo").resolve(new ArrayList<>(codigos)).stream()
        .collect(Collectors.toMap(DynamicSelectionProvider.Option::getCode, DynamicSelectionProvider.Option::getLabel, (a, b) -> a));
    } catch (final RuntimeException e) {
      return Map.of();
    }
  }

  private Map<String, Object> indicadores(final Long projeto, final LocalDate pos) {
    final List<Map<String, Object>> lista = new ArrayList<>();
    long emAndamento = 0;
    long abaixo = 0;
    for (final Map<String, Object> i : this.repository.indicadores(projeto)) {
      final LocalDate inicio = data(i.get("inicio"));
      final LocalDate fim = data(i.get("fim"));
      final boolean andamento = (inicio == null || !inicio.isAfter(pos)) && (fim == null || !fim.isBefore(pos));
      final List<Map<String, Object>> periodos = lista(i.get("periodos")).stream()
        .sorted(Comparator.comparing(g -> String.valueOf(g.get("periodo")))).collect(Collectors.toList());
      final String ano = String.valueOf(pos.getYear());
      final double previstoAno = soma(periodos.stream().filter(g -> String.valueOf(g.get("periodo")).startsWith(ano)), "previsto");
      final double realizadoAno = soma(periodos.stream().filter(g -> String.valueOf(g.get("periodo")).startsWith(ano)), "realizado");
      final double previstoTotal = soma(periodos.stream(), "previsto");
      final double realizadoTotal = soma(periodos.stream(), "realizado");
      final boolean abaixoDaMeta = andamento && previstoAno > 0 && realizadoAno < previstoAno;
      if (andamento) {
        emAndamento++;
      }
      if (abaixoDaMeta) {
        abaixo++;
      }
      lista.add(mapa(
        "id", i.get("id"), "nome", i.get("nome"), "fonte", i.get("fonte"),
        "emAndamento", andamento, "abaixoDaMeta", abaixoDaMeta,
        "metaAnoVigente", mapa("previsto", valor(previstoAno), "realizado", valor(realizadoAno), "percentual", percentual(realizadoAno, previstoAno)),
        "metaTotal", mapa("previsto", valor(previstoTotal), "realizado", valor(realizadoTotal), "percentual", percentual(realizadoTotal, previstoTotal)),
        "vigenciaInicio", inicio, "vigenciaFim", fim, "periodicidade", i.get("periodicidade"), "descricao", i.get("descricao"),
        "periodos", periodos.stream().map(g -> mapa("periodo", g.get("periodo"), "previsto", g.get("previsto"), "realizado", g.get("realizado")))
          .collect(Collectors.toList()),
        "ultimaAtualizacao", periodos.stream().map(g -> data(g.get("ultimaAtualizacao"))).filter(Objects::nonNull)
          .max(Comparator.naturalOrder()).orElse(null)));
    }
    return mapa("emAndamento", emAndamento, "abaixoDaMeta", abaixo, "lista", lista);
  }

  private Map<String, Object> riscos(final Long projeto) {
    final Map<String, List<Map<String, Object>>> porImportancia = porImportancia();
    for (final Map<String, Object> r : this.repository.riscos(projeto)) {
      final List<Map<String, Object>> planos = lista(r.get("planos"));
      final Map<String, Object> plano = planos.isEmpty() ? null : planos.get(0);
      porImportancia.get(importancia(r.get("importancia"))).add(mapa(
        "id", r.get("id"), "nome", r.get("nome"), "descricao", r.get("descricao"),
        "natureza", traduzir(r.get("natureza")), "status", traduzir(r.get("status")), "dataProvavel", r.get("dataProvavel"),
        "planoResposta", plano == null ? null : mapa(
          "nome", plano.get("nome"), "estrategia", traduzir(plano.get("estrategia")),
          "responsavel", String.join(", ", textos(plano.get("responsaveis"))),
          "situacao", traduzir(plano.get("situacao")), "quando", traduzir(plano.get("quando")))));
    }
    return new LinkedHashMap<>(porImportancia);
  }

  private Map<String, Object> ocorrencias(final Long projeto) {
    final Map<String, List<Map<String, Object>>> porImportancia = porImportancia();
    for (final Map<String, Object> o : this.repository.ocorrencias(projeto)) {
      porImportancia.get(importancia(o.get("importancia"))).add(mapa(
        "id", o.get("id"), "nome", o.get("nome"), "descricao", o.get("descricao"),
        "natureza", traduzir(o.get("natureza")), "status", traduzir(o.get("status"))));
    }
    return new LinkedHashMap<>(porImportancia);
  }

  // ===================================================================================== componentes

  private static Map<String, Object> cabecalho(final PainelFiltro filtro) {
    return mapa("dataPosicao", filtro.posicao());
  }

  private static Map<String, Object> projetosNaCarteira(final Carteira c) {
    final Map<String, Map<String, Object>> porArea = new LinkedHashMap<>();
    for (final Projeto p : c.projetos) {
      final String chave = String.valueOf(p.areaId);
      porArea.computeIfAbsent(chave, k -> mapa("areaId", p.areaId, "area", p.area, "projetos", 0));
      porArea.get(chave).merge("projetos", 1, (a, b) -> (Integer) a + (Integer) b);
    }
    return mapa("totalProjetos", c.projetos.size(), "totalEntregas", c.entregas().size(),
      "porArea", new ArrayList<>(porArea.values()));
  }

  private static Map<String, Object> financeiro(final Collection<Entrega> entregas, final PainelFiltro filtro) {
    double estimado = 0;
    double reprogramado = 0;
    double realizado = 0;
    for (final Entrega e : entregas) {
      final Map<String, Object> f = e.financeiro(filtro);
      estimado += ((BigDecimal) f.get("estimado")).doubleValue();
      reprogramado += ((BigDecimal) f.get("reprogramado")).doubleValue();
      realizado += ((BigDecimal) f.get("realizado")).doubleValue();
    }
    return mapa("estimado", valor(estimado), "reprogramado", valor(reprogramado), "realizado", valor(realizado));
  }

  private static <T> List<Map<String, Object>> distribuicao(
    final Collection<T> itens, final Function<T, String> chave, final List<String> oficiais, final String nome
  ) {
    final Map<String, Long> contagem = new LinkedHashMap<>();
    oficiais.forEach(o -> contagem.put(o, 0L));
    for (final T item : itens) {
      contagem.merge(normalizar(chave.apply(item), oficiais), 1L, Long::sum);
    }
    return contagem.entrySet().stream()
      .map(e -> mapa(nome, e.getKey(), "quantidade", e.getValue(), "percentual", percentual(e.getValue(), itens.size())))
      .collect(Collectors.toList());
  }

  private static List<Map<String, Object>> projetosPorInstituicao(final Carteira c) {
    final Map<String, Long> contagem = c.projetos.stream()
      .collect(Collectors.groupingBy(p -> p.instituicao == null ? "Não informada" : p.instituicao, Collectors.counting()));
    return contagem.entrySet().stream()
      .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
      .map(e -> mapa("instituicao", e.getKey(), "projetos", e.getValue(), "percentual", percentual(e.getValue(), c.projetos.size())))
      .collect(Collectors.toList());
  }

  private static Map<String, Object> territorio(final Collection<Entrega> entregas) {
    final Map<Long, Map<String, Object>> municipios = new LinkedHashMap<>();
    for (final Entrega e : entregas) {
      if (e.localId == null) {
        continue;
      }
      municipios.computeIfAbsent(e.localId, id -> mapa("id", id, "nome", e.local, "entregas", 0L));
      municipios.get(e.localId).merge("entregas", 1L, (a, b) -> (Long) a + (Long) b);
    }
    return mapa("totalMunicipios", municipios.size(), "municipios", new ArrayList<>(municipios.values()));
  }

  private static List<Map<String, Object>> valorPorUo(final Collection<Entrega> entregas, final PainelFiltro filtro) {
    final Map<String, double[]> soma = new LinkedHashMap<>();
    final Map<String, Object[]> nomes = new HashMap<>();
    final int limite = anomes(filtro.posicao());
    for (final Entrega e : entregas) {
      for (final Mes m : e.meses) {
        for (final Consumo c : m.consumos) {
          if (c.uoCodigo == null && c.uo == null) {
            continue;
          }
          final String chave = c.uoCodigo + "|" + c.uo;
          nomes.putIfAbsent(chave, new Object[] {c.uoCodigo, c.uo});
          final double[] v = soma.computeIfAbsent(chave, k -> new double[3]);
          if (filtro.total() || m.anomes <= limite) {
            v[0] += c.estimado;
            v[1] += c.planejado;
          }
          if (m.anomes <= limite) {
            v[2] += c.realizado;
          }
        }
      }
    }
    return soma.entrySet().stream()
      .sorted(Comparator.comparing((Map.Entry<String, double[]> e) -> e.getValue()[0]).reversed())
      .map(e -> mapa("codigo", nomes.get(e.getKey())[0], "nome", nomes.get(e.getKey())[1],
        "estimado", valor(e.getValue()[0]), "reprogramado", valor(e.getValue()[1]), "realizado", valor(e.getValue()[2])))
      .collect(Collectors.toList());
  }

  private static List<String> avisos(final Map<String, Object> fisico, final Map<String, Object> financeiro) {
    final List<String> avisos = new ArrayList<>();
    final double fEst = ((BigDecimal) fisico.get("estimado")).doubleValue();
    final double fRep = ((BigDecimal) fisico.get("reprogramado")).doubleValue();
    final double fReal = ((BigDecimal) fisico.get("realizado")).doubleValue();
    final double cRep = ((BigDecimal) financeiro.get("reprogramado")).doubleValue();
    final double cReal = ((BigDecimal) financeiro.get("realizado")).doubleValue();
    if (fRep > 0 && fReal >= fRep) {
      avisos.add("Escopo concluído");
    }
    if (fEst > 0 && fRep > fEst) {
      avisos.add("Escopo físico reprogramado excedente ao planejado");
    }
    if (cRep > 0 && cReal > cRep * 1.25) {
      avisos.add("Realização de custo excedida acima de 25%");
    }
    return avisos;
  }

  // ===================================================================================== modelos

  static final class Carteira {
    final List<Projeto> projetos;
    private final Map<Long, List<Entrega>> entregasPorProjeto = new LinkedHashMap<>();
    private final Map<Long, List<Marco>> marcosPorProjeto = new LinkedHashMap<>();
    private final Map<Long, Entrega> entregaPorId = new HashMap<>();

    Carteira(final List<Projeto> projetos) {
      this.projetos = projetos;
    }

    void adicionar(final Entrega e) {
      this.entregasPorProjeto.computeIfAbsent(e.projetoId, k -> new ArrayList<>()).add(e);
      this.entregaPorId.put(e.id, e);
    }

    void adicionar(final Marco m) {
      this.marcosPorProjeto.computeIfAbsent(m.projetoId, k -> new ArrayList<>()).add(m);
    }

    List<Entrega> entregas() {
      return this.entregasPorProjeto.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    List<Marco> marcos() {
      return this.marcosPorProjeto.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    List<Entrega> entregasDe(final Long projeto) {
      return this.entregasPorProjeto.getOrDefault(projeto, List.of());
    }

    List<Marco> marcosDe(final Long projeto) {
      return this.marcosPorProjeto.getOrDefault(projeto, List.of());
    }

    Entrega entrega(final Long id) {
      return id == null ? null : this.entregaPorId.get(id);
    }

    String nomeEntrega(final Long id) {
      final Entrega e = this.entrega(id);
      return e == null ? null : e.nome;
    }

    /** Maior termino previsto entre as entregas; sem entrega com termino, a propriedade do projeto. */
    LocalDate dataTermino(final Projeto p) {
      return this.entregasDe(p.id).stream().map(e -> e.termino).filter(Objects::nonNull)
        .max(Comparator.naturalOrder()).orElse(p.dataTerminoPropriedade);
    }
  }

  static final class Projeto {
    Long id;
    String nome;
    Long areaId;
    String area;
    String status;
    LocalDate dataInicio;
    LocalDate dataTerminoPropriedade;
    String instituicao;
    String gerente;
    List<String> objetivos = List.of();
    List<String> estrategias = List.of();
    List<String> metas = List.of();
    List<String> ods = List.of();

    static Projeto de(final Map<String, Object> linha) {
      final Projeto p = new Projeto();
      p.id = numero(linha.get("id")).longValue();
      p.nome = texto(linha.get("nome"));
      p.areaId = linha.get("areaId") == null ? null : numero(linha.get("areaId")).longValue();
      p.area = texto(linha.get("area"));
      for (final Map<String, Object> prop : lista(linha.get("props"))) {
        final String n = texto(prop.get("n"));
        if (n == null) {
          continue;
        }
        final String v = coalesce(texto(prop.get("sel")), texto(prop.get("v")));
        if (n.equals("situação") || n.equals("situacao")) {
          p.status = v;
        } else if (n.startsWith("data de início") || n.startsWith("data de inicio")) {
          p.dataInicio = data(v);
        } else if (n.startsWith("data prevista de término") || n.startsWith("data prevista de termino")) {
          p.dataTerminoPropriedade = data(v);
        } else if (NOMES_INSTITUICAO.contains(n) && p.instituicao == null) {
          p.instituicao = v;
        } else if (n.startsWith("objetivo")) {
          p.objetivos = separar(texto(prop.get("bruto")), ",");
        } else if (n.startsWith("estratégia") || n.startsWith("estrategia")) {
          p.estrategias = separar(v, ";");
        } else if (n.startsWith("meta(s)") || n.startsWith("metas")) {
          p.metas = separar(v, ";");
        } else if (n.startsWith("ods")) {
          p.ods = separar(v, ";");
        }
      }
      final List<Map<String, Object>> partes = lista(linha.get("partes"));
      if (p.instituicao == null) {
        p.instituicao = partes.stream()
          .filter(a -> Boolean.TRUE.equals(a.get("organizacao")) && PAPEL_INSTITUICAO.equals(a.get("papel")))
          .map(a -> texto(a.get("nome"))).findFirst().orElse(null);
      }
      final List<String> gerentes = partes.stream().filter(a -> "gerente".equals(a.get("papel")))
        .map(a -> texto(a.get("nome"))).filter(Objects::nonNull).collect(Collectors.toList());
      p.gerente = gerentes.isEmpty() ? null : String.join(", ", gerentes);
      return p;
    }
  }

  static final class Entrega {
    Long id;
    Long projetoId;
    String nome;
    String tipo;
    String situacao;
    Long localId;
    String local;
    String unidade;
    String gerente;
    LocalDate termino;
    LocalDate encerramento;
    List<Mes> meses = List.of();

    static Entrega de(final Map<String, Object> linha, final List<Map<String, Object>> meses) {
      final Entrega e = new Entrega();
      e.id = numero(linha.get("id")).longValue();
      e.projetoId = numero(linha.get("projetoId")).longValue();
      e.nome = texto(linha.get("nome"));
      e.termino = data(linha.get("termino"));
      e.encerramento = data(linha.get("encerramento"));
      final List<String> gerentes = textos(linha.get("gerentes"));
      e.gerente = gerentes.isEmpty() ? null : String.join(", ", gerentes);
      for (final Map<String, Object> prop : lista(linha.get("props"))) {
        final String n = texto(prop.get("n"));
        if (n == null) {
          continue;
        }
        final String v = coalesce(texto(prop.get("sel")), texto(prop.get("v")));
        if (n.equals("tipo")) {
          e.tipo = v;
        } else if (n.equals("situação") || n.equals("situacao")) {
          e.situacao = v;
        } else if (n.startsWith("local") && prop.get("selId") != null && "CITY".equals(prop.get("selTipo"))) {
          e.localId = numero(prop.get("selId")).longValue();
          e.local = texto(prop.get("sel"));
        } else if (n.startsWith("unidade de medida")) {
          e.unidade = v;
        }
      }
      e.meses = meses.stream().map(Mes::de).sorted(Comparator.comparingInt(m -> m.anomes)).collect(Collectors.toList());
      return e;
    }

    boolean concluida() {
      return this.situacao != null && this.situacao.toLowerCase(Locale.ROOT).startsWith("conclu");
    }

    boolean cancelada() {
      return this.situacao != null && this.situacao.toLowerCase(Locale.ROOT).startsWith("cancel");
    }

    boolean atrasada(final LocalDate pos) {
      return !this.concluida() && !this.cancelada() && this.termino != null && this.termino.isBefore(pos);
    }

    long diasAtraso(final LocalDate pos) {
      return this.termino == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(this.termino, pos));
    }

    /** Data de encerramento do gerenciamento; sem ela, o ultimo dia do ultimo mes com realizado. */
    LocalDate dataConclusao() {
      if (!this.concluida()) {
        return null;
      }
      if (this.encerramento != null) {
        return this.encerramento;
      }
      return this.meses.stream()
        .filter(m -> m.fisicoRealizado > 0 || m.consumos.stream().anyMatch(c -> c.realizado > 0))
        .map(m -> YearMonth.of(m.anomes / 100, m.anomes % 100).atEndOfMonth())
        .max(Comparator.naturalOrder()).orElse(null);
    }

    Map<String, Object> fisico() {
      return mapa(
        "estimado", valor(this.meses.stream().mapToDouble(m -> m.fisicoEstimado).sum()),
        "reprogramado", valor(this.meses.stream().mapToDouble(m -> m.fisicoPlanejado).sum()),
        "realizado", valor(this.meses.stream().mapToDouble(m -> m.fisicoRealizado).sum()),
        "unidade", this.unidade);
    }

    Map<String, Object> financeiro(final PainelFiltro filtro) {
      final int limite = anomes(filtro.posicao());
      double estimado = 0;
      double planejado = 0;
      double realizado = 0;
      for (final Mes m : this.meses) {
        for (final Consumo c : m.consumos) {
          if (filtro.total() || m.anomes <= limite) {
            estimado += c.estimado;
            planejado += c.planejado;
          }
          if (m.anomes <= limite) {
            realizado += c.realizado;
          }
        }
      }
      return mapa("estimado", valor(estimado), "reprogramado", valor(planejado), "realizado", valor(realizado));
    }
  }

  static final class Mes {
    int anomes;
    double fisicoPlanejado;
    double fisicoRealizado;
    double fisicoEstimado;
    List<Consumo> consumos = List.of();

    static Mes de(final Map<String, Object> linha) {
      final Mes m = new Mes();
      m.anomes = numero(linha.get("anomes")).intValue();
      m.fisicoPlanejado = numero(linha.get("fisicoPlanejado")).doubleValue();
      m.fisicoRealizado = numero(linha.get("fisicoRealizado")).doubleValue();
      m.fisicoEstimado = numero(linha.get("fisicoEstimado")).doubleValue();
      m.consumos = lista(linha.get("consumos")).stream().map(Consumo::de).collect(Collectors.toList());
      return m;
    }
  }

  static final class Consumo {
    Object uoCodigo;
    String uo;
    double planejado;
    double realizado;
    double estimado;

    static Consumo de(final Map<String, Object> linha) {
      final Consumo c = new Consumo();
      c.uoCodigo = linha.get("uoCodigo");
      c.uo = texto(linha.get("uo"));
      c.planejado = numero(linha.get("planejado")).doubleValue();
      c.realizado = numero(linha.get("realizado")).doubleValue();
      c.estimado = numero(linha.get("estimado")).doubleValue();
      return c;
    }
  }

  static final class Marco {
    Long id;
    Long projetoId;
    Long entregaId;
    String nome;
    String tipo;
    LocalDate data;
    LocalDate dataLinhaBase;
    boolean concluido;
    boolean cancelado;

    static Marco de(final Map<String, Object> linha) {
      final Marco m = new Marco();
      m.id = numero(linha.get("id")).longValue();
      m.projetoId = numero(linha.get("projetoId")).longValue();
      m.entregaId = linha.get("entregaId") == null ? null : numero(linha.get("entregaId")).longValue();
      m.nome = texto(linha.get("nome"));
      m.tipo = texto(linha.get("tipo"));
      m.data = data(linha.get("data"));
      m.dataLinhaBase = data(linha.get("dataLinhaBase"));
      m.concluido = Boolean.TRUE.equals(linha.get("concluido"));
      m.cancelado = Boolean.TRUE.equals(linha.get("cancelado"));
      return m;
    }

    /** Mesma regra do MilestoneService: concluido ate a data da linha de base, ou atrasado depois da posicao. */
    String situacao(final LocalDate pos) {
      if (this.concluido) {
        final LocalDate referencia = this.dataLinhaBase == null ? this.data : this.dataLinhaBase;
        return this.data == null || referencia == null || !this.data.isAfter(referencia) ? "Concluído" : "Concluído atrasado";
      }
      return this.data == null || !this.data.isBefore(pos) ? "No prazo" : "Atrasado";
    }

    long diasAtraso(final LocalDate pos) {
      return this.data == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(this.data, pos));
    }
  }

  /** Indice de cumprimento: marcos concluidos ate a posicao / marcos com data prevista ate a posicao. */
  static final class Indice {
    long concluidos;
    long vencidos;
    BigDecimal indice;

    static Indice de(final Collection<Marco> marcos, final LocalDate pos) {
      final Indice i = new Indice();
      final List<Marco> vencidos = marcos.stream().filter(m -> m.data != null && !m.data.isAfter(pos)).collect(Collectors.toList());
      i.vencidos = vencidos.size();
      i.concluidos = vencidos.stream().filter(m -> m.concluido).count();
      i.indice = i.vencidos == 0 ? null : percentual(i.concluidos, i.vencidos);
      return i;
    }

    boolean noUniverso() {
      return this.indice != null;
    }

    boolean critico() {
      return this.noUniverso() && this.indice.doubleValue() < 60;
    }

    String faixa() {
      if (!this.noUniverso()) {
        return null;
      }
      final double v = this.indice.doubleValue();
      return v >= 80 ? "NO_PRAZO" : v >= 60 ? "ATENCAO" : "CRITICO";
    }

    String chaveContagem() {
      final String f = this.faixa();
      if (f == null) {
        return "foraDoUniverso";
      }
      return "NO_PRAZO".equals(f) ? "noPrazo" : "ATENCAO".equals(f) ? "atencao" : "critico";
    }

    Map<String, Object> comoMapa() {
      return mapa("marcosConcluidos", this.concluidos, "marcosVencidos", this.vencidos, "indice", this.indice, "faixa", this.faixa());
    }
  }

  // ===================================================================================== utilitarios

  static Map<String, Object> mapa(final Object... chavesValores) {
    final Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < chavesValores.length; i += 2) {
      m.put((String) chavesValores[i], chavesValores[i + 1]);
    }
    return m;
  }

  static BigDecimal valor(final double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal percentual(final double parte, final double total) {
    return total <= 0 ? valor(0) : valor(100.0 * parte / total);
  }

  static int anomes(final LocalDate d) {
    return d.getYear() * 100 + d.getMonthValue();
  }

  private static String normalizar(final String valor, final List<String> oficiais) {
    if (valor == null || valor.trim().isEmpty()) {
      return "Não informado";
    }
    final String v = valor.trim().toLowerCase(Locale.ROOT);
    return oficiais.stream().filter(o -> v.startsWith(o.toLowerCase(Locale.ROOT))).findFirst().orElse(valor.trim());
  }

  private static Map<String, List<Map<String, Object>>> porImportancia() {
    final Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
    m.put("ALTA", new ArrayList<>());
    m.put("MEDIA", new ArrayList<>());
    m.put("BAIXA", new ArrayList<>());
    return m;
  }

  private static String importancia(final Object v) {
    final String s = String.valueOf(v);
    return "HIGH".equals(s) ? "ALTA" : "MEDIUM".equals(s) ? "MEDIA" : "BAIXA";
  }

  private static final Map<String, String> TRADUCOES = Map.ofEntries(
    Map.entry("THREAT", "AMEACA"), Map.entry("OPPORTUNITY", "OPORTUNIDADE"),
    Map.entry("PROBLEM", "PROBLEMA"), Map.entry("BENEFIT", "BENEFICIO"),
    Map.entry("OPEN", "ABERTO"), Map.entry("HAPPENED", "OCORRIDO"), Map.entry("NOT_GONNA_HAPPEN", "NAO_OCORRERA"),
    Map.entry("CLOSED", "FECHADO"),
    Map.entry("MITIGATE", "MITIGAR"), Map.entry("TRANSFER", "TRANSFERIR"), Map.entry("ACCEPT", "ACEITAR"),
    Map.entry("ELIMINATE", "ELIMINAR"), Map.entry("SCALE", "ESCALAR"), Map.entry("EXPLOIT", "EXPLORAR"),
    Map.entry("ENHANCE", "MELHORAR"), Map.entry("SHARE", "COMPARTILHAR"),
    Map.entry("WAITING_TRIGGER", "AGUARDANDO_GATILHO"), Map.entry("WAITING", "AGUARDANDO"), Map.entry("RUNNING", "EM_ANDAMENTO"),
    Map.entry("DONE", "CONCLUIDO"), Map.entry("CANCELLED", "CANCELADO"),
    Map.entry("PRE_OCCURRENCE", "PRE_OCORRENCIA"), Map.entry("POST_OCCURRENCE", "POS_OCORRENCIA"));

  private static String traduzir(final Object v) {
    return v == null ? null : TRADUCOES.getOrDefault(String.valueOf(v), String.valueOf(v));
  }

  static Number numero(final Object v) {
    return v instanceof Number ? (Number) v : 0;
  }

  static String texto(final Object v) {
    if (v == null) {
      return null;
    }
    final String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  private static String coalesce(final String a, final String b) {
    return a != null ? a : b;
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> lista(final Object v) {
    if (!(v instanceof List)) {
      return List.of();
    }
    return ((List<Object>) v).stream().filter(x -> x instanceof Map).map(x -> (Map<String, Object>) x).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private static List<String> textos(final Object v) {
    if (!(v instanceof List)) {
      return List.of();
    }
    return ((List<Object>) v).stream().map(PainelService::texto).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private static List<String> separar(final String v, final String separador) {
    if (v == null) {
      return List.of();
    }
    return Arrays.stream(v.split(separador)).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  private static double soma(final java.util.stream.Stream<Map<String, Object>> periodos, final String campo) {
    return periodos.mapToDouble(g -> numero(g.get(campo)).doubleValue()).sum();
  }

  /** Aceita AAAA-MM-DD (com ou sem hora) e DD/MM/AAAA. */
  static LocalDate data(final Object v) {
    final String s = texto(v);
    if (s == null) {
      return null;
    }
    try {
      if (s.length() >= 10 && s.charAt(4) == '-') {
        return LocalDate.parse(s.substring(0, 10));
      }
      if (s.length() >= 10 && s.charAt(2) == '/') {
        return LocalDate.parse(s.substring(0, 10), DATA_BR);
      }
    } catch (final RuntimeException e) {
      return null;
    }
    return null;
  }

  static Set<String> conjunto(final String csv) {
    if (csv == null || csv.trim().isEmpty()) {
      return Collections.emptySet();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
  }

}
