package br.gov.es.openpmo.service.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.utils.ApplicationMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Regressao SA-528.
 *
 * O relatorio devolvia HTTP 200 com um PDF de uma pagina em branco quando o
 * escopo nao resolvia para nenhum workpack vivo — id inexistente, id de projeto
 * deletado, ou escopo vazio. Arquivo aparentemente valido e sem nenhum aviso.
 */
public class ReportScopeValidatorTest {

  private GetReportScope getReportScope;
  private ReportScopeValidator validator;

  @Before
  public void setUp() {
    this.getReportScope = mock(GetReportScope.class);
    this.validator = new ReportScopeValidator(this.getReportScope);
  }

  @Test
  public void deveRecusarEscopoQueNaoResolveParaNenhumWorkpack() {
    when(this.getReportScope.execute(any(), anyLong(), anyString()))
      .thenReturn(Collections.emptyList());

    try {
      this.validator.execute(Arrays.asList(999999L), 701L, "Bearer x");
      fail("esperava NegocioException para escopo sem resultado");
    } catch (final NegocioException e) {
      assertEquals(ApplicationMessage.REPORT_GENERATE_SCOPE_PARAMETER_INVALID, e.getMessage());
    }
  }

  @Test
  public void deveRecusarEscopoNulo() {
    when(this.getReportScope.execute(any(), anyLong(), anyString())).thenReturn(null);

    try {
      this.validator.execute(Collections.emptyList(), 701L, "Bearer x");
      fail("esperava NegocioException para escopo nulo");
    } catch (final NegocioException e) {
      assertEquals(ApplicationMessage.REPORT_GENERATE_SCOPE_PARAMETER_INVALID, e.getMessage());
    }
  }

  @Test
  public void deveDevolverOEscopoQuandoResolve() {
    final List<Long> vivos = Arrays.asList(100L, 101L);
    when(this.getReportScope.execute(any(), anyLong(), anyString())).thenReturn(vivos);

    assertEquals(vivos, this.validator.execute(Arrays.asList(701L), 701L, "Bearer x"));
  }
}
