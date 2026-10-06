package br.gov.es.openpmo.configuration.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.core.io.ClassPathResource;

/**
 * SD-10607: migracoes Cypher aplicadas uma vez na subida da API.
 */
public class CypherMigrationRunnerTest {

  private Transaction tx;
  private CypherMigrationRunner runner;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    final Driver driver = mock(Driver.class);
    final Session session = mock(Session.class);
    this.tx = mock(Transaction.class);
    when(driver.session()).thenReturn(session);
    when(session.readTransaction(any(TransactionWork.class)))
      .thenAnswer(inv -> ((TransactionWork<Object>) inv.getArgument(0)).execute(this.tx));
    when(session.writeTransaction(any(TransactionWork.class)))
      .thenAnswer(inv -> ((TransactionWork<Object>) inv.getArgument(0)).execute(this.tx));
    this.runner = new CypherMigrationRunner(driver);
  }

  private void marcadorExiste(final boolean applied) {
    final Result result = mock(Result.class);
    final Record record = mock(Record.class);
    when(this.tx.run(anyString(), any(Value.class))).thenReturn(result);
    when(result.single()).thenReturn(record);
    when(record.get("applied")).thenReturn(Values.value(applied));
  }

  @Test
  public void separaStatementsPorPontoEVirgulaIgnorandoComentarios() {
    final String content = "// cabecalho\n\nMERGE (a:A {k: 'x; y'}) SET a.v = 1;\n"
      + "UNWIND [1, 2] AS n\n  MERGE (b:B {n: n});\n// fim\n";
    final List<String> statements = CypherMigrationRunner.statements(content);
    assertEquals(Arrays.asList(
      "MERGE (a:A {k: 'x; y'}) SET a.v = 1",
      "UNWIND [1, 2] AS n\nMERGE (b:B {n: n})"
    ), statements);
  }

  @Test
  public void ultimoStatementSemPontoEVirgulaTambemEntra() {
    assertEquals(Arrays.asList("RETURN 1"), CypherMigrationRunner.statements("RETURN 1"));
  }

  @Test
  public void chaveEONomeDoArquivoSemExtensao() {
    assertEquals("V001__pelp_objetivo",
      CypherMigrationRunner.keyOf(new ClassPathResource("db/migration/V001__pelp_objetivo.cypher")));
  }

  @Test
  public void aplicaERegistraMarcadorQuandoPendente() {
    this.marcadorExiste(false);
    final boolean applied = this.runner.applyIfPending("V001__x", Arrays.asList("MERGE (a:A)", "MERGE (b:B)"));
    assertTrue(applied);
    verify(this.tx).run("MERGE (a:A)");
    verify(this.tx).run("MERGE (b:B)");
    verify(this.tx, times(2)).run(anyString(), any(Value.class)); // consulta do marcador + gravacao
  }

  @Test
  public void pulaQuandoMarcadorJaExiste() {
    this.marcadorExiste(true);
    final boolean applied = this.runner.applyIfPending("V001__x", Arrays.asList("MERGE (a:A)"));
    assertFalse(applied);
    verify(this.tx, never()).run(eq("MERGE (a:A)"));
    verify(this.tx, times(1)).run(anyString(), any(Value.class)); // so a consulta
  }

  @Test
  public void seedDoPelpEstaNoClasspathEParseia() throws Exception {
    final ClassPathResource seed = new ClassPathResource("db/migration/V001__pelp_objetivo.cypher");
    assertTrue(seed.exists());
    final String content = new String(seed.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    final List<String> statements = CypherMigrationRunner.statements(content);
    assertEquals(2, statements.size());
    assertTrue(statements.get(0).startsWith("MERGE (l:DynamicSelectionList {key: 'pelp.objetivo'})"));
    assertTrue(statements.get(1).contains("{code: 'OBJ19'"));
    assertTrue(statements.get(1).contains("{code: 'NA'"));
  }
}
