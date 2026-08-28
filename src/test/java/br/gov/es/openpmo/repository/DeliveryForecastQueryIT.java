package br.gov.es.openpmo.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import br.gov.es.openpmo.dto.workpack.DeliveryForecastDto;
import br.gov.es.openpmo.repository.custom.CustomRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.springframework.data.neo4j.repository.support.Neo4jRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;

/**
 * US-003 — as consultas de previsao de entrega contra o Neo4j de verdade.
 *
 * Os unitarios cobrem o roteamento por tipo e a serializacao; este cobre a peca do meio:
 * o Cypher rodando no banco e o Spring Data materializando {@link DeliveryForecastDto}.
 * Se isso falhar, deliveryForecastDate volta sempre nulo e a feature vira no-op silencioso.
 *
 * Exercita o {@link WorkpackRepository} de verdade, montado pelo
 * {@link Neo4jRepositoryFactory} — o mesmo caminho da aplicacao. Nao usar
 * {@code session.query(DeliveryForecastDto.class, ...)} aqui: o OGM so reconhece a sua
 * propria @QueryResult ({@code org.neo4j.ogm.annotation}), enquanto o projeto usa a do
 * Spring Data ({@code org.springframework.data.neo4j.annotation}), e o resultado sai vazio
 * sem erro nenhum.
 *
 * Precisa do Neo4j local (bolt://localhost:7688). Sem ele o teste e ignorado via Assume,
 * para nao quebrar o CI.
 */
public class DeliveryForecastQueryIT {

  private static final String URI = System.getProperty("neo4j.test.uri", "bolt://localhost:7688");
  private static final String USER = System.getProperty("neo4j.test.user", "neo4j");
  private static final String PASSWORD = System.getProperty("neo4j.test.password", "openpmo2024");

  private static SessionFactory sessionFactory;
  private static WorkpackRepository repository;

  @BeforeClass
  public static void connect() {
    try {
      final Configuration configuration = new Configuration.Builder()
        .uri(URI)
        .credentials(USER, PASSWORD)
        .verifyConnection(true)
        .build();
      sessionFactory = new SessionFactory(
        configuration,
        "br.gov.es.openpmo.model",
        "br.gov.es.openpmo.dto"
      );
      final Session session = sessionFactory.openSession();
      // WorkpackRepository herda o fragmento CustomRepository (getSession()). Sem passar
      // a implementacao, o Spring Data tenta derivar uma query do nome do metodo e
      // estoura com "No property getSession found for type Workpack".
      final CustomRepository customFragment = () -> session;
      repository = new Neo4jRepositoryFactory(session, null)
        .getRepository(WorkpackRepository.class, RepositoryFragments.just(customFragment));
    } catch (final Exception e) {
      // Sem isto a causa fica invisivel: o Assume marca a classe inteira como ignorada e
      // o Gradle reporta "0 testes", o que se confunde com sucesso.
      System.out.println("### DeliveryForecastQueryIT ignorado. Causa:");
      e.printStackTrace(System.out);
      assumeNoException("Neo4j local indisponivel em " + URI + " — teste ignorado", e);
    }
  }

  @AfterClass
  public static void disconnect() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  private static Map<Long, String> asMap(final List<DeliveryForecastDto> results) {
    final Map<Long, String> map = new HashMap<>();
    for (final DeliveryForecastDto dto : results) {
      map.put(dto.getIdWorkpack(), dto.getForecastDate());
    }
    return map;
  }

  @Test
  public void deveMaterializarODtoParaDeliverables() {
    final List<DeliveryForecastDto> results =
      repository.findDeliverableDeliveryForecast(Arrays.asList(1461L, 1467L, 1468L));

    assertTrue(
      "nenhuma linha materializada — deliveryForecastDate viraria sempre nulo",
      !results.isEmpty()
    );

    for (final DeliveryForecastDto dto : results) {
      assertNotNull("idWorkpack nao foi mapeado", dto.getIdWorkpack());
      assertNotNull("forecastDate nao foi mapeado", dto.getForecastDate());
      assertTrue(
        "data fora do padrao yyyy-MM-dd: " + dto.getForecastDate(),
        dto.getForecastDate().matches("\\d{4}-\\d{2}-\\d{2}")
      );
    }
  }

  @Test
  public void deliverablesTrazemAsDatasDoCronograma() {
    final Map<Long, String> forecasts =
      asMap(repository.findDeliverableDeliveryForecast(Arrays.asList(1461L, 1467L, 1468L)));

    assertEquals("2027-06-30", forecasts.get(1461L));
    assertEquals("2026-08-14", forecasts.get(1467L));
    assertEquals("2026-08-18", forecasts.get(1468L));
  }

  @Test
  public void idInexistenteNaoDevolveLinha() {
    final Map<Long, String> forecasts =
      asMap(repository.findDeliverableDeliveryForecast(Collections.singletonList(999999L)));

    assertTrue("id inexistente nao pode gerar linha: " + forecasts, forecasts.isEmpty());
  }

  @Test
  public void projectAgregaPelaDataMaisAvancada() {
    final Map<Long, String> forecasts =
      asMap(repository.findProjectDeliveryForecast(Arrays.asList(94L, 95L)));

    // DIGITALIZA PB tem 15 componentes entre entregas e marcos; 2027-06-30 e o maior,
    // do marco "Plataforma web entregue".
    assertEquals("2027-06-30", forecasts.get(95L));
    assertEquals("2027-08-30", forecasts.get(94L));
  }

  @Test
  public void agregacaoDoProjectNaoEMenorQueAMaiorEntrega() {
    final Map<Long, String> deliverables =
      asMap(repository.findDeliverableDeliveryForecast(Arrays.asList(1461L, 1467L, 1468L)));
    final Map<Long, String> projects =
      asMap(repository.findProjectDeliveryForecast(Collections.singletonList(95L)));

    String maiorEntrega = "";
    for (final String data : deliverables.values()) {
      if (data.compareTo(maiorEntrega) > 0) {
        maiorEntrega = data;
      }
    }

    assertTrue(
      "projeto (" + projects.get(95L) + ") ficou antes da maior entrega (" + maiorEntrega + ")",
      projects.get(95L).compareTo(maiorEntrega) >= 0
    );
  }
}
