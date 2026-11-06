package br.gov.es.openpmo.repository.painel;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Consultas do Painel Estrategico (SD #10795-#10798). Cada consulta busca os fatos da carteira inteira de uma vez
 * (lista de projetos), no padrao de {@code DashboardRepositoryCustomImpl}: Cypher em resources/cyphers/painel.
 */
@Repository
public class PainelRepository {

  private final Driver driver;

  private final Map<String, String> consultas = new ConcurrentHashMap<>();

  public PainelRepository(final Driver driver) {
    this.driver = driver;
  }

  public List<Map<String, Object>> projetos() {
    return this.executar("projetos", Map.of());
  }

  public List<Map<String, Object>> entregas(final Collection<Long> projetos) {
    return this.executar("entregas", Map.of("projetos", List.copyOf(projetos)));
  }

  public List<Map<String, Object>> custos(final Collection<Long> projetos) {
    return this.executar("custos", Map.of("projetos", List.copyOf(projetos)));
  }

  public List<Map<String, Object>> marcos(final Collection<Long> projetos) {
    return this.executar("marcos", Map.of("projetos", List.copyOf(projetos)));
  }

  public List<Map<String, Object>> indicadores(final Long projeto) {
    return this.executar("indicadores", Map.of("projeto", projeto));
  }

  public List<Map<String, Object>> riscos(final Long projeto) {
    return this.executar("riscos", Map.of("projeto", projeto));
  }

  public List<Map<String, Object>> ocorrencias(final Long projeto) {
    return this.executar("ocorrencias", Map.of("projeto", projeto));
  }

  private List<Map<String, Object>> executar(final String nome, final Map<String, Object> parametros) {
    final String consulta = this.consultas.computeIfAbsent(nome, PainelRepository::ler);
    try (Session session = this.driver.session()) {
      return session.readTransaction(tx -> tx.run(consulta, parametros).list(Record::asMap));
    }
  }

  private static String ler(final String nome) {
    try (InputStream is = new ClassPathResource("cyphers/painel/" + nome + ".cypher").getInputStream()) {
      return new String(is.readAllBytes(), StandardCharsets.UTF_8).lines()
        .filter(linha -> !linha.trim().startsWith("//"))
        .collect(Collectors.joining("\n"));
    } catch (final IOException e) {
      throw new IllegalStateException("Consulta do painel nao encontrada: " + nome, e);
    }
  }

}
