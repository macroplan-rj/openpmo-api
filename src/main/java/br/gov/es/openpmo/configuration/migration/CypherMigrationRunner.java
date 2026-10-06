package br.gov.es.openpmo.configuration.migration;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aplica, na subida da API, os arquivos classpath:db/migration/*.cypher que ainda nao foram
 * aplicados neste banco. Cada arquivo roda uma unica vez: ao terminar, grava um no
 * (:DataMigration {key}) com o nome do arquivo — o mesmo marcador que as migracoes feitas por
 * script (tools/) usam — e nas subidas seguintes e pulado.
 *
 * Formato do arquivo: um statement por linha terminada em ';' (pode ocupar varias linhas);
 * linhas em branco e comentarios '//' sao ignorados. Os arquivos sao aplicados em ordem de nome,
 * cada um na propria transacao. Uma falha interrompe a subida: e melhor nao subir do que subir
 * com o banco pela metade (SD #10607).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CypherMigrationRunner implements ApplicationRunner {

  static final String LOCATION = "classpath*:db/migration/*.cypher";

  private static final Logger LOGGER = LoggerFactory.getLogger(CypherMigrationRunner.class);

  private static final String IS_APPLIED =
    "MATCH (d:DataMigration {key: $key}) RETURN count(d) > 0 AS applied";

  private static final String MARK_APPLIED =
    "MERGE (d:DataMigration {key: $key}) "
    + "ON CREATE SET d.appliedAt = toString(datetime()), d.appliedBy = 'openpmo-api'";

  private final Driver driver;

  public CypherMigrationRunner(final Driver driver) {
    this.driver = driver;
  }

  @Override
  public void run(final ApplicationArguments args) throws IOException {
    final Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
    Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
    for (final Resource resource : resources) {
      final String key = keyOf(resource);
      final List<String> statements = statements(read(resource));
      this.applyIfPending(key, statements);
    }
  }

  boolean applyIfPending(final String key, final List<String> statements) {
    try (Session session = this.driver.session()) {
      final boolean applied = session.readTransaction(tx ->
        tx.run(IS_APPLIED, Values.parameters("key", key)).single().get("applied").asBoolean()
      );
      if (applied) {
        LOGGER.debug("Migracao {} ja aplicada; pulando.", key);
        return false;
      }
      session.writeTransaction(tx -> {
        for (final String statement : statements) {
          tx.run(statement);
        }
        tx.run(MARK_APPLIED, Values.parameters("key", key));
        return null;
      });
      LOGGER.info("Migracao {} aplicada ({} statements).", key, statements.size());
      return true;
    } catch (final RuntimeException e) {
      throw new IllegalStateException("Falha ao aplicar a migracao " + key, e);
    }
  }

  static String keyOf(final Resource resource) {
    final String name = Objects.requireNonNull(resource.getFilename(), "migracao sem nome");
    return name.endsWith(".cypher") ? name.substring(0, name.length() - ".cypher".length()) : name;
  }

  static List<String> statements(final String content) {
    final List<String> statements = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    for (final String rawLine : content.split("\\r?\\n")) {
      final String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("//")) {
        continue;
      }
      if (current.length() > 0) {
        current.append('\n');
      }
      current.append(line);
      if (line.endsWith(";")) {
        current.setLength(current.length() - 1);
        statements.add(current.toString().trim());
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      statements.add(current.toString().trim());
    }
    return statements;
  }

  private static String read(final Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
